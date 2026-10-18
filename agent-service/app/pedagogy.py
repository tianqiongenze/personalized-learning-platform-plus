"""OpenMAIC 借鉴 3: 教育层级画像(Pedagogy Profile)动态切换

在 职教(高职) / 研究型本科 / 研究生(博士) 三档之间动态切换,
每档改变: Bloom 天花板 / 脚手架策略 / 交互深度上限 / 生成提示词修饰 / 槽位与工作流联动。

覆盖链(与 slots 完全同构, 可组合):
  profiles/global.yml (默认档) → profiles/{profileId}.yml (档定义)
  → courses/{courseId}.yml 的 pedagogy: 段 (课程绑定/微调) → API 请求级参数

fail-loud: 未知的 profileId / 层级字段直接报错并指明字段, 不静默降级。
"""
from __future__ import annotations

import os
import re
from typing import Any

import yaml

CONFIG_DIR = os.getenv("PEDAGOGY_CONFIG_DIR",
                       os.path.join(os.path.dirname(os.path.abspath(__file__)), "configs"))

# 档位注册表(OpenMAIC "职教/项目/任务动态选择" 语义)
PROFILE_IDS = ("vocational", "research-undergrad", "graduate")
BLOOM_LADDER = ["remember", "understand", "apply", "analyze", "evaluate", "create"]

DEFAULT_PROFILE = {
    "id": "vocational",
    "display": "高职(职业本科)",
    "bloomCeiling": "apply",            # 本档 Bloom 天花板
    "scaffolding": "high",              # high|medium|low 脚手架密度
    "interaction": {
        "maxTurns": 6,                  # 深度交互最大轮次
        "socraticLevel": "guided",      # guided|open|socratic
        "depthTarget": "apply",         # 交互深度目标(Bloom)
        "breadthBranches": 2,           # 广度扩展分支数(知识图谱邻接)
        "giveAnswerOnMiss": True,       # 职教: 连续答错直接给答案+补救
    },
    "promptModifiers": {
        "audience": "高职/职业本科学生, 重视动手与产业情境",
        "style": "任务驱动, 案例先行, 理论够用为度, 深度嵌入 33618 产业场景",
    },
    "workflow": "standard",             # 与 slots.workflow 联动(可被课程覆盖)
    "slotOverrides": {},                # 本档生效的槽位覆盖(如强模型档)
    "reviewWeights": {"accuracy": 0.3, "fit": 0.3, "industry": 0.25, "safety": 0.15},
}

_PROFILE_YAML_CACHE: dict[str, dict] = {}


class PedagogyError(Exception):
    """fail-loud: 层级画像解析失败(OpenMAIC 语义: names every mistake by its field)"""


def _load_yaml(path: str) -> dict:
    if not os.path.isfile(path):
        return {}
    with open(path, encoding="utf-8") as f:
        return yaml.safe_load(f) or {}


def _deep_merge(base: dict, override: dict) -> dict:
    out = dict(base)
    for k, v in (override or {}).items():
        if isinstance(v, dict) and isinstance(out.get(k), dict):
            out[k] = _deep_merge(out[k], v)
        else:
            out[k] = v
    return out


def _bloom_index(level: str) -> int:
    try:
        return BLOOM_LADDER.index(level)
    except ValueError:
        raise PedagogyError(f"bloom level '{level}' 不在阶梯 {BLOOM_LADDER} 中")


def list_profiles(config_dir: str | None = None) -> list[dict]:
    """全部可用档位(全局注册表 + profiles/*.yml 扩展档)"""
    cfg_dir = config_dir or CONFIG_DIR
    found = []
    # 内置三档(允许被 yml 覆盖增强)
    for pid in PROFILE_IDS:
        yml = _load_yaml(os.path.join(cfg_dir, "profiles", f"{pid}.yml"))
        merged = _deep_merge({**DEFAULT_PROFILE, "id": pid}, yml)
        if pid == "vocational":
            merged.setdefault("display", DEFAULT_PROFILE["display"])
        found.append(merged)
    # 扩展档(目录里多出的 yml, 如 enterprise-trainer)
    pdir = os.path.join(cfg_dir, "profiles")
    if os.path.isdir(pdir):
        for fn in sorted(os.listdir(pdir)):
            if fn.endswith(".yml") and fn[:-4] not in PROFILE_IDS:
                yml = _load_yaml(os.path.join(pdir, fn))
                if yml.get("id"):
                    found.append(_deep_merge(DEFAULT_PROFILE, yml))
    return found


