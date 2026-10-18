"""A2A (Agent2Agent) 协议支持 — 调研: a2a-protocol.org v1.0 / Linux Foundation

让外部智能体(其它学校/平台的 Agent、第三方辅导 Bot)可以发现并委派任务给本系统:
  GET  /.well-known/agent-card.json   A2A Agent Card(能力发现, v1.0 规范字段)
  POST /a2a/v1/tasks                  接收委派任务(JSON-RPC 风格 message/send 语义)
  GET  /a2a/v1/tasks/{id}             查询任务状态与 artifacts
任务在内存中执行(映射到既有能力: 生成资源/学情查询/推荐), 完整 A2A(gRPC/推送回调)
列入 P4; 本实现覆盖 A2A 最小互操作集(discovery + sync/async tasks), 与 MCP 互补:
MCP=agent→工具, A2A=agent→agent。
"""
from __future__ import annotations

import time
import uuid

from app import config

_SERVICE = "qianrenqianmian-agent"
_TASKS: dict[str, dict] = {}
MAX_TASKS = 200


def agent_card() -> dict:
    """A2A v1.0 Agent Card — 能力发现(对外只暴露抽象技能, 不暴露内部实现)"""
    base = f"http://agent-service.qianrenqianmian:8090"
    return {
        "name": "个性化学习智能体",
        "description": ("千人千面个性化课程资源系统: 六类资源生成、BKT/DKT 学情追踪、"
                        "记忆金字塔复习、ZPD 推荐、深度交互、教育层级画像"),
        "url": base,
        "version": config.__dict__.get("VERSION", "8.4.0"),
        "protocolVersion": "1.0",
        "preferredTransport": "JSONRPC",
        "additionalInterfaces": [{"transport": "JSONRPC", "url": base},
                                 {"transport": "HTTP+JSON", "url": base}],
        "capabilities": {"streaming": True, "pushNotifications": True,
                         "stateTransitionHistory": False},
        "defaultInputModes": ["application/json", "text/plain"],
        "defaultOutputModes": ["application/json", "text/plain"],
        "provider": {"organization": "重庆市教委重点项目", "url": base},
        "skills": [
            {"id": "generate_resource", "name": "教学资源生成",
             "description": "六类资源(练习/讲解/实训任务书/微课脚本/视频脚本/知识点讲解), 支持教育层级画像",
             "tags": ["generation", "education", "aigc"]},
            {"id": "learning_analytics", "name": "学情分析",
             "description": "BKT+DKT 掌握度/记忆金字塔/SM-2 复习计划/技能缺口",
             "tags": ["kt", "mastery", "review"]},
            {"id": "recommend_next", "name": "下一步学习推荐",
             "description": "ZPD 桥接 + 世界模型 what-if 增益推演",
             "tags": ["recommendation", "zpd"]},
            {"id": "deep_interaction", "name": "深度交互对话",
             "description": "Bloom 爬升式深度/广度交互会话",
             "tags": ["tutoring", "bloom"]},
        ],
    }


def _prune() -> None:
    if len(_TASKS) >= MAX_TASKS:
        for k in sorted(_TASKS, key=lambda k: _TASKS[k]["createdAt"])[:50]:
            _TASKS.pop(k, None)


def _skill_ids() -> list[str]:
    return [s["id"] for s in agent_card()["skills"]]


def send_task(data: dict) -> dict:
    """message/send: {skillId, message:{parts:[{text}]}, contextId?,
    notification?: {url, token?}} → task 对象
    带 notification 时执行完成后 POST 回调(push notification, P4)"""
    skill = data.get("skillId", "")
    if skill not in _skill_ids():
        return {"error": f"unknown skill '{skill}'(可用: {_skill_ids()})"}
    task = {
        "id": uuid.uuid4().hex[:16],
        "contextId": data.get("contextId") or uuid.uuid4().hex[:12],
        "skillId": skill,
        "status": {"state": "submitted", "timestamp": int(time.time() * 1000)},
        "message": data.get("message", {}),
        "artifacts": [],
        "notification": data.get("notification"),   # {url, token?}
    }
    _prune()
    _TASKS[task["id"]] = task
    return task


