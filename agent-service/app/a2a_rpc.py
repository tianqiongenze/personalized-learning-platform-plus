"""A2A 官方必选传输: JSON-RPC 2.0 over HTTP(含 SSE streaming) — P4 后续

对齐 A2A v1.0 规范的 JSONRPC 传输层, 供标准 A2A Client(如 google-a2a SDK)直连:
  POST /a2a/rpc                     JSON-RPC 2.0 单条: message/send, tasks/get
  POST /a2a/rpc  (Accept: SSE)      message/stream → SSE: status-update / artifact-update 事件
JSON-RPC 方法:
  message/send   {message:{role,parts}, metadata:{skillId}} → task
  message/stream 同上但返回 SSE 事件流
  tasks/get      {id} → task
协议错误码: -32601 method not found / -32602 invalid params / -32000 task unknown
"""
from __future__ import annotations

import json
import time
import uuid

from app import a2a

JSONRPC_ERRORS = {"parse": -32700, "invalid": -32602, "method": -32601, "app": -32000}


def _rpc_result(req_id, result) -> dict:
    return {"jsonrpc": "2.0", "id": req_id, "result": result}


def _rpc_error(req_id, code: int, message: str) -> dict:
    return {"jsonrpc": "2.0", "id": req_id,
            "error": {"code": code, "message": message}}


def _extract_message_params(params: dict) -> dict:
    """A2A message 结构 → 内部 send_task 入参"""
    msg = params.get("message") or {}
    meta = params.get("metadata") or msg.get("metadata") or {}
    skill = meta.get("skillId", "")
    text = "".join(p.get("text", "") for p in msg.get("parts", [])
                   if isinstance(p, dict) and p.get("kind") in ("text", None))
    return {"skillId": skill,
            "message": {"parts": [{"text": text}]},
            "contextId": params.get("contextId") or msg.get("contextId"),
            "notification": meta.get("notification")}


def handle_rpc(payload: dict) -> dict:
    """单条 JSON-RPC(message/send | tasks/get)"""
    if not isinstance(payload, dict) or payload.get("jsonrpc") != "2.0":
        return _rpc_error(None, JSONRPC_ERRORS["invalid"], "jsonrpc 2.0 required")
    method = payload.get("method", "")
    req_id = payload.get("id")
    params = payload.get("params") or {}
    try:
        if method == "message/send":
            task = a2a.send_task(_extract_message_params(params))
            if task.get("error"):
                return _rpc_error(req_id, JSONRPC_ERRORS["invalid"], task["error"])
            return _rpc_result(req_id, a2a.execute_task(task))
        if method == "tasks/get":
            t = a2a.get_task(params.get("id", ""))
            if not t:
                return _rpc_error(req_id, JSONRPC_ERRORS["app"],
                                  f"task '{params.get('id')}' unknown")
            return _rpc_result(req_id, t)
        return _rpc_error(req_id, JSONRPC_ERRORS["method"], f"method '{method}' not found")
    except Exception as e:  # noqa: BLE001
        return _rpc_error(req_id, JSONRPC_ERRORS["app"], str(e)[:150])


def stream_rpc(payload: dict):
    """message/stream → SSE 事件行(status-update → artifact-update → status completed)"""
    method = payload.get("method", "")
    req_id = payload.get("id")
    if method != "message/stream":
        yield "event: error\ndata: " + json.dumps(
            _rpc_error(req_id, JSONRPC_ERRORS["method"],
                       "streaming only for message/stream"), ensure_ascii=False) + "\n\n"
        return
    task = a2a.send_task(_extract_message_params(payload.get("params") or {}))
    if task.get("error"):
        yield "event: error\ndata: " + json.dumps(
            _rpc_error(req_id, JSONRPC_ERRORS["invalid"], task["error"]),
            ensure_ascii=False) + "\n\n"
        return
    tid, ctx = task["id"], task["contextId"]

    def ev(kind: str, body: dict) -> str:
        return "event: %s\ndata: %s\n\n" % (
            kind, json.dumps({"jsonrpc": "2.0", "id": req_id,
                              "result": {"taskId": tid, "contextId": ctx, **body}},
                             ensure_ascii=False))

    yield ev("status-update", {"status": {"state": "working"}})
    done = a2a.execute_task(task)
    for art in done.get("artifacts", []):
        yield ev("artifact-update", {"artifact": art})
    yield ev("status-update", {"status": {"state": done["status"]["state"]},
                               "final": True})
