"""修订计数器增量同步(P3, OpenMAIC revision-counter 语义)

前端一致性方案: 资源/会话/画像等实体带单调递增 revision,
客户端携带上次 revision, 服务端只返回变更项(增量拉取), 替代全量刷新。
内存注册表 + CRDB 持久化(可选), 契约与 OpenMAIC 一致:
  GET  /sync/updates?since=<rev>   → {latestRevision, changed:[{entity, revision, payload}]}
  PUT /sync/bump (内部)           → 实体变更时 bump revision
"""
from __future__ import annotations

import os
import threading
import time

_lock = threading.Lock()
_entities: dict[str, dict] = {}     # key: "{kind}:{id}" -> {"revision": n, "payload": ...}
_global_rev = 0
MAX_ENTITIES = 2000

_pg = None


def _dsn() -> str:
    return os.getenv("DURABLE_SESSION_DSN", "")


def _db():
    global _pg
    if _pg is not None:
        return _pg
    dsn = _dsn()
    if not dsn:
        return None
    try:
        import psycopg2
        _pg = psycopg2.connect(dsn, connect_timeout=5)
        _pg.autocommit = True
        with _pg.cursor() as c:
            c.execute("""CREATE TABLE IF NOT EXISTS entity_revisions (
                           entity_key VARCHAR(128) PRIMARY KEY,
                           revision   BIGINT NOT NULL,
                           payload    JSONB NOT NULL,
                           updated_at TIMESTAMPTZ NOT NULL DEFAULT now())""")
        return _pg
    except Exception:
        _pg = None
        return None


def bump(kind: str, entity_id: str, payload: dict) -> int:
    """实体变更 → revision+1(全局单调)"""
    global _global_rev
    with _lock:
        _global_rev += 1
        rev = _global_rev
        _entities[f"{kind}:{entity_id}"] = {"revision": rev, "payload": payload}
        if len(_entities) > MAX_ENTITIES:
            for k in sorted(_entities, key=lambda k: _entities[k]["revision"])[:200]:
                _entities.pop(k, None)
    db = _db()
    if db:
        import json
        with db.cursor() as c:
            c.execute("""INSERT INTO entity_revisions(entity_key, revision, payload)
                         VALUES (%s,%s,%s)
                         ON CONFLICT (entity_key) DO UPDATE SET
                           revision=EXCLUDED.revision, payload=EXCLUDED.payload,
                           updated_at=now()""",
                      (f"{kind}:{entity_id}", rev,
                       json.dumps(payload, ensure_ascii=False)))
    return rev


def updates(since: int, kinds: list[str] | None = None) -> dict:
    """增量: 返回 revision > since 的变更(可按 kind 过滤)"""
    db = _db()
    changed = []
    if db:
        with db.cursor() as c:
            if kinds:
                c.execute("""SELECT entity_key, revision, payload FROM entity_revisions
                             WHERE revision > %s AND split_part(entity_key,':',1) = ANY(%s)
                             ORDER BY revision""", (since, kinds))
            else:
                c.execute("""SELECT entity_key, revision, payload FROM entity_revisions
                             WHERE revision > %s ORDER BY revision""", (since,))
            for k, r, p in c.fetchall():
                changed.append({"entity": k, "revision": r, "payload": p})
    else:
        with _lock:
            for k, v in _entities.items():
                if v["revision"] > since and (not kinds or k.split(":", 1)[0] in kinds):
                    changed.append({"entity": k, "revision": v["revision"],
                                    "payload": v["payload"]})
            changed.sort(key=lambda x: x["revision"])
    with _lock:
        latest = _global_rev
    if db:
        with db.cursor() as c:
            c.execute("SELECT COALESCE(MAX(revision),0) FROM entity_revisions")
            latest = max(latest, c.fetchone()[0])
    return {"latestRevision": latest, "changed": changed, "count": len(changed)}


def latest() -> int:
    with _lock:
        return _global_rev
