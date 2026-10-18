"""RAG 检索质量评测(Recall@5 / MRR / nDCG@5) + MCP 工具Server 能力模块。

评测集: evals/generation_quality_eval.jsonl(字段 query / relevant_ids / retrieved_ids)
MCP:   Model Context Protocol 工具描述(KT/课程/生成三大能力以 MCP tool 形式暴露,
       任意 MCP Client(Claude/Cursor/Dify)可经 /api/v1/mcp/tools 获取清单、
       /api/v1/mcp/call 同步调用。
"""
from __future__ import annotations

import json
import math
from typing import Any

# ================= RAG 质量指标 =================

def recall_at_k(retrieved: list[str], relevant: list[str], k: int = 5) -> float:
    if not relevant:
        return 0.0
    return len(set(retrieved[:k]) & set(relevant)) / len(relevant)


def mrr(retrieved: list[str], relevant: list[str]) -> float:
    for i, r in enumerate(retrieved, 1):
        if r in relevant:
            return 1.0 / i
    return 0.0


def ndcg_at_k(retrieved: list[str], relevant: list[str], k: int = 5) -> float:
    def dcg(items: list[str]) -> float:
        return sum(1.0 / math.log2(i + 2) for i, it in enumerate(items[:k]) if it in relevant)
    ideal = list(relevant)[:k]
    idcg = sum(1.0 / math.log2(i + 2) for i in range(len(ideal)))
    return dcg(retrieved) / idcg if idcg > 0 else 0.0


def evaluate_dataset(eval_path: str) -> dict:
    """逐条评测 + 汇总均值; eval JSONL 每行 {query, relevant_ids, retrieved_ids}"""
    n = recall_s = mrr_s = ndcg_s = 0
    per_query = []
    with open(eval_path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            item = json.loads(line)
            rel = item.get("relevant_ids", [])
            ret = item.get("retrieved_ids", [])
            r5 = recall_at_k(ret, rel, 5)
            m = mrr(ret, rel)
            n5 = ndcg_at_k(ret, rel, 5)
            recall_s += r5; mrr_s += m; ndcg_s += n5; n += 1
            per_query.append({"query": item.get("query"), "recall@5": round(r5, 3),
                              "mrr": round(m, 3), "ndcg@5": round(n5, 3)})
    if n == 0:
        return {"error": "empty eval set"}
    return {"n": n,
            "recall@5": round(recall_s / n, 4),
            "mrr": round(mrr_s / n, 4),
            "ndcg@5": round(ndcg_s / n, 4),
            "passThreshold": {"recall@5": 0.8, "mrr": 0.6, "ndcg@5": 0.75},
            "detail": per_query[:20]}


# ================= MCP 工具清单(Model Context Protocol) =================

MCP_TOOLS: list[dict[str, Any]] = [
    {
        "name": "kt_mastery",
        "description": "查询学生某知识点的实时掌握度(BKT+DKT 融合)",
        "inputSchema": {"type": "object",
                        "properties": {"studentId": {"type": "string"},
                                       "skillId": {"type": "string"}},
                        "required": ["studentId", "skillId"]},
        "endpoint": {"service": "kt", "method": "GET",
                     "path": "/kt/mastery?studentId={studentId}&skillId={skillId}"},
    },
    {
        "name": "kt_recommendations",
        "description": "获取学生个性化学习推荐(ZPD 桥接 + 薄弱点 TopN)",
        "inputSchema": {"type": "object",
                        "properties": {"studentId": {"type": "string"},
                                       "limit": {"type": "integer", "default": 5}},
                        "required": ["studentId"]},
        "endpoint": {"service": "kt", "method": "GET",
                     "path": "/kt/recommendations/{studentId}?limit={limit}"},
    },
    {
        "name": "kt_review_plan",
        "description": "获取 SM-2 间隔重复复习计划(Ebbinghaus 遗忘曲线修正)",
        "inputSchema": {"type": "object",
                        "properties": {"studentId": {"type": "string"}},
                        "required": ["studentId"]},
        "endpoint": {"service": "kt", "method": "GET", "path": "/kt/review/{studentId}"},
    },
    {
        "name": "course_search",
        "description": "按主题/知识点检索课程资源(供 RAG 候选集)",
        "inputSchema": {"type": "object",
                        "properties": {"courseId": {"type": "string"},
                                       "keyword": {"type": "string"}},
                        "required": ["courseId", "keyword"]},
        "endpoint": {"service": "course", "method": "GET",
                     "path": "/resources?courseId={courseId}&keyword={keyword}"},
    },
    {
        "name": "generate_resource",
        "description": "触发 6 类课程资源 AI 生成(练习题/讲解文档/实训任务书等)",
        "inputSchema": {"type": "object",
                        "properties": {"resourceType": {"type": "string"},
                                       "knowledgePoint": {"type": "string"},
                                       "studentId": {"type": "string", "default": "guest"}},
                        "required": ["resourceType", "knowledgePoint"]},
        "endpoint": {"service": "agent", "method": "POST",
                     "path": "/api/v1/agent/generation"},
    },
]


def mcp_call(service_base: dict, tool_name: str, args: dict) -> dict:
    """执行一次 MCP 工具调用(同步 HTTP; service_base: {svc: url}"""
    import urllib.request
    tool = next((t for t in MCP_TOOLS if t["name"] == tool_name), None)
    if tool is None:
        return {"error": f"unknown tool: {tool_name}", "available": [t["name"] for t in MCP_TOOLS]}
    ep = tool["endpoint"]
    if ep["service"] not in service_base:
        return {"error": f"service {ep['service']} not reachable"}
    base = service_base[ep["service"]]
    path = ep["path"].format(**{**{"limit": 5, "keyword": "", "studentId": "guest"}, **args})
    url = base.rstrip("/") + path
    try:
        req = urllib.request.Request(url, method=ep["method"])
        if ep["method"] == "POST":
            req.add_header("Content-Type", "application/json")
            body = json.dumps(args).encode()
            with urllib.request.urlopen(req, body, timeout=30) as resp:
                return json.loads(resp.read())
        with urllib.request.urlopen(req, timeout=30) as resp:
            return json.loads(resp.read())
    except Exception as e:  # noqa: BLE001
        return {"error": str(e), "tool": tool_name}