def push_notification(task: dict) -> dict:
    """A2A push notification: 任务终态异步回调委派方"""
    n = task.get("notification") or {}
    url = n.get("url")
    if not url:
        return {"pushed": False, "reason": "no notification url"}
    try:
        import httpx
        headers = {}
        if n.get("token"):
            headers["Authorization"] = f"Bearer {n['token']}"
        r = httpx.post(url, json={
            "taskId": task["id"], "contextId": task["contextId"],
            "status": task["status"]["state"],
            "artifacts": task["artifacts"]}, headers=headers, timeout=15)
        return {"pushed": r.status_code in (200, 201, 202), "status": r.status_code}
    except Exception as e:  # noqa: BLE001
        return {"pushed": False, "reason": str(e)[:120]}


def complete_task(task_id: str, artifact: dict) -> dict | None:
    t = _TASKS.get(task_id)
    if not t:
        return None
    t["artifacts"].append(artifact)
    t["status"] = {"state": "completed", "timestamp": int(time.time() * 1000)}
    return t


def get_task(task_id: str) -> dict | None:
    return _TASKS.get(task_id)


def execute_task(task: dict) -> dict:
    """把 A2A 任务映射到内部能力(fail-soft; 生成类走模板降级即可同步完成)"""
    skill = task["skillId"]
    text = ""
    for p in task.get("message", {}).get("parts", []):
        if isinstance(p, dict) and "text" in p:
            text += p["text"]
    try:
        if skill == "generate_resource":
            from app.agents.generation_graph import generation_graph
            import asyncio
            result = asyncio.get_event_loop().run_until_complete(
                generation_graph.ainvoke({
                    "resource_type": "KNOWLEDGE_POINT_TALK",
                    "student_id": "a2a-external", "course_id": "",
                    "knowledge_point": text[:60] or "未指定知识点",
                    "requirements": "来自 A2A 外部智能体委派",
                    "retrieved_context": [], "drafts": [], "messages": [],
                    "revision_round": 0}))
            artifact = {"name": "resource.md", "mimeType": "text/markdown",
                        "content": result.get("final_content", "")[:4000]}
        elif skill == "learning_analytics":
            import httpx
            r = httpx.get(f"{config.KT_BASE}/api/v1/kt/mastery",
                          params={"studentId": text[:40] or "guest"}, timeout=15)
            artifact = {"name": "mastery.json", "mimeType": "application/json",
                        "content": json_dumps(r.json())}
        else:  # recommend_next → 学习世界模型动作选择(P4 接线); 其余给引导应答
            import json as _json
            from app import world_model as wm
            req = {}
            try:
                req = _json.loads(text) if text.strip().startswith("{") else {}
            except Exception:
                req = {}
            if req.get("candidates"):
                st = wm.init_state(req.get("masteries"),
                                   req.get("learningStyle", "unknown"))
                r = wm.recommend_next(st, req["candidates"], req.get("prereqMap"))
                artifact = {"name": "recommendation.json",
                            "mimeType": "application/json",
                            "content": _json.dumps(r, ensure_ascii=False)}
            else:
                artifact = {"name": "reply.txt", "mimeType": "text/plain",
                            "content": (f"[{_SERVICE}] 已接收 '{skill}' 委派: {text[:120]}。"
                                        "recommend_next 需 JSON: {masteries, candidates, prereqMap?}"
                                        "; 深度交互请使用 interaction 端点。")}
    except Exception as e:  # noqa: BLE001
        artifact = {"name": "error.txt", "mimeType": "text/plain",
                    "content": f"task execution degraded: {e}"}
    done = complete_task(task["id"], artifact) or task
    # P4: push notification 回调(带 notification 配置时)
    done["pushResult"] = push_notification(done)
    return done


def json_dumps(obj) -> str:
    import json
    return json.dumps(obj, ensure_ascii=False)
