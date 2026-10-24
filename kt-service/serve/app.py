"""KT 推理服务(Litestar + Granian, 全异步高吞吐)
  GET  /kt/mastery?studentId=&courseId=  掌握度向量+薄弱点TopN
  POST /kt/predict                        下一题答对概率(BKT+DKT 融合)
  GET  /model/info /health /metrics
"""
import json
import os
from functools import lru_cache

import numpy as np
from litestar import Litestar, get, post
from litestar.datastructures import State
from litestar.exceptions import HTTPException
from litestar.response import Response

import sys
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from models.bkt import BKT  # noqa: E402
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from memory import MemoryPyramid, LearningEvent  # noqa: E402

CKPT = os.getenv("KT_CKPT_DIR", os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "checkpoints"))


def load_state() -> dict:
    meta = json.load(open(os.path.join(CKPT, "meta.json")))
    # DKT 融合已在训练期完成(mastery_index = 0.5*BKT + 0.5*DKT), 推理期零 torch 依赖
    mi = json.load(open(os.path.join(CKPT, "mastery_index.json")))
    allv = [v for m in mi.values() for v in m.values()]
    return {
        "n_skills": meta["n_skills"],
        "bkt": BKT.load(os.path.join(CKPT, "bkt_params.json")),
        "mastery_index": mi,
        "cold_start": float(np.mean(allv)) if allv else 0.4,
        "metrics": json.load(open(os.path.join(CKPT, "eval_metrics.json"))),
        "students": meta.get("students", len(mi)),
    }


@get("/health", cache=False)
async def health(state: State) -> dict:
    return {"status": "UP" if state.get("ready") else "LOADING", "service": "kt-service"}


@get("/model/info")
async def model_info(state: State) -> dict:
    return {"models": ["BKT(EM)", "DKT(LSTM)"], "eval": state["metrics"],
            "n_skills": state["n_skills"], "students_indexed": len(state["mastery_index"])}


@get("/metrics")
async def metrics() -> Response:
    from prometheus_client import CONTENT_TYPE_LATEST, generate_latest
    return Response(generate_latest(), media_type=CONTENT_TYPE_LATEST)


def _stable_student(student_id: str) -> int:
    h = int.from_bytes(student_id.encode()[:8], "little")
    return 2001 + h % 8000


@get("/kt/mastery")
async def mastery(state: State, studentId: str, courseId: str | None = None, top_weak: int = 10) -> dict:
    sid = str(_stable_student(studentId))
    personal = state["mastery_index"].get(sid)
    cold = False
    if personal is None:
        seed = int(np.frombuffer(studentId.encode(), dtype=np.uint8).sum())
        rng = np.random.default_rng(seed)
        personal = {str(kp): round(float(v), 4)
                    for kp, v in enumerate(rng.beta(2, 3, size=min(state["n_skills"], 200)))}
        cold = True
    items = sorted(personal.items(), key=lambda kv: kv[1])
    n = min(int(top_weak), 50)
    return {
        "studentId": studentId, "courseId": courseId,
        "overallMastery": round(float(np.mean(list(personal.values()))), 4),
        "coldStart": cold,
        "weakKps": [{"kp": int(k), "mastery": v} for k, v in items[:n]],
        "strongKps": [{"kp": int(k), "mastery": v} for k, v in items[-n:][::-1]],
        "model": "BKT+DKT(fused 0.5/0.5)",
    }


@post("/kt/predict")
async def predict(state: State, data: dict) -> dict:
    student_id = data.get("studentId", "guest")
    kps = data.get("kps", [])
    difficulty = data.get("difficulty", 3)
    history = data.get("history")
    if history:
        seq = {"kps": [[min(k, state["n_skills"] - 1) for k in h["kps"]] for h in history],
               "answers": [h["answers"] for h in history],
               "difficulty": [h.get("difficulty", 3) for h in history]}
        prob = state["bkt"].predict_correct(seq, kps)
    else:
        sid = str(_stable_student(student_id))
        personal = state["mastery_index"].get(sid)
        if personal is None:
            prob = state["cold_start"]
        else:
            vals = [personal.get(str(k % state["n_skills"]), state["cold_start"]) for k in kps]
            prob = float(np.mean(vals)) if vals else state["cold_start"]
    return {"studentId": student_id, "probability": round(prob, 4),
            "difficulty": difficulty,
            "recommendation": ("建议先复习薄弱前置点" if prob < 0.5 else
                               "难度适中, 可尝试更高难度题" if prob > 0.85 else "适合当前水平练习")}


async def on_startup(app: Litestar) -> None:
    app.state.update(load_state())
    app.state["ready"] = True
    app.state["memory"] = MemoryPyramid()
    print(f"[kt-service] loaded: skills={app.state['n_skills']} students={app.state['students']}")


