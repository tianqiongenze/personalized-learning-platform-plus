"""OpenMAIC 借鉴 2: 课程级模型槽位(Slots)与工作流配置

三层覆盖链(与 OpenMAIC openmaic.yml 一致):
  全局默认(global.yml) → 课程覆盖(courses/{courseId}.yml) → 任务级参数(API 入参)

槽位语义:
  slots.llm             默认对话模型
  slots.course.outline  大纲生成(强模型)
  slots.exercise        出题
  slots.grading         批改
  slots.rag.rewrite     RAG 改写
  slots.tts             语音(置 null 关闭)
  slots.workflow        生成工作流(standard|two_phase|fast)

fail-loud: 未解析的槽位路由直接报错并指明字段, 不静默降级。
"""
from __future__ import annotations

import os
from typing import Any

import yaml

CONFIG_DIR = os.getenv("SLOTS_CONFIG_DIR",
                       os.path.join(os.path.dirname(os.path.abspath(__file__)), "configs"))

DEFAULT_GLOBAL = {
    "slots": {
        "llm": "qwen2.5-14b-instruct",
        "course.outline": "qwen2.5-32b-instruct",
        "exercise": "qwen2.5-14b-instruct",
        "grading": "qwen2.5-7b-instruct",
        "rag.rewrite": "qwen2.5-7b-instruct",
        "tts": None,
        "workflow": "standard",
    },
    "providers": {
        "litellm": {"base_url": os.getenv("LLM_BASE_URL", "http://ai-platform:4000/v1"),
                     "api_key_env": "LLM_API_KEY"},
    },
}


class SlotResolutionError(Exception):
    """fail-loud: 槽位无法解析(OpenMAIC 语义: names every mistake by its field)"""


class SlotConfig:
    def __init__(self, config_dir: str | None = None) -> None:
        self.config_dir = config_dir or CONFIG_DIR
        self._global: dict[str, Any] = {}
        self._courses: dict[str, dict[str, Any]] = {}
        self._load()

    def _load(self) -> None:
        os.makedirs(self.config_dir, exist_ok=True)
        gp = os.path.join(self.config_dir, "global.yml")
        if os.path.isfile(gp):
            with open(gp, encoding="utf-8") as f:
                self._global = yaml.safe_load(f) or {}
        else:
            self._global = DEFAULT_GLOBAL
        self._courses = {}
        for fn in sorted(os.listdir(self.config_dir)):
            if fn.startswith("course.") and fn.endswith(".yml"):
                cid = fn[len("course."):-len(".yml")]
                with open(os.path.join(self.config_dir, fn), encoding="utf-8") as f:
                    self._courses[cid] = yaml.safe_load(f) or {}
        # 启动期校验(fail-loud)
        problems = self.validate()
        if problems:
            raise SlotConfigError("; ".join(problems))

    def validate(self) -> list[str]:
        problems = []
        for scope, cfg in [("global", self._global)] + list(self._courses.items()):
            for slot, val in (cfg.get("slots") or {}).items():
                if slot == "workflow":
                    if val not in (None, "standard", "two_phase", "fast"):
                        problems.append(f"{scope}.slots.workflow: 未知工作流 '{val}'")
                elif val is not None and not isinstance(val, str):
                    problems.append(f"{scope}.slots.{slot}: 模型路由必须是 provider:model 或 null")
        return problems

    def reload(self) -> None:
        self._load()

    def resolve(self, slot: str, course_id: str | None = None) -> str | None:
        """解析槽位: 课程覆盖优先, 其次全局; 均 null 则关闭该能力"""
        if course_id and course_id in self._courses:
            v = (self._courses[course_id].get("slots") or {}).get(slot, ...)
            if v is not ... and v is not None:
                return str(v)
            if v is None:
                return None  # 课程显式置 null = 关闭
        v = (self._global.get("slots") or {}).get(slot)
        if v is None:
            return None
        if not isinstance(v, str):
            raise SlotConfigError(f"global.slots.{slot}: 模型路由必须是 'provider:model' 字符串, 实际 {v!r}")
        return v

    def workflow(self, course_id: str | None = None) -> str:
        return (self.resolve("workflow", course_id) or "standard")

    def course_overlay(self, course_id: str) -> dict[str, Any]:
        return self._courses.get(course_id, {})


class SlotConfigError(SlotResolutionError):
    pass
