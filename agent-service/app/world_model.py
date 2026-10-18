"""学习世界模型(Learning World Model) — OpenMAIC 吸收 4 / 调研 2025 世界模型文献落地

世界模型 = 智能体对环境动力学的"内部模拟器"。本项目落地为**学习世界模型**:
对"学生-知识-掌握度"环境建模, 支持反事实推演(what-if):
  state  s = 各知识点掌握度向量 + 学习风格 + 记忆衰减状态
  action a = 推荐的学习动作(讲解/练习/复习/跳过, 资源类型+知识点)
  transition T(s'|s,a) ≈ BKT 学习增益 + SM-2 遗忘曲线 + 协同效应(知识点前置传递)
  reward    R = Bloom 深度增益 + 时间成本惩罚

用途:
  1) 生成资源前模拟"不同难度档对学生掌握度的 3 步推演"→ 选最优教案(action selection)
  2) 深度交互引擎的下一动作预判(probe/deepen/branch 建议由模拟给出)
  3) KT 推荐的 what-if 解释("若先学 X, 掌握度预计 +0.12")

纯确定性参数化模型, 无需 GPU/训练数据, 与 BKT/DKT(kt-service)互补:
KT 管"现在掌握多少"(估计), 世界模型管"接下来会怎样"(推演)。
"""
from __future__ import annotations

import time
import uuid

# BKT 学习增益基线(无 GPU 保守参数, 后续可由 kt-service 数据校准)
LEARN_GAIN = 0.12          # 一次有效讲解/练习的平均掌握度增益
FORGET_LAMBDA = 0.06       # 每日遗忘率(SM-2 简化)
PREREQ_TRANSFER = 0.35     # 前置知识对下游的传递系数
TIME_COST_WEIGHT = 0.05    # 每预计学习分钟的奖励惩罚
MAX_MASTERY = 0.98

ACTIONS = ("explain", "exercise", "review", "skip")


class WorldModelError(Exception):
    pass


def init_state(masteries: dict[str, float] | None = None,
               learning_style: str = "unknown") -> dict:
    """环境状态 s0: {知识点: 掌握度} + 风格 + 版本号"""
    return {
        "stateId": uuid.uuid4().hex[:12],
        "masteries": dict(masteries or {}),
        "learningStyle": learning_style,
        "t": 0,                      # 模拟时间步(天)
        "version": 1,
    }


def _clamp(x: float) -> float:
    return max(0.0, min(MAX_MASTERY, x))


def transition(state: dict, action: str, knowledge_point: str,
               minutes: int = 20, prereqs: list[str] | None = None) -> tuple[dict, float]:
    """一步动力学 T(s'|s,a) → (新状态, reward)

    action: explain(讲解, 增益×0.9)/exercise(练习, 增益×1.2 且依赖当前水平)/
            review(复习, 抵消遗忘)/skip(仅衰减)
    reward: 该知识点掌握度增量 - 时间成本; 复习按"遗忘挽回"计奖励。
    """
    if action not in ACTIONS:
        raise WorldModelError(f"unknown action '{action}'(可用: {ACTIONS})")
    m = state["masteries"].get(knowledge_point, 0.3)
    # 1) 遗忘: 所有知识点按经过时间衰减
    new_masteries = {kp: _clamp(v * ((1 - FORGET_LAMBDA) ** state["t"]) if state["t"] else v)
                     for kp, v in state["masteries"].items()}
    # 2) 学习增益(BKT 风格: 未掌握空间越大增益越大, 练习再乘熟练激励)
    gain = 0.0
    if action == "explain":
        gain = LEARN_GAIN * 0.9 * (1 - m)
    elif action == "exercise":
        gain = LEARN_GAIN * 1.2 * (1 - m) * (0.5 + 0.5 * m)
    elif action == "review":
        gain = LEARN_GAIN * 0.5 * (1 - m)
    # 前置知识传递: 前置掌握度高 → 增益上浮
    if prereqs:
        pr = sum(state["masteries"].get(p, 0.3) for p in prereqs) / len(prereqs)
        gain *= (1 + PREREQ_TRANSFER * pr)
    new_m = _clamp(m + gain)
    new_masteries[knowledge_point] = new_m
    # 3) 奖励 = 掌握增量 - 时间成本(跳过为 0 成本 0 增益)
    reward = round(gain - (TIME_COST_WEIGHT * minutes / 20 if action != "skip" else 0.0), 4)
    ns = {**state, "masteries": new_masteries,
          "t": state["t"] + (1 if action != "skip" else 0),
          "version": state["version"] + 1}
    return ns, reward


def simulate(state: dict, plan: list[dict], horizon: int = 3) -> dict:
    """多步推演: plan=[{action, knowledgePoint, minutes?, prereqs?}] (截断到 horizon)

    返回逐轨迹 + 最终掌握度 + 累积奖励; 超出 horizon 的步骤忽略。
    """
    s = state
    trace, total_r = [], 0.0
    for step in plan[:horizon]:
        kp = step.get("knowledgePoint", "")
        if not kp:
            raise WorldModelError("plan 步骤缺少 knowledgePoint")
        s, r = transition(s, step.get("action", "explain"), kp,
                          int(step.get("minutes", 20)), step.get("prereqs"))
        trace.append({"step": step, "reward": r,
                      "mastery": s["masteries"][kp]})
        total_r += r
    return {"startMastery": state["masteries"],
            "endMastery": s["masteries"], "trace": trace,
            "totalReward": round(total_r, 4), "horizon": horizon}


def what_if(state: dict, knowledge_point: str, prereqs: list[str] | None = None) -> dict:
    """反事实对比: 对同一知识点, 4 种动作各推演 1 步 → 教案选择的依据"""
    table = []
    for a in ACTIONS:
        s2, r = transition(state, a, knowledge_point, 20, prereqs)
        table.append({"action": a, "reward": r,
                      "masteryAfter": s2["masteries"][knowledge_point]})
    best = max(table, key=lambda x: x["reward"])
    return {"knowledgePoint": knowledge_point, "options": table,
            "recommended": best["action"], "expectedGain": round(
                best["masteryAfter"] - state["masteries"].get(knowledge_point, 0.3), 3)}


def recommend_next(state: dict, candidates: list[str],
                   prereq_map: dict[str, list[str]] | None = None) -> dict:
    """动作选择: 对候选知识点逐个 what-if, 选期望增益最大者(供深度交互/推荐引用)"""
    prereq_map = prereq_map or {}
    best = None
    for kp in candidates:
        w = what_if(state, kp, prereq_map.get(kp))
        if best is None or w["expectedGain"] > best["expectedGain"]:
            best = {**w, "knowledgePoint": kp}
    return best or {"knowledgePoint": None, "expectedGain": 0}
