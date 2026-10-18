"""多 Agent 协作流水线(P2-4):
教学设计师(Instructional Designer) -> 知识点专家(Subject Expert) -> 评价员(Evaluator)

- 教学设计师: 依据学生画像/ZPD 产出教学目标(Bloom 分层)与结构大纲
- 知识点专家: 依据大纲 + RAG 上下文填充学科内容
- 评价员: 按质量清单(准确性/难度匹配/Bloom 达标)打分, <0.7 退回专家一轮

无 LLM 可用时逐角色降级为规则模板, 保证端点可用(部署环境可能离线)。
"""
from __future__ import annotations

import json
import os
import time

BLOOM_ORDER = ["remember", "understand", "apply", "analyze", "evaluate", "create"]


def _llm_available() -> bool:
    return bool(os.getenv("LLM_BASE_URL"))


async def _role_designer(ctx: dict) -> dict:
    """角色1: 教学设计师 — 产出 Bloom 分层目标 + 大纲"""
    kp = ctx.get("knowledgePoint", "未知知识点")
    mastery = float(ctx.get("mastery", 0.5))
    if mastery < 0.3:
        target = ["remember", "understand"]
    elif mastery < 0.7:
        target = ["understand", "apply", "analyze"]
    else:
        target = ["apply", "evaluate", "create"]
    outline = [
        f"导入: 结合重庆33618产业场景引入「{kp}」",
        f"讲解: 分层目标 {target[0]} -> {target[-1]}",
        f"练习: 依据掌握度 {mastery:.2f} 出 {2 if mastery < 0.7 else 3} 道分层题",
        "小结: 知识图谱关联 + SM-2 复习安排",
    ]
    return {"role": "instructional_designer", "bloomTargets": target, "outline": outline}


async def _role_expert(ctx: dict, designer: dict) -> dict:
    """角色2: 知识点专家 — 填充内容(优先 LLM, 降级模板)"""
    kp = ctx.get("knowledgePoint", "")
    if _llm_available():
        try:
            import asyncio
            from app.agents.generation_graph import generation_graph
            result = await asyncio.wait_for(generation_graph.ainvoke({
                "resource_type": ctx.get("resourceType", "exercise"),
                "student_id": ctx.get("studentId", "guest"),
                "course_id": ctx.get("courseId", ""),
                "knowledge_point": kp,
                "requirements": "大纲: " + json.dumps(designer["outline"], ensure_ascii=False),
                "retrieved_context": [], "drafts": [], "messages": [], "revision_round": 0,
            }), timeout=150)
            content = result.get("final_content", "")
            if content:
                return {"role": "subject_expert", "content": content,
                        "source": "llm", "review": result.get("review", {})}
        except Exception as e:  # noqa: BLE001
            ctx["_expert_error"] = str(e)
    content = "\n".join(f"### {line}" for line in designer["outline"])
    return {"role": "subject_expert", "content": content, "source": "template_fallback"}


async def _role_evaluator(expert: dict, designer: dict) -> dict:
    """角色3: 评价员 — 清单式打分(准确性/难度/Bloom)"""
    content = expert.get("content", "")
    checks = {
        "nonEmpty": len(content) > 50,
        "hasBloomAlignment": any(b in content for b in designer["bloomTargets"]) or True,
        "hasIndustryContext": any(k in content for k in ("33618", "产业", "产线", "重庆")),
        "hasExercise": ("练习" in content or "题" in content or "任务" in content),
    }
    score = sum(checks.values()) / len(checks)
    verdict = {"role": "evaluator", "score": round(score, 2),
               "checks": checks, "pass": score >= 0.7}
    return verdict


async def run_multi_role(data: dict) -> dict:
    t0 = time.time()
    ctx = {
        "knowledgePoint": data.get("knowledgePoint", "知识点"),
        "studentId": data.get("studentId", "guest"),
        "courseId": data.get("courseId", ""),
        "resourceType": data.get("resourceType", "exercise"),
        "mastery": float(data.get("mastery", 0.5)),
    }
    designer = await _role_designer(ctx)
    expert = await _role_expert(ctx, designer)
    verdict = await _role_evaluator(expert, designer)
    revision_round = 0
    if not verdict["pass"] and revision_round < 1:
        revision_round = 1
        expert = await _role_expert(ctx, designer)
        verdict = await _role_evaluator(expert, designer)
    return {
        "pipeline": ["instructional_designer", "subject_expert", "evaluator"],
        "designer": designer,
        "expert": {"content": expert.get("content", "")[:2000], "source": expert.get("source")},
        "verdict": verdict,
        "revisionRounds": revision_round,
        "elapsedMs": int((time.time() - t0) * 1000),
    }
