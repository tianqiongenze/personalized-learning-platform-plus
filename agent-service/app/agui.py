"""AG-UI (Agent User Interaction) 协议支持 — 调研: docs.ag-ui.com

把 agent 事件流标准化为 AG-UI 16 类事件(本项目实现核心子集), 供三端前端
(Web WS / 小程序 WS / PC SSE)统一消费, 替代各端自定义 SSE 格式:
  POST /agui/runs   → SSE 事件流(RUN_STARTED → TEXT_MESSAGE_* → RUN_FINISHED)
  事件: runStarted/RunFinished/TextMessageContent/ToolCallStart/End/Custom...
后续前端接入 AG-UI SDK 后即免适配直连。与既有 /assistant/stream(旧 SSE)并存,
旧端点保持兼容, 新端点为标准事件流。
"""
from __future__ import annotations

import json
import time
import uuid


def _ev(event_type: str, **kw) -> dict:
    return {"type": event_type, "timestamp": int(time.time() * 1000), **kw}


def run_events(request: dict):
    """生成 AG-UI 事件流(生成器; 由调用方包 SSE)。
    request: {message, threadId?, runId?, pageContext?, studentId?}
    内部复用 assistant_graph 的 ReAct 循环; LLM 不可用时降级为模板应答(仍发标准事件)。
    """
    thread_id = request.get("threadId") or uuid.uuid4().hex[:12]
    run_id = request.get("runId") or uuid.uuid4().hex[:12]
    message = request.get("message", "")
    yield _ev("RUN_STARTED", threadId=thread_id, runId=run_id)
    msg_id = uuid.uuid4().hex[:12]
    yield _ev("TEXT_MESSAGE_START", messageId=msg_id, role="assistant")
    reply = None
    # 尝试真 LLM(流式); 失败→模板降级
    try:
        from app.agents.assistant_graph import assistant_graph
        from langchain_core.messages import HumanMessage
        import asyncio

        async def _stream():
            out = []
            state = {"messages": [HumanMessage(content=message)],
                     "page_context": request.get("pageContext", "dashboard"),
                     "student_id": request.get("studentId", "guest")}
            async for chunk in assistant_graph.astream(state, stream_mode="updates"):
                for node, upd in chunk.items():
                    msgs = upd.get("messages", []) if isinstance(upd, dict) else []
                    for m in msgs:
                        c = getattr(m, "content", "")
                        if c and not getattr(m, "tool_calls", None):
                            out.append(c)
            return out

        loop = _get_loop()
        parts = loop.run_until_complete(asyncio.wait_for(_stream(), timeout=60)) \
            if loop else []
        reply = "".join(parts) if parts else None
    except Exception:  # noqa: BLE001
        reply = None
    if not reply:
        reply = (f"[模板降级] 已收到你的问题: {message[:80]}。"
                 "当前 LLM 算力受限, 请稍后重试获取 AI 生成回答; 学情/推荐数据可正常查询。")
    # 模拟分片推送(标准 TEXT_MESSAGE_CONTENT 事件)
    for i in range(0, len(reply), 24):
        yield _ev("TEXT_MESSAGE_CONTENT", messageId=msg_id,
                  delta=reply[i:i + 24])
    yield _ev("TEXT_MESSAGE_END", messageId=msg_id)
    yield _ev("RUN_FINISHED", threadId=thread_id, runId=run_id)


def _get_loop():
    import asyncio
    try:
        return asyncio.get_event_loop()
    except RuntimeError:
        return None


def sse_format(event: dict) -> str:
    return f"data: {json.dumps(event, ensure_ascii=False)}\n\n"
