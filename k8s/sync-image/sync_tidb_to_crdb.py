#!/usr/bin/env python3
"""TiDB → CRDB 数据同步(MySQL 读, PG 写, UPSERT 主键幂等, 自动 DDL 镜像)。

数据层默认 TiDB; CRDB 为随时可切换镜像。分级:
  默认小表集(每小时 CronJob): courses / users / roles / tenants /
      credit_transfer_rules / assignments / live_sessions / learning_paths / learning_path_steps
  --full 大表全量(切换前手动, 数千万行分批): chapters / knowledge_points /
      resources / credit_transfer_records / discussions
  --reverse: 反向(CRDB→TiDB), 切回前手动跑一次, 不做常驻双向(避免回环写)

幂等: 单列主键 ON CONFLICT DO UPDATE; 目标库表不存在时按 information_schema
类型映射自动 CREATE(可 --dry-run 先看 DDL)。

用法:
  python3 scripts/sync_tidb_to_crdb.py --dry-run        # 只打印将建的 DDL
  python3 scripts/sync_tidb_to_crdb.py                  # 小表集同步
  python3 scripts/sync_tidb_to_crdb.py --full           # 全量(含大表)
  python3 scripts/sync_tidb_to_crdb.py --tables course_db.courses
环境: TIDB_HOST/PORT/USER/PASSWORD, CRDB_HOST/PORT/USER/PASSWORD
"""
from __future__ import annotations

import argparse
import os
import time

import pymysql

SMALL_TABLES = [
    ("course_db", "courses"),
    ("iam_db", "users"), ("iam_db", "roles"),
    ("tenant_db", "tenants"), ("tenant_db", "credit_transfer_rules"),
    ("teaching_db", "assignments"), ("teaching_db", "live_sessions"),
    ("teaching_db", "learning_paths"), ("teaching_db", "learning_path_steps"),
]
BIG_TABLES = [
    ("course_db", "chapters"), ("course_db", "knowledge_points"), ("course_db", "resources"),
    ("tenant_db", "credit_transfer_records"), ("teaching_db", "discussions"),
]
BATCH = 5000

# MySQL → PG(CRDB) 类型映射
TYPE_MAP = {
    "bigint": "BIGINT", "int": "INT", "integer": "INT", "mediumint": "INT",
    "smallint": "SMALLINT", "tinyint": "SMALLINT",
    "varchar": "VARCHAR", "char": "VARCHAR", "text": "TEXT", "longtext": "TEXT",
    "mediumtext": "TEXT", "tinytext": "TEXT",
    "datetime": "TIMESTAMP", "timestamp": "TIMESTAMPTZ", "date": "DATE",
    "time": "TIME", "year": "INT",
    "decimal": "DECIMAL", "numeric": "DECIMAL",
    "double": "DOUBLE PRECISION", "float": "DOUBLE PRECISION", "real": "DOUBLE PRECISION",
    "json": "JSONB", "bit": "BOOLEAN",
}


def tidb_connect():
    return pymysql.connect(
        host=os.getenv("TIDB_HOST", "10.167.2.175"),
        port=int(os.getenv("TIDB_PORT", "30210")),
        user=os.getenv("TIDB_USER", "root"),
        password=os.getenv("TIDB_PASSWORD", ""),
        charset="utf8mb4", connect_timeout=10,
        cursorclass=pymysql.cursors.SSCursor)


def crdb_connect(db: str = "defaultdb"):
    import psycopg2
    return psycopg2.connect(
        host=os.getenv("CRDB_HOST", "10.167.2.175"),
        port=int(os.getenv("CRDB_PORT", "32257")),
        user=os.getenv("CRDB_USER", "root"),
        password=os.getenv("CRDB_PASSWORD", ""),
        dbname=db, sslmode=os.getenv("CRDB_SSLMODE", "disable"), connect_timeout=10)


# ---------- 元数据(TiDB 侧) ----------
def table_meta(src, db: str, table: str):
    """返回 (columns[(name, mapped_type, is_pk)], pk)"""
    cur = src.cursor()
    cur.execute(
        "SELECT column_name, data_type, character_maximum_length, column_key "
        "FROM information_schema.columns WHERE table_schema=%s AND table_name=%s "
        "ORDER BY ordinal_position", (db, table))
    cols, pk = [], None
    for name, dtype, charlen, key in cur.fetchall():
        pg = TYPE_MAP.get(dtype.lower(), "TEXT")
        if pg == "VARCHAR" and charlen:
            pg = f"VARCHAR({int(charlen)})"
        elif pg == "VARCHAR":
            pg = "TEXT"
        if key == "PRI":
            pk = pk or name
        cols.append((name, pg, key == "PRI"))
    if pk is None:
        raise SystemExit(f"[sync] {db}.{table} 无主键, 跳过(不支持幂等)")
    return cols, pk


