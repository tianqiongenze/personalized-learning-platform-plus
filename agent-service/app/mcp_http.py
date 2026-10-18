"""MCP Streamable HTTP 传输(P4 后续) — 对齐 MCP 2026 规范的 streamable-http

既有 /api/v1/mcp/tools|call 为自定义 RPC(兼容保留); 本模块提供标准 MCP 传输,
主流 MCP Client(Claude/Cursor/ChatGPT connectors)可直连:
  POST /mcp   JSON-RPC 2.0: initialize / tools/list / tools/call / ping
              响应可 JSON(单条) 或 SSE 流(Accept: text/event-stream 时)
  GET  /mcp   SSE(服务器推送; 本实现按需回 JSON, 不开长连通道)
会话: Mcp-Session-Id 响应头(无状态实现, 头透传忽略即可)。
"""
from __future__ import annotations

import json

from app.rag_mcp import mcp_call as _call, MCP_TOOLS

PROTOCOL_VERSION = "2026-06-18"
SERVER_INFO = {"name": "qianrenqianmian-agent", "version": "8.6.0"}


def _tools_payload() -> list[dict]:
    return [{
        "name": t["name"], "description": t["description"],
        "inputSchema": {"type": "object", "properties": t.get("params", {}),
                        "required": t.get("required", [])},
    } for t in MCP_TOOLS]


def _result(req_id, content: list[dict], structured=None) -> dict:
    r = {"jsonrpc": "2.0", "id": req_id,
         "result": {"content": content, "isError": False}}
    if structured is not None:
        r["result"]["structuredContent"] = structured
    return r


def _err(req_id, code: int, message: str) -> dict:
    return {"jsonrpc": "2.0", "id": req_id, "error": {"code": code, "message": message}}


def handle_mcp(payload: dict, svc_bases: dict) -> dict:
    """MCP JSON-RPC 单条处理"""
    req_id = payload.get("id")
    method = payload.get("method", "")
    params = payload.get("params") or {}
    if method == "initialize":
        return {"jsonrpc": "2.0", "id": req_id, "result": {
            "protocolVersion": PROTOCOL_VERSION,
            "capabilities": {"tools": {"listChanged": True}},
            "serverInfo": SERVER_INFO,
            "instructions": "教育智能体 MCP 服务: 5 个工具(学情/推荐/复习/检索/生成)"}}
    if method == "notifications/initialized":
        return {"jsonrpc": "2.0", "id": req_id, "result": {}}
    if method == "ping":
        return {"jsonrpc": "2.0", "id": req_id, "result": {}}
    if method == "tools/list":
        return {"jsonrpc": "2.0", "id": req_id, "result": {"tools": _tools_payload()}}
    if method == "tools/call":
        name = params.get("name", "")
        args = params.get("arguments") or {}
        try:
            out = _call(svc_bases, name, args)
            content = [{"type": "text", "text": json.dumps(out, ensure_ascii=False)[:8000]}]
            return _result(req_id, content, structured=out if isinstance(out, dict) else None)
        except Exception as e:  # noqa: BLE001
            return {"jsonrpc": "2.0", "id": req_id,
                    "result": {"content": [{"type": "text", "text": f"tool error: {e}"}],
                               "isError": True}}
    return _err(req_id, -32601, f"method '{method}' not found")
