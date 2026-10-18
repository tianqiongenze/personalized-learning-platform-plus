"""Durable Session(持久会话) — P3 构建, OpenMAIC Durable Session 语义

解决: agent 多副本/重启后内存会话(session/interaction/world)丢失问题。
机制(与 OpenMAIC 一致):
  租约(lease): 会话归属某副本, 到期自动释放(默认 30s, 心跳续约)
  心跳(heartbeat): 归属副本定期续租; 副本死亡 → 租约过期 → 其他副本可接管
  续跑(resume): 会话状态经 CRDB 持久化, 接管副本从 checkpoint 恢复
  追加指令: 运行中会话可注入新指令(排队到 inbox, 下轮消费)
存储: CRDB(PG wire) durable_sessions 表, 带版本号乐观锁; 无 DB 时降级内存
(fail-soft, 与既有行为兼容)。
"""
from __future__ import annotations

import os
import time
import uuid

LEASE_SECONDS = 30
_pg_pool = None

DDL = """
CREATE TABLE IF NOT EXISTS durable_sessions (
    session_id   VARCHAR(32) PRIMARY KEY,
    kind         VARCHAR(32) NOT NULL,        -- interaction | generation | a2a
    owner_pod    VARCHAR(128) NOT NULL,
    lease_until  TIMESTAMPTZ NOT NULL,
    version      INT NOT NULL DEFAULT 1,
    state        JSONB NOT NULL,
    inbox        JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
"""


def _dsn() -> str:
    return os.getenv("DURABLE_SESSION_DSN", "")


def _pool():
    global _pg_pool
    if _pg_pool is not None:
        return _pg_pool
    dsn = _dsn()
    if not dsn:
        return None
    try:
        import psycopg2
        _pg_pool = psycopg2.connect(dsn, connect_timeout=5)
        _pg_pool.autocommit = True
        with _pg_pool.cursor() as c:
            c.execute(DDL)
        return _pg_pool
    except Exception:
        _pg_pool = None
        return None


def _pod() -> str:
    return os.getenv("HOSTNAME", "pod-unknown")


class LeaseLost(Exception):
    """租约已失(被其他副本接管或过期)"""


# 内存降级存储(同进程可 resume; 跨副本不可用 → 配 DURABLE_SESSION_DSN 启用 CRDB)
_MEM: dict[str, dict] = {}


class DurableSession:
    """一个可持久化会话: interaction/world/生成共用同一租约语义"""

    def __init__(self, kind: str, state: dict, session_id: str | None = None):
        self.kind = kind
        self.session_id = session_id or uuid.uuid4().hex[:16]
        self.state = state
        self.inbox: list[dict] = []
        self._db = _pool()
        self._loaded = False

    # ---- 持久化 ----
    def save(self) -> int:
        """写 CRDB(乐观锁 version++); 无 DB 时内存模式"""
        if not self._db:
            import copy
            _MEM[f"{self.kind}:{self.session_id}"] = {
                "state": copy.deepcopy(self.state), "inbox": copy.deepcopy(self.inbox)}
            return 0
        import json
        with self._db.cursor() as c:
            c.execute(
                """INSERT INTO durable_sessions
                   (session_id, kind, owner_pod, lease_until, version, state, inbox)
                   VALUES (%s,%s,%s,now()+interval '%s seconds',1,%s,%s)
                   ON CONFLICT (session_id) DO UPDATE SET
                     owner_pod=EXCLUDED.owner_pod,
                     lease_until=now()+interval '%s seconds',
                     version=durable_sessions.version+1,
                     state=EXCLUDED.state,
                     inbox=EXCLUDED.inbox,
                     updated_at=now()""" % (LEASE_SECONDS, LEASE_SECONDS),
                (self.session_id, self.kind, _pod(),
                 json.dumps(self.state, ensure_ascii=False),
                 json.dumps(self.inbox, ensure_ascii=False)))
            c.execute("SELECT version FROM durable_sessions WHERE session_id=%s",
                      (self.session_id,))
            return c.fetchone()[0]

    @classmethod
    def resume(cls, kind: str, session_id: str, takeover: bool = False) -> "DurableSession":
        """从 checkpoint 恢复; takeover=True 时接管过期租约(副本崩溃恢复)"""
        s = cls(kind, {}, session_id)
        if not s._db:
            mem = _MEM.get(f"{kind}:{session_id}")
            if not mem:
                raise LeaseLost("session not found")
            import copy
            s.state = copy.deepcopy(mem["state"])
            s.inbox = copy.deepcopy(mem["inbox"])
            s._loaded = True
            return s
        with s._db.cursor() as c:
            if takeover:
                # 仅当租约已过期才允许接管(乐观: UPDATE 影响 0 行 = 未过期或不存在)
                c.execute(
                    """UPDATE durable_sessions SET owner_pod=%s,
                       lease_until=now()+interval '%s seconds', version=version+1
                       WHERE session_id=%s AND kind=%s AND lease_until < now()"""
                    % (_pod(), LEASE_SECONDS), (session_id, kind))
                if c.rowcount == 0:
                    row = c.execute("SELECT 1 FROM durable_sessions WHERE session_id=%s",
                                    (session_id,)).fetchone()
                    raise LeaseLost("lease active on another replica" if row
                                    else "session not found")
            else:
                row = c.execute("""SELECT lease_until < now() FROM durable_sessions
                                   WHERE session_id=%s AND kind=%s""",
                                (session_id, kind)).fetchone()
                if not row:
                    raise LeaseLost("session not found")
                if row[0]:
                    return cls.resume(kind, session_id, takeover=True)
            data = c.execute("""SELECT state, inbox FROM durable_sessions
                                WHERE session_id=%s""", (session_id,)).fetchone()
        import json
        s.state = data[0] if isinstance(data[0], dict) else json.loads(data[0])
        s.inbox = data[1] if isinstance(data[1], list) else json.loads(data[1])
        s._loaded = True
        return s

    def heartbeat(self) -> None:
        """续租; 租约已易主则抛 LeaseLost"""
        if not self._db:
            return
        with self._db.cursor() as c:
            c.execute(
                """UPDATE durable_sessions SET lease_until=now()+interval '%s seconds',
                   owner_pod=%s, updated_at=now()
                   WHERE session_id=%s AND owner_pod=%s""" % (LEASE_SECONDS, _pod()),
                (self.session_id, _pod()))
            if c.rowcount == 0:
                raise LeaseLost("lease held by another replica")

    def push_instruction(self, instruction: dict) -> None:
        """运行中追加指令(排队, 下轮消费)"""
        self.inbox.append({**instruction, "queuedAt": int(time.time() * 1000)})
        self.save()

    def drain_instructions(self) -> list[dict]:
        out, self.inbox = self.inbox, []
        if self._db:
            with self._db.cursor() as c:
                c.execute("UPDATE durable_sessions SET inbox='[]'::jsonb WHERE session_id=%s",
                          (self.session_id,))
        return out