def resolve(profile_id: str | None = None,
            course_id: str = "",
            request_override: dict | None = None,
            config_dir: str | None = None) -> dict:
    """解析最终生效的教育层级画像。

    优先级: 请求参数 > 课程 pedagogy 绑定 > profiles/{id}.yml > 全局默认。
    返回带 _resolvedChain 的 merged dict; 任何未知档位/层级 fail-loud。
    """
    cfg_dir = config_dir or CONFIG_DIR
    chain = []
    # 1) 课程绑定档位
    course = _load_yaml(os.path.join(cfg_dir, f"course.{course_id}.yml")) if course_id else {}
    bound = (course.get("pedagogy") or {}).get("profile")
    pid = profile_id or bound or "vocational"
    if pid not in PROFILE_IDS and not os.path.isfile(os.path.join(cfg_dir, "profiles", f"{pid}.yml")):
        raise PedagogyError(f"profile '{pid}' 未注册(可用: {list(PROFILE_IDS)} 或 profiles/*.yml)")
    # 2) 档位定义
    yml = _load_yaml(os.path.join(cfg_dir, "profiles", f"{pid}.yml"))
    merged = _deep_merge({**DEFAULT_PROFILE, "id": pid}, yml)
    chain.append(f"default:{pid}")
    # 3) 课程级 pedagogy 微调(不换档, 只覆字段)
    tune = (course.get("pedagogy") or {}).get("override")
    if tune:
        merged = _deep_merge(merged, tune)
        chain.append(f"course:{course_id}")
    # 4) 请求级覆盖(仅允许白名单字段, 防越权改天花板)
    for key, val in (request_override or {}).items():
        if key in ("bloomCeiling", "interaction", "scaffolding", "workflow"):
            if key == "bloomCeiling":
                _bloom_index(val)  # 校验合法层级
            merged = _deep_merge(merged, {key: val})
            chain.append("request")
        else:
            raise PedagogyError(f"请求级不允许覆盖字段 '{key}'(白名单: bloomCeiling/interaction/scaffolding/workflow)")
    # 一致性校验: 交互深度目标不得超过(覆盖后)的 Bloom 天花板
    if _bloom_index(merged["interaction"]["depthTarget"]) > _bloom_index(merged["bloomCeiling"]):
        raise PedagogyError(
            f"interaction.depthTarget '{merged['interaction']['depthTarget']}' 超过档位 '{pid}' "
            f"bloomCeiling '{merged['bloomCeiling']}'")
    merged["_resolvedChain"] = chain
    return merged


def generation_directives(profile: dict) -> str:
    """将画像编译为生成工作流的提示词指令段(注入 generation_graph)"""
    pm = profile.get("promptModifiers", {})
    it = profile.get("interaction", {})
    return (f"- 教育层级: {profile.get('display', profile['id'])}"
            f" | Bloom 天花板: {profile['bloomCeiling']}"
            f" | 受众: {pm.get('audience', '')}"
            f" | 风格: {pm.get('style', '')}"
            f" | 脚手架: {profile.get('scaffolding')}"
            f" | 交互设计: 最多{it.get('maxTurns')}轮{socratic_label(it.get('socraticLevel'))}"
            f", 深度目标 {it.get('depthTarget')}, 广度分支 {it.get('breadthBranches')}")


def socratic_label(level: str) -> str:
    return {"guided": "引导式追问", "open": "开放式提问", "socratic": "苏格拉底式追问"}.get(level, level or "")


def bloom_within(level: str, ceiling: str) -> bool:
    return _bloom_index(level) <= _bloom_index(ceiling)
