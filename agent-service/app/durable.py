"""Durable Session(持久会话) — OpenMAIC Durable Session 语义

解决: agent 多副本/重启后内存会话(session/interaction/world)丢失问题。
机制(与 OpenMAIC 一致):
  租约(lease): 会话归属某副本, 到期自动释放(默认 30s, 心跳续约)
  心跳(heartbeat): 归属副本定期续租; 副本死亡 → 租约过期 → 其他副本可接管
  续跑(resume): 会话状态经数据层持久化, 接管副本从 checkpoint 恢复
  追加指令: 运行中会话可注入新指令(排队到 inbox, 下轮消费)
存储: TiDB(MySQL 协议) durable_sessions 表, 带版本号乐观锁; 无 DB 时降级内存
(fail-soft, 与既有行为兼容)。
2026-10-24 数据层统一 TiDB: psycopg2/CRDB → pymysql/TiDB(全平台单一默认数据层)。
"""
from __future__ import annotations

import os
import time
import uuid

LEASE_SECONDS = 30
_db = None

DDL = """
CREATE TABLE IF NOT EXISTS durable_sessions (
    session_id   VARCHAR(32) PRIMARY KEY,
    kind         VARCHAR(32) NOT NULL,
    owner_pod    VARCHAR(128) NOT NULL,
    lease_until  DATETIME(6) NOT NULL,
    version      INT NOT NULL DEFAULT 1,
    state        JSON NOT NULL,
    inbox        JSON NOT NULL,
    created_at   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
)
"""


def _conn():
    global _db
    if _db is not None:
        return _db
    host = os.getenv("DURABLE_DB_HOST", "")
    if not host:
        return None
    try:
        import pymysql
        _db = pymysql.connect(
            host=host, port=int(os.getenv("DURABLE_DB_PORT", "4000")),
            user=os.getenv("DURABLE_DB_USER", "root"),
            password=os.getenv("DURABLE_DB_PASSWORD", ""),
            database=os.getenv("DURABLE_DB_NAME", "agent_db"),
            charset="utf8mb4", autocommit=True, connect_timeout=5)
        with _db.cursor() as c:
            c.execute(DDL)
        return _db
    except Exception:
        _db = None
        return None


def _pod() -> str:
    return os.getenv("HOSTNAME", "pod-unknown")


class LeaseLost(Exception):
    """租约已失(被其他副本接管或过期)"""


# 内存降级存储(同进程可 resume; 跨副本不可用 → 配 DURABLE_DB_* 启用 TiDB)
_MEM: dict[str, dict] = {}


class DurableSession:
    """一个可持久化会话: interaction/world/生成共用同一租约语义"""

    def __init__(self, kind: str, state: dict, session_id: str | None = None):
        self.kind = kind
        self.session_id = session_id or uuid.uuid4().hex[:16]
        self.state = state
        self.inbox: list[dict] = []
        self._db = _conn()
        self._loaded = False

    # ---- 持久化 ----
    def save(self) -> int:
        """写 TiDB(乐观锁 version++); 无 DB 时内存模式"""
        if not self._db:
            import copy
            _MEM[f"{self.kind}:{self.session_id}"] = {
                "state": copy.deepcopy(self.state), "inbox": copy.deepcopy(self.inbox)}
            return 0
        import json
        sql = """INSERT INTO durable_sessions
                   (session_id, kind, owner_pod, lease_until, version, state, inbox)
                   VALUES (%s,%s,%s,NOW(6)+INTERVAL %s SECOND,1,%s,%s)
                   ON DUPLICATE KEY UPDATE
                     owner_pod=VALUES(owner_pod),
                     lease_until=NOW(6)+INTERVAL %s SECOND,
                     version=durable_sessions.version+1,
                     state=VALUES(state),
                     inbox=VALUES(inbox),
                     updated_at=CURRENT_TIMESTAMP(6)"""
        with self._db.cursor() as c:
            c.execute(sql,
                      (self.session_id, self.kind, _pod(), LEASE_SECONDS,
                       json.dumps(self.state, ensure_ascii=False),
                       json.dumps(self.inbox, ensure_ascii=False), LEASE_SECONDS))
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
                # 仅当租约已过期才允许接管(乐观: 影响行数 0 = 未过期或不存在)
                c.execute(
                    """UPDATE durable_sessions SET owner_pod=%s,
                       lease_until=NOW(6)+INTERVAL %s SECOND, version=version+1
                       WHERE session_id=%s AND kind=%s AND lease_until < NOW(6)""",
                    (_pod(), LEASE_SECONDS, session_id, kind))
                if c.rowcount == 0:
                    c.execute("SELECT 1 FROM durable_sessions WHERE session_id=%s",
                              (session_id,))
                    row = c.fetchone()
                    raise LeaseLost("lease active on another replica" if row
                                    else "session not found")
            else:
                c.execute("""SELECT lease_until < NOW(6) FROM durable_sessions
                             WHERE session_id=%s AND kind=%s""",
                          (session_id, kind))
                row = c.fetchone()
                if not row:
                    raise LeaseLost("session not found")
                if row[0]:
                    return cls.resume(kind, session_id, takeover=True)
            c.execute("""SELECT state, inbox FROM durable_sessions
                         WHERE session_id=%s""", (session_id,))
            data = c.fetchone()
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
                """UPDATE durable_sessions SET lease_until=NOW(6)+INTERVAL %s SECOND,
                   owner_pod=%s, updated_at=CURRENT_TIMESTAMP(6)
                   WHERE session_id=%s AND owner_pod=%s""",
                (LEASE_SECONDS, _pod(), self.session_id, _pod()))
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
                c.execute("UPDATE durable_sessions SET inbox='[]' WHERE session_id=%s",
                          (self.session_id,))
        return out
