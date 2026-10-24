#!/usr/bin/env python3
"""seed_milvus_kp.py — 把 TiDB 新课知识点灌入 Milvus edu_resources collection。

修复: 生产向量库从未灌入(Milvus 只有 ESP 旧 collection), agent RAG 检索链恒空。
内容: 课程重填充后的 45 门新课(BK/ZK/ZB 前缀)的知识点 code/name/description。
向量: embed-proxy(bge-m3, 1024)。幂等: 已存在则先 drop 再建(演示库语义)。
标注集联动: 同步生成 evals/rag_retrieval_eval_online.jsonl(以现库 kp code 为 gold)。
环境: TIDB_HOST/PORT/USER/PASSWORD, EMBED_URL, MILVUS_URL
"""
from __future__ import annotations

import json
import os
import urllib.request

import pymysql

TIDB = dict(host=os.getenv("TIDB_HOST", "tidb.infra.svc.cluster.local"),
            port=int(os.getenv("TIDB_PORT", "4000")), user=os.getenv("TIDB_USER", "root"),
            password=os.getenv("TIDB_PASSWORD", ""), database="course_db", charset="utf8mb4")
EMBED_URL = os.getenv("EMBED_URL", "http://embed-proxy.ai-platform.svc.cluster.local:8088")
MILVUS = os.getenv("MILVUS_URL", "http://milvus.infra.svc.cluster.local:19530")
COLLECTION = "edu_resources"
EMBED_MODEL = os.getenv("EMBEDDING_MODEL", "bge-m3")
DIM = 1024


def http_json(url: str, payload: dict, timeout: int = 60) -> dict:
    req = urllib.request.Request(url, data=json.dumps(payload).encode(),
                                 headers={"Content-Type": "application/json"}, method="POST")
    try:
        return json.load(urllib.request.urlopen(req, timeout=timeout))
    except urllib.error.HTTPError as e:
        body = e.read()[:200] if e.fp else b""
        raise RuntimeError(f"HTTP {e.code} on {url}: {body}") from e


def _embed_one(text: str, timeout: int = 180) -> list[float]:
    d = http_json(f"{EMBED_URL}/v1/embeddings",
                  {"model": EMBED_MODEL, "input": [text]}, timeout=timeout)
    return d["data"][0]["embedding"]


def embed(texts: list[str]) -> list:
    """逐条 embedding(ollama 后端 CPU 推理慢, 间歇 503): 失败跳过+多轮补救,
    保证整体收敛; 仍失败的条目以 None 占位(插入时丢弃)。"""
    import time as _t
    out: list = [None] * len(texts)
    pending = list(range(len(texts)))
    for round_no, backoff in ((1, 3), (2, 15), (3, 60)):
        retry = []
        for i in pending:
            try:
                out[i] = _embed_one(texts[i])
            except Exception:
                retry.append(i)
        pending = retry
        print(f"  [embed] round {round_no}: 剩余失败 {len(pending)}/{len(texts)}", flush=True)
        if not pending:
            break
        _t.sleep(backoff)
    failed = [i for i, v in enumerate(out) if v is None]
    if failed:
        print(f"  [embed] 最终失败 {len(failed)} 条(将跳过): {failed[:10]}", flush=True)
    return out


def main() -> None:
    conn = pymysql.connect(connect_timeout=10, **TIDB)
    cur = conn.cursor()
    cur.execute("""SELECT k.code, k.name, k.description, c.name
                   FROM knowledge_points k JOIN courses c ON k.course_id=c.id
                   WHERE c.code REGEXP '^(BK|ZK|ZB)' AND k.status='ACTIVE'""")
    rows = cur.fetchall()
    print(f"[seed] kp rows: {len(rows)}")
    texts = [f"{r[3]} {r[1]} {r[2] or ''}".strip() for r in rows]

    # drop + create(演示库幂等重建)
    try:
        http_json(f"{MILVUS}/v2/vectordb/collections/drop", {"collectionName": COLLECTION})
        print("[seed] dropped old collection")
    except Exception:
        pass
    r = http_json(f"{MILVUS}/v2/vectordb/collections/create", {
        "collectionName": COLLECTION,
        "schema": {
            "fields": [
                {"fieldName": "doc_id", "dataType": "VarChar", "isPrimary": True,
                 "elementTypeParams": {"max_length": "64"}},
                {"fieldName": "content", "dataType": "VarChar",
                 "elementTypeParams": {"max_length": "4096"}},
                {"fieldName": "metadata", "dataType": "JSON"},
                {"fieldName": "embedding", "dataType": "FloatVector",
                 "elementTypeParams": {"dim": str(DIM)}},
            ]},
        "indexParams": [{"fieldName": "embedding", "metricType": "COSINE",
                         "indexType": "AUTOINDEX", "params": {}}]})
    assert r.get("code") == 0, f"create failed: {r}"
    print("[seed] collection created")

    vecs = embed(texts)
    ok_idx = [j for j in range(len(rows)) if vecs[j] is not None]
    print(f"[seed] 有效向量 {len(ok_idx)}/{len(rows)}")
    for i in range(0, len(ok_idx), 32):
        batch = [{"doc_id": rows[j][0],
                  "content": texts[j][:4000],
                  "metadata": {"kpName": rows[j][1], "courseName": rows[j][3]},
                  "embedding": vecs[j]} for j in ok_idx[i:i + 32]]
        r = http_json(f"{MILVUS}/v2/vectordb/entities/insert",
                      {"collectionName": COLLECTION, "data": batch}, timeout=120)
        assert r.get("code") == 0, f"insert failed: {r}"
    print(f"[seed] inserted {len(ok_idx)} vectors")

    # 重建在线标注集: 每门课抽 1 kp, query=课程名+知识点名, gold=[kp code]
    valid = {rows[j][0] for j in ok_idx}
    seen, items = set(), []
    for code, name, desc, cname in rows:
        if code not in valid or cname in seen:
            continue
        seen.add(cname)
        q = f"{cname} {name} 练习题"
        items.append({"query": q, "relevant_ids": [code]})
    with open("/tmp/rag_online.jsonl", "w", encoding="utf-8") as f:
        for it in items:
            f.write(json.dumps(it, ensure_ascii=False) + "\n")
    print(f"[seed] 标注集 {len(items)} 条 -> /tmp/rag_online.jsonl")


if __name__ == "__main__":
    main()