def ensure_schema(dst_admin, db: str, table: str, cols, dry: bool):
    ddl_db = f"CREATE DATABASE IF NOT EXISTS {db}"
    col_defs = ", ".join(f'"{n}" {t}' + (" PRIMARY KEY" if p else "") for n, t, p in cols)
    ddl_tbl = f'CREATE TABLE IF NOT EXISTS {db}.{table} ({col_defs})'
    if dry:
        print(ddl_db + ";")
        print(ddl_tbl + ";")
        return
    dst_admin.commit()   # 同上: 切 autocommit 前清事务
    dst_admin.autocommit = True
    dst_admin.cursor().execute(ddl_db)
    dst_admin.cursor().execute(ddl_tbl)
    dst_admin.autocommit = False


# ---------- 水位增量(小表集每小时 CronJob 的正确姿势: 首次全量, 之后只传变更) ----------
WM_DDL = """CREATE TABLE IF NOT EXISTS _sync_meta (
    db_tbl VARCHAR(128) PRIMARY KEY, last_ts TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ DEFAULT now())"""


def ensure_wm(dst_admin):
    dst_admin.commit()   # 清未决事务, 否则 psycopg2 拒绝切 autocommit
    dst_admin.autocommit = True
    dst_admin.cursor().execute(WM_DDL)
    dst_admin.autocommit = False


def get_wm(dst, db, table) -> str | None:
    cur = dst.cursor()
    cur.execute("SELECT last_ts FROM _sync_meta WHERE db_tbl=%s", (f"{db}.{table}",))
    row = cur.fetchone()
    return row[0].isoformat() if row else None


def set_wm(dst, db, table, ts: str):
    cur = dst.cursor()
    cur.execute("""INSERT INTO _sync_meta (db_tbl, last_ts) VALUES (%s, %s)
                   ON CONFLICT (db_tbl) DO UPDATE SET last_ts=EXCLUDED.last_ts, updated_at=now()""",
                (f"{db}.{table}", ts))
    dst.commit()


# ---------- 同步 ----------
def pg_column_types(dst, db: str, table: str) -> dict[str, str]:
    """目标(CRDB)表实际列类型: {col: format_type}。目标表由 JPA 预建时类型
    与源 MySQL 不同(如 JSON→uuid[]), 值必须按目标类型适配。
    注: CRDB 的 information_schema.columns 经 PG 协议查询不稳定(返回空),
    改用 pg_catalog(format_type 给出 'uuid[]'/'jsonb' 等完整类型)。"""
    cur = dst.cursor()
    try:
        cur.execute("""SELECT a.attname, format_type(a.atttypid, a.atttypmod)
                       FROM pg_attribute a JOIN pg_class c ON a.attrelid=c.oid
                       JOIN pg_namespace n ON c.relnamespace=n.oid
                       WHERE n.nspname='public' AND c.relname=%s
                         AND a.attnum>0 AND NOT a.attisdropped""", (table,))
        return {r[0]: r[1] for r in cur.fetchall()}
    except Exception:
        return {}


def adapt_row(row: tuple, columns: list[str], pg_types: dict[str, str]) -> tuple:
    """源值 → 目标类型适配: 数组列(JSON 串→list)、jsonb(→Json)、bool(bytes/int→bool)。"""
    import json as _json
    from psycopg2.extras import Json
    out = []
    for v, c in zip(row, columns):
        t = pg_types.get(c, "")
        if t.endswith("[]") and isinstance(v, (str, bytes)):       # 目标数组列
            v = _json.loads(v) if v else []
        elif t == "jsonb" and isinstance(v, (str, bytes)):
            v = Json(_json.loads(v) if v else None)
        elif t == "boolean" and isinstance(v, (bytes, int)):
            v = bool(int.from_bytes(v, "little")) if isinstance(v, bytes) else bool(v)
        elif v == b"" and t not in ("bytea",):
            v = None
        out.append(v)
    return tuple(out)


