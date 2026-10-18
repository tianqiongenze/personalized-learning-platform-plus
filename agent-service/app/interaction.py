"""深度交互引擎(Interaction Depth Engine) — OpenMAIC "深度交互" 吸收

增强交互式课程资源的交互深度(Bloom 爬升)与广度(知识图谱分支扩展):
  会话状态机: probe(摸底) → deepen(逐层深化, Bloom 爬升) → branch(广度分支)
             → consolidate(总结+SM-2 落库钩子) | remediate(降层补救)
每轮由 LLM 依据 pedagogy profile 产出下一问(选项/开放题自适应), 全程可流式。
无 LLM 时模板降级, 保证可用性。
"""
from __future__ import annotations

import time
import uuid
from typing import Any

# 会话内存存储(生产经 Pulsar+CRDB 持久化, 见 P3 Durable Session 路线)
_SESSIONS: dict[str, dict] = {}
MAX_SESSIONS = 500

BLOOM_LADDER = ["remember", "understand", "apply", "analyze", "evaluate", "create"]

STAGE_ORDER = ["probe", "deepen", "branch", "consolidate"]


class InteractionError(Exception):
    pass


def _prune() -> None:
    if len(_SESSIONS) >= MAX_SESSIONS:
        oldest = sorted(_SESSIONS.items(), key=lambda kv: kv[1]["updatedAt"])[:100]
        for k, _ in oldest:
            _SESSIONS.pop(k, None)


def start(profile: dict, knowledge_point: str, course_id: str = "",
          student_id: str = "guest", seed_mastery: float = 0.5) -> dict:
    """开启一次深度交互会话(依据教育层级画像初始化深度/广度目标)"""
    it = profile.get("interaction", {})
    sess = {
        "sessionId": uuid.uuid4().hex[:16],
        "profileId": profile["id"],
        "bloomCeiling": profile["bloomCeiling"],
        "depthTarget": it.get("depthTarget", "apply"),
        "socraticLevel": it.get("socraticLevel", "guided"),
        "breadthBranches": it.get("breadthBranches", 2),
        "maxTurns": it.get("maxTurns", 6),
        "giveAnswerOnMiss": it.get("giveAnswerOnMiss", False),
        "courseId": course_id, "studentId": student_id,
        "knowledgePoint": knowledge_point,
        "stage": "probe",
        "bloomLevel": "remember",           # 从记忆层起步
        "branchTopics": [],                  # 广度扩展已覆盖主题
        "turns": [],
        "masteryEstimate": seed_mastery,
        "consecutiveMiss": 0,
        "createdAt": time.time(), "updatedAt": time.time(),
    }
    _prune()
    _SESSIONS[sess["sessionId"]] = sess
    return sess


def get(session_id: str) -> dict:
    s = _SESSIONS.get(session_id)
    if not s:
        raise InteractionError(f"session '{session_id}' 不存在或已过期")
    return s


def _bloom_idx(l: str) -> int:
    return BLOOM_LADDER.index(l)


def next_turn(sess: dict, llm_ask=None) -> dict:
    """生成下一轮提问。llm_ask(sess) -> {"question":..., "options": [...], "hint":...}
    无 LLM 时模板降级。深度的推进由 answer() 后的状态迁移决定, 本函数只按当前状态出题。"""
    kp = sess["knowledgePoint"]
    stage, bloom = sess["stage"], sess["bloomLevel"]
    socratic = sess["socraticLevel"]
    if llm_ask is not None:
        try:
            ask = llm_ask(sess)
            if ask and ask.get("question"):
                ask.update({"stage": stage, "bloomLevel": bloom})
                return ask
        except Exception:
            pass  # 降级到模板
    # ---- 模板降级(确定性可用) ----
    tpl = {
        ("probe", "remember"): f"先热身: {kp} 的基本定义是什么?(用自己的话)",
        ("deepen", "understand"): f"你能解释 {kp} 为什么重要吗?它解决了什么问题?",
        ("deepen", "apply"): f"给出一个 {kp} 的实际应用场景, 说明你会怎么用。",
        ("deepen", "analyze"): f"把 {kp} 拆成几个关键要素, 它们之间有什么依赖关系?",
        ("deepen", "evaluate"): f"两种 {kp} 的实现方案各有什么优劣?你会怎么选?",
        ("branch", "apply"): f"{kp} 与相邻主题(如 {', '.join(sess['branchTopics'][-2:]) or '相关技术'}) 有什么联系与区别?",
        ("branch", "analyze"): f"如果把 {kp} 应用到一个全新的行业场景, 需要哪些适配?",
        ("consolidate", "apply"): f"用三句话总结你今天关于 {kp} 掌握了什么, 以及还想知道什么?",
    }
    q = tpl.get((stage, bloom)) or tpl.get(("deepen", bloom)) or tpl[("probe", "remember")]
    ask = {"question": q, "options": [], "hint": "",
           "stage": stage, "bloomLevel": bloom}
    if socratic == "socratic" and stage == "deepen":
        ask["hint"] = "先别急着下结论——你的依据是什么?"
    return ask


