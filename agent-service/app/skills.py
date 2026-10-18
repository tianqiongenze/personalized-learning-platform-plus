"""OpenMAIC 借鉴 1: Skills 动态加载机制(教学技能包)

Skill = SKILL.md(frontmatter 元数据+正文 SOP) + references/ 懒加载参考资料。
- 技能以目录形式存放于 SKILLS_DIR(挂 ConfigMap/卷, 可热添加)
- 按 frontmatter.description 做关键词匹配 + 可选语义检索动态注入 LangGraph 提示词
- frontmatter.name 唯一; capabilities 声明所需工具(白名单)
- reload() 支持运行时重扫目录(CRUD via API → ConfigMap 更新 → reload)
"""
from __future__ import annotations

import os
import re
import threading
import time
from typing import Any

FRONTMATTER_RE = re.compile(r"^---\s*\n(.*?)\n---\s*\n", re.DOTALL)
FIELD_RE = re.compile(r"^([\w-]+):\s*(.+)$", re.M)


def parse_skill_md(path: str) -> dict | None:
    """解析 SKILL.md: frontmatter(yaml-lite) + body"""
    try:
        raw = open(path, encoding="utf-8").read()
    except OSError:
        return None
    m = FRONTMATTER_RE.match(raw)
    meta: dict[str, Any] = {}
    body = raw
    if m:
        body = raw[m.end():]
        for line in m.group(1).splitlines():
            fm = FIELD_RE.match(line.strip())
            if fm:
                k, v = fm.group(1), fm.group(2).strip().strip('"')
                if v.startswith("[") and v.endswith("]"):
                    meta[k] = [x.strip() for x in v[1:-1].split(",") if x.strip()]
                else:
                    meta[k] = v
    if "name" not in meta:
        return None
    return {
        "name": meta["name"],
        "description": meta.get("description", ""),
        "capabilities": meta.get("capabilities", []) if isinstance(meta.get("capabilities"), list) else [],
        "user_invocable": str(meta.get("user-invocable", "true")).lower() != "false",
        "bloomLevels": meta.get("bloom-levels", "understand,apply"),
        "body": body.strip(),
        "path": path,
        "mtime": os.path.getmtime(path),
    }


class SkillRegistry:
    """技能注册表: 目录扫描 + 关键词/语义匹配 + 热重载(线程安全)"""

    def __init__(self, skills_dir: str | None = None) -> None:
        self.skills_dir = skills_dir or os.getenv(
            "SKILLS_DIR", os.path.join(os.path.dirname(os.path.abspath(__file__)), "skills"))
        self._skills: dict[str, dict] = {}
        self._lock = threading.Lock()
        self._last_scan = 0.0
        self.reload()

    def reload(self) -> int:
        """重扫 SKILLS_DIR 下所有 */SKILL.md, 返回技能数"""
        found = {}
        if os.path.isdir(self.skills_dir):
            for entry in sorted(os.listdir(self.skills_dir)):
                skill_md = os.path.join(self.skills_dir, entry, "SKILL.md")
                if os.path.isfile(skill_md):
                    s = parse_skill_md(skill_md)
                    if s:
                        # references/ 懒加载清单(不读内容, 仅登记)
                        refs_dir = os.path.join(self.skills_dir, entry, "references")
                        s["references"] = (sorted(os.listdir(refs_dir))
                                           if os.path.isdir(refs_dir) else [])
                        found[s["name"]] = s
        with self._lock:
            self._skills = found
            self._last_scan = time.time()
        return len(found)

    def ensure_fresh(self, max_age: float = 30.0) -> None:
        """超过 max_age 秒未扫描则自动重扫(ConfigMap 更新后自动生效)"""
        if time.time() - self._last_scan > max_age:
            self.reload()

    def list_skills(self) -> list[dict]:
        self.ensure_fresh()
        with self._lock:
            return [{k: v for k, v in s.items() if k != "body"} for s in self._skills.values()]

    def match(self, query: str, bloom_level: str | None = None, top: int = 3) -> list[dict]:
        """按查询文本+bloom 级别匹配最相关技能(字符 bigram 打分; 生产可换向量检索)"""
        self.ensure_fresh()
        q = query.lower()
        q_bigrams = {q[i:i+2] for i in range(len(q)-1)} | {q}
        scored = []
        for s in self._skills.values():
            if bloom_level and bloom_level not in str(s.get("bloomLevels", "")):
                continue
            d = s["description"].lower()
            d_bigrams = {d[i:i+2] for i in range(len(d)-1)}
            score = len(q_bigrams & d_bigrams)
            if score:
                scored.append((score, s))
        scored.sort(key=lambda x: -x[0])
        return [s for _, s in scored[:top]]

    def get_prompt(self, skill_name: str, references: list[str] | None = None) -> str | None:
        """渲染技能注入提示词(正文 + 指定 references 内容)"""
        s = self._skills.get(skill_name)
        if not s:
            return None
        parts = [s["body"]]
        for ref in (references or []):
            ref_path = os.path.join(os.path.dirname(s["path"]), "references", ref)
            try:
                parts.append(f"\\n## Reference: {ref}\\n" + open(ref_path, encoding="utf-8").read())
            except OSError:
                pass
        return "\\n\\n".join(parts)