def sync_table(src, dst, db: str, table: str, incremental: bool = True,
               wm_conn=None) -> tuple[int, float]:
    """incremental=True: 按 updated_at 水位增量(无水位首次自动全量)。
    水位表 _sync_meta 在 defaultdb(admin 连接), 不随目标库走。"""
    t0 = time.time()
    cols, pk = table_meta(src, db, table)
    names = [c[0] for c in cols]
    pg_types = pg_column_types(dst, db, table)
    if pg_types:
        common = [c for c in names if c in pg_types]
        if len(common) < len(names):
            print(f"  [warn] {db}.{table}: 目标缺列, 只同步交集 {len(common)}/{len(names)}")
        names = common
    sel = ",".join(f"`{c}`" for c in names)
    col_list = ",".join(f'"{c}"' for c in names)
    updates = ",".join(f'"{c}"=EXCLUDED."{c}"' for c in names if c != pk) or f'"{pk}"=EXCLUDED."{pk}"'
    upsert = (f'INSERT INTO {db}.{table} ({col_list}) VALUES ({",".join(["%s"]*len(names))}) '
              f'ON CONFLICT ("{pk}") DO UPDATE SET {updates}')

    wm = get_wm(wm_conn or dst, db, table) if incremental else None
    has_ts = "updated_at" in names
    ts_idx = names.index("updated_at") if has_ts else None

    scur = src.cursor()
    dcur = dst.cursor()
    n, last, max_ts = 0, None, None
    while True:
        if wm and has_ts:
            if last is None:
                scur.execute(f"SELECT {sel} FROM {db}.`{table}` WHERE updated_at > %s "
                             f"ORDER BY `{pk}` LIMIT {BATCH}", (wm,))
            else:
                scur.execute(f"SELECT {sel} FROM {db}.`{table}` WHERE updated_at > %s AND `{pk}` > %s "
                             f"ORDER BY `{pk}` LIMIT {BATCH}", (wm, last))
        else:
            if last is None:
                scur.execute(f"SELECT {sel} FROM {db}.`{table}` ORDER BY `{pk}` LIMIT {BATCH}")
            else:
                scur.execute(f"SELECT {sel} FROM {db}.`{table}` WHERE `{pk}` > %s "
                             f"ORDER BY `{pk}` LIMIT {BATCH}", (last,))
        rows = scur.fetchall()
        if not rows:
            break
        for r in rows:
            dcur.execute(upsert, adapt_row(r, names, pg_types))
        dst.commit()
        n += len(rows)
        last = rows[-1][names.index(pk)]
        if ts_idx is not None:
            import datetime as _dt
            for r in rows:
                v = r[ts_idx]
                if isinstance(v, _dt.datetime):
                    max_ts = v if max_ts is None or v > max_ts else max_ts
        if n % (BATCH * 10) == 0:
            print(f"  {db}.{table} ... {n}", flush=True)
    if incremental and ts_idx is not None:
        # 水位=本轮见过最大 updated_at; 无行时保持旧水位
        if max_ts is not None:
            set_wm(wm_conn or dst, db, table, max_ts.isoformat(sep=" "))
    print(f"  {db}.{table} done: {n} rows ({'incr' if wm else 'full'})", flush=True)
    return n, time.time() - t0


def prune_table(src, dst, db: str, table: str) -> int:
    """对账清理: 删除目标中源已不存在的行(pk diff)。小表集适用。"""
    cols, pk = table_meta(src, db, table)
    scur, dcur = src.cursor(), dst.cursor()
    scur.execute(f"SELECT `{pk}` FROM {db}.`{table}`")
    src_ids = {r[0] for r in scur.fetchall()}
    dcur.execute(f'SELECT "{pk}" FROM {db}.{table}')
    dst_ids = {r[0] for r in dcur.fetchall()}
    gone = dst_ids - src_ids
    for g in gone:
        dcur.execute(f'DELETE FROM {db}.{table} WHERE "{pk}"=%s', (g,))
    dst.commit()
    return len(gone)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--tables", help="db.table 逗号分隔, 覆盖默认集")
    ap.add_argument("--full", action="store_true", help="含大表全量")
    ap.add_argument("--prune", action="store_true", help="对账删除目标多余行(小表集)")
    ap.add_argument("--no-incremental", action="store_true", help="忽略水位全量(重扫)")
    ap.add_argument("--dry-run", action="store_true", help="只打印 DDL 不执行")
    args = ap.parse_args()

    targets = ([tuple(t.split(".", 1)) for t in args.tables.split(",")] if args.tables
               else SMALL_TABLES + (BIG_TABLES if args.full else []))

    src = tidb_connect()
    admin = crdb_connect()          # defaultdb: 建 DATABASE 用
    if not args.dry_run:
        ensure_wm(admin)
    total = 0
    for db, table in targets:
        cols, pk = table_meta(src, db, table)
        ensure_schema(admin, db, table, cols, dry=args.dry_run)
        if args.dry_run:
            print(f"-- dry-run skip data: {db}.{table} pk={pk}")
            continue
        dst = crdb_connect(db)      # information_schema 按库隔离, 数据连接须切到目标库
        n, secs = sync_table(src, dst, db, table,
                             incremental=not args.no_incremental, wm_conn=admin)
        if args.prune:
            gone = prune_table(src, dst, db, table)
            if gone:
                print(f"[sync] {db}.{table}: prune {gone} rows", flush=True)
        dst.close()
        total += n
        print(f"[sync] {db}.{table}: {n} rows in {secs:.1f}s", flush=True)
    if not args.dry_run:
        print(f"[sync] 完成, 共 {total} rows")
    src.close()
    admin.close()


if __name__ == "__main__":
    main()