def answer(sess: dict, correct: bool | None, student_text: str = "") -> dict:
    """提交本轮作答 → 状态迁移(深度爬升/降层补救/广度分支/总结收束)"""
    sess["turns"].append({"stage": sess["stage"], "bloomLevel": sess["bloomLevel"],
                          "correct": correct, "text": student_text[:500]})
    sess["updatedAt"] = time.time()
    ceiling_i = _bloom_idx(sess["bloomCeiling"])
    target_i = _bloom_idx(sess["depthTarget"])
    cur_i = _bloom_idx(sess["bloomLevel"])

    if correct is True:
        sess["consecutiveMiss"] = 0
        sess["masteryEstimate"] = min(0.95, sess["masteryEstimate"] + 0.08)
        if sess["stage"] == "probe":
            sess["stage"] = "deepen"
            sess["bloomLevel"] = "understand"
        elif sess["stage"] == "deepen":
            if cur_i < min(target_i, ceiling_i):
                sess["bloomLevel"] = BLOOM_LADDER[min(cur_i + 1, ceiling_i)]  # 深度爬升
            else:
                sess["stage"] = "branch"  # 深度达标 → 拓广度
        elif sess["stage"] == "branch":
            if len(sess["branchTopics"]) < sess["breadthBranches"]:
                sess["branchTopics"].append(f"{sess['knowledgePoint']}-拓展{len(sess['branchTopics']) + 1}")
                sess["bloomLevel"] = "apply" if cur_i > _bloom_idx("apply") else sess["bloomLevel"]
            else:
                sess["stage"] = "consolidate"  # 广度达标 → 总结
        else:  # consolidate
            pass
    else:
        sess["consecutiveMiss"] += 1
        sess["masteryEstimate"] = max(0.05, sess["masteryEstimate"] - 0.05)
        if sess["giveAnswerOnMiss"] and sess["consecutiveMiss"] >= 2:
            return {"transition": "remediate", "action": "reveal_answer",
                    "nextStage": "deepen", "bloomLevel": sess["bloomLevel"]}
        # 连续答错 → 降一档 Bloom(职教 high 脚手架), 否则同层换角度
        if cur_i > 0:
            sess["bloomLevel"] = BLOOM_LADDER[cur_i - 1]
        sess["stage"] = "deepen" if sess["stage"] in ("probe", "branch") else sess["stage"]
        if sess["stage"] == "branch":
            sess["stage"] = "deepen"

    finished = sess["stage"] == "consolidate" and len(
        [t for t in sess["turns"] if t["stage"] == "consolidate" and t["correct"]]) >= 1
    finished = finished or len(sess["turns"]) >= sess["maxTurns"]
    return {
        "transition": ("consolidate" if finished else
                       {"probe": "deepen", "deepen": "deepen",
                        "branch": "branch", "consolidate": "consolidate"}[sess["stage"]]),
        "stage": sess["stage"], "bloomLevel": sess["bloomLevel"],
        "masteryEstimate": round(sess["masteryEstimate"], 3),
        "branchTopics": sess["branchTopics"],
        "turn": len(sess["turns"]), "maxTurns": sess["maxTurns"],
        "finished": finished,
        "sm2Hint": {"skillId": sess["knowledgePoint"],
                    "suggestReview": sess["masteryEstimate"] < 0.7} if finished else None,
    }


def summary(sess: dict) -> dict:
    """会话报告: 深度足迹(Bloom 爬升轨迹)/广度足迹(分支主题)/掌握度估计"""
    return {
        "sessionId": sess["sessionId"], "profileId": sess["profileId"],
        "turns": len(sess["turns"]),
        "depthFootprint": [t["bloomLevel"] for t in sess["turns"]],
        "breadthFootprint": sess["branchTopics"],
        "maxBloomReached": max((t["bloomLevel"] for t in sess["turns"]),
                               key=lambda b: BLOOM_LADDER.index(b), default="remember"),
        "masteryEstimate": round(sess["masteryEstimate"], 3),
        "correctRate": (round(sum(1 for t in sess["turns"] if t["correct"]) / len(sess["turns"]), 2)
                        if sess["turns"] else 0.0),
    }
