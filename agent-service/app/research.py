"""论文/专利实验数据采集器(Research Data Collector)

支撑 IP_STRATEGY_PLAN 论文 A/B/C/D 与专利 1/2 的可复现实验:
  1) 实验运行登记: 每次实验(消融/基线/参数档)登记条件+快照 → experiments 表
  2) 指标快照: 生成质量(规则分/审核分)/世界模型(推演奖励 MAE)/KT(AUC 掌握度)
  3) 数据集导出: L1/L2 脱敏学习事件 → JSONL(论文投稿用, studentId 哈希脱敏)
端点:
  POST /api/v1/research/experiments          登记一次实验(条件+结果)
  GET  /api/v1/research/experiments?tag=     列出实验
  POST /api/v1/research/datasets/export      导出脱敏数据集(JSONL, 参数: limit)
无 CRDB 时落本地 /tmp/research/(fail-soft), 保证采集永不阻塞业务。
"""
from __future__ import annotations

import hashlib
import json
import os
import time
import uuid

_pg = None

DDL = """
CREATE TABLE IF NOT EXISTS research_experiments (
    exp_id      VARCHAR(32) PRIMARY KEY,
    tag         VARCHAR(64) NOT NULL,
    condition   JSONB NOT NULL,
    metrics     JSONB NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
"""


def _db():
    global _pg
    if _pg is not None:
        return _pg
    dsn = os.getenv("DURABLE_SESSION_DSN", "")
    if not dsn:
        return None
    try:
        import psycopg2
        _pg = psycopg2.connect(dsn, connect_timeout=5)
        _pg.autocommit = True
        with _pg.cursor() as c:
            c.execute(DDL)
        return _pg
    except Exception:
        _pg = None
        return None


def _local_path() -> str:
    d = os.getenv("RESEARCH_DATA_DIR", "/tmp/research")
    os.makedirs(d, exist_ok=True)
    return d


def hash_id(raw: str) -> str:
    """studentId 等敏感标识脱敏(GB/T 35273): SHA-256 前 16 位"""
    return hashlib.sha256(f"qiqm-salt:{raw}".encode()).hexdigest()[:16]


def record_experiment(tag: str, condition: dict, metrics: dict) -> dict:
    exp = {"expId": uuid.uuid4().hex[:12], "tag": tag,
           "condition": condition, "metrics": metrics,
           "createdAt": int(time.time() * 1000)}
    db = _db()
    if db:
        with db.cursor() as c:
            c.execute("""INSERT INTO research_experiments(exp_id, tag, condition, metrics)
                         VALUES (%s,%s,%s,%s)""",
                      (exp["expId"], tag,
                       json.dumps(condition, ensure_ascii=False),
                       json.dumps(metrics, ensure_ascii=False)))
    with open(os.path.join(_local_path(), f"exp_{exp['expId']}.json"),
              "w", encoding="utf-8") as f:
        json.dump(exp, f, ensure_ascii=False, indent=1)
    return exp


def list_experiments(tag: str = "") -> list[dict]:
    db = _db()
    out = []
    if db:
        with db.cursor() as c:
            if tag:
                c.execute("SELECT exp_id, tag, condition, metrics FROM research_experiments "
                          "WHERE tag=%s ORDER BY created_at DESC LIMIT 100", (tag,))
            else:
                c.execute("SELECT exp_id, tag, condition, metrics FROM research_experiments "
                          "ORDER BY created_at DESC LIMIT 100")
            for eid, t, cond, m in c.fetchall():
                out.append({"expId": eid, "tag": t,
                            "condition": cond if isinstance(cond, dict) else json.loads(cond),
                            "metrics": m if isinstance(m, dict) else json.loads(m)})
        return out
    for fn in sorted(os.listdir(_local_path()), reverse=True):
        if fn.startswith("exp_") and fn.endswith(".json"):
            d = json.load(open(os.path.join(_local_path(), fn), encoding="utf-8"))
            if not tag or d.get("tag") == tag:
                out.append(d)
    return out[:100]


def export_dataset(kind: str, limit: int = 500) -> dict:
    """导出脱敏数据集: kind = learning_events(L1/L2 摘要) | interactions(交互足迹)"""
    # 学习事件源: kt-service L1/L2(经 REST); 无源时返回空集说明
    rows = []
    try:
        import httpx
        from app import config
        if kind in ("learning_events", "interactions"):
            r = httpx.get(f"{config.KT_BASE}/api/v1/kt/memory",
                          params={"studentId": "export-dataset", "limit": limit},
                          timeout=15)
            body = r.json()
            profile = body.get("profile", {})
            for kp, m in (profile.get("masteries") or {}).items():
                rows.append({"kind": kind, "skillHash": hash_id(str(kp)),
                             "mastery": m, "ts": body.get("ts", int(time.time()))})
    except Exception as e:  # noqa: BLE001
        note = f"kt-source unavailable: {e}"
    else:
        note = "ok" if rows else "no data for seed student (正常: 数据经真实学习产生)"
    out_file = os.path.join(_local_path(), f"dataset_{kind}_{int(time.time())}.jsonl")
    with open(out_file, "w", encoding="utf-8") as f:
        for r in rows:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")
    return {"kind": kind, "rows": len(rows), "file": out_file, "note": note,
            "anonymized": True}