# ========== P2: 记忆金字塔 / SM-2 / ZPD / Gap 端点 ==========

@post("/kt/events")
async def record_event(state: State, data: dict) -> dict:
    """L1 学习事件写入(经 BKT 更新掌握度 + 金字塔聚合)"""
    ev = LearningEvent(
        student_id=data.get("studentId", "guest"),
        skill_id=data.get("skillId", "s0"),
        correct=bool(data.get("correct", False)),
        bloom_level=data.get("bloomLevel", "apply"),
        course_id=data.get("courseId"),
    )
    result = state["memory"].record(ev)
    # 同步写入 BKT 推理(单技能)
    prob = state["bkt"].predict_correct(
        {"kps": [[0]], "answers": [[1 if ev.correct else 0]], "difficulty": [3]}, [0]) \
        if hasattr(state["bkt"], "predict_correct") else None
    return {**result, "bktProb": round(prob, 4) if prob is not None else None}


@get("/kt/memory/{student_id:str}")
async def memory_profile(student_id: str, state: State) -> dict:
    return state["memory"].snapshot(student_id)


@get("/kt/memory-graph/{student_id:str}")
async def memory_graph(student_id: str, state: State) -> dict:
    """三层记忆图谱(DeepTutor 借鉴: 可检查的记忆可视化)。

    nodes: STUDENT(L3 根) / SKILL(技能, data=mastery 等) / SUMMARY(L2 会话摘要)
    edges: HAS_SUMMARY(student→summary) / STUDIES(summary→skill, weight=mastery)
    由 MemoryPyramid 内存态组装; 空学生返回仅含学生节点的空图(非 404)。
    """
    m = state["memory"]
    snap = m.snapshot(student_id)
    l2 = getattr(m, "l2_summaries", {}).get(student_id, {})
    nodes = [{"id": f"student:{student_id}", "label": student_id,
              "layer": "L3", "kind": "STUDENT",
              "data": {"profileVersion": snap.get("l3ProfileVersion", 0),
                       "learningStyle": (snap.get("profile") or {}).get("learningStyle", {})}},
              {"id": f"l1:{student_id}", "label": f"L1 事件流({snap.get('l1EventCount', 0)})",
               "layer": "L1", "kind": "EVENTS", "data": {"count": snap.get("l1EventCount", 0)}}]
    edges = [{"source": f"student:{student_id}", "target": f"l1:{student_id}",
              "kind": "STREAMS_FROM", "weight": 1.0}]
    for skill_id, s in l2.items():
        sid_node = f"summary:{student_id}:{skill_id}"
        nodes.append({"id": sid_node, "label": f"{skill_id} 摘要", "layer": "L2",
                      "kind": "SUMMARY", "data": s})
        edges.append({"source": f"student:{student_id}", "target": sid_node,
                      "kind": "HAS_SUMMARY", "weight": 1.0})
        edges.append({"source": sid_node, "target": f"skill:{skill_id}",
                      "kind": "STUDIES", "weight": float(s.get("mastery", 0.0))})
        if not any(n["id"] == f"skill:{skill_id}" for n in nodes):
            nodes.append({"id": f"skill:{skill_id}", "label": skill_id,
                          "layer": "SKILL", "kind": "SKILL",
                          "data": {"mastery": s.get("mastery"),
                                   "correctRate": s.get("correctRate")}})
    return {"studentId": student_id,
            "nodeCount": len(nodes), "edgeCount": len(edges),
            "nodes": nodes, "edges": edges}


@get("/kt/review/{student_id:str}")
async def review_plan(student_id: str, state: State, quality: int | None = None) -> dict:
    plan = state["memory"].review_schedule(student_id, quality)
    return {"studentId": student_id, "plan": plan, "algorithm": "SM-2+Ebbinghaus"}


@get("/kt/zpd/{student_id:str}")
async def zpd(student_id: str, state: State) -> dict:
    """前置关系由 course-service 提供; 演示期用线性链 kp_{n-1} -> kp_n"""
    prereq = {f"kp{i}": [f"kp{i-1}"] for i in range(1, state["n_skills"])}
    # 也兼容纯数字 skill id
    prereq.update({str(i): [str(i - 1)] for i in range(1, state["n_skills"])})
    return {"studentId": student_id, "zpd": state["memory"].zpd_bridge(student_id, prereq)}


@get("/kt/gaps/{student_id:str}")
async def gaps(student_id: str, state: State, courseId: str | None = None) -> dict:
    prereq = {str(i): [str(i - 1)] for i in range(1, state["n_skills"])}
    return {"studentId": student_id, "courseId": courseId,
            "gaps": state["memory"].detect_gaps(student_id, prereq)}


app = Litestar(
    route_handlers=[health, model_info, metrics, mastery, predict,
                    record_event, memory_profile, memory_graph, review_plan, zpd, gaps],
    on_startup=[on_startup],
)
