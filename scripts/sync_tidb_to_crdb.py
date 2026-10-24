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
    ac = dst_admin.autocommit
    dst_admin.autocommit = True
    dst_admin.cursor().execute(ddl_db)
    dst_admin.cursor().execute(ddl_tbl)
    dst_admin.autocommit = ac


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


def sync_table(src, dst, db: str, table: str) -> tuple[int, float]:
    t0 = time.time()
    cols, pk = table_meta(src, db, table)
    names = [c[0] for c in cols]
    # 目标表实际列(JPA 预建时列集/类型可能异于源); 只同步交集, 按目标类型适配
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

    scur = src.cursor()
    n = 0
    last = None
    while True:
        if last is None:
            scur.execute(f"SELECT {sel} FROM {db}.`{table}` ORDER BY `{pk}` LIMIT {BATCH}")
        else:
            scur.execute(f"SELECT {sel} FROM {db}.`{table}` WHERE `{pk}` > %s "
                         f"ORDER BY `{pk}` LIMIT {BATCH}", (last,))
        rows = scur.fetchall()
        if not rows:
            break
        dcur = dst.cursor()
        for r in rows:
            dcur.execute(upsert, adapt_row(r, names, pg_types))
        dst.commit()
        n += len(rows)
        last = rows[-1][names.index(pk)]
        print(f"  {db}.{table} ... {n}", flush=True)
    return n, time.time() - t0


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--tables", help="db.table 逗号分隔, 覆盖默认集")
    ap.add_argument("--full", action="store_true", help="含大表全量")
    ap.add_argument("--dry-run", action="store_true", help="只打印 DDL 不执行")
    args = ap.parse_args()

    targets = ([tuple(t.split(".", 1)) for t in args.tables.split(",")] if args.tables
               else SMALL_TABLES + (BIG_TABLES if args.full else []))

    src = tidb_connect()
    admin = crdb_connect()          # defaultdb: 建 DATABASE 用
    total = 0
    for db, table in targets:
        cols, pk = table_meta(src, db, table)
        ensure_schema(admin, db, table, cols, dry=args.dry_run)
        if args.dry_run:
            print(f"-- dry-run skip data: {db}.{table} pk={pk}")
            continue
        dst = crdb_connect(db)      # information_schema 按库隔离, 数据连接须切到目标库
        n, secs = sync_table(src, dst, db, table)
        dst.close()
        total += n
        print(f"[sync] {db}.{table}: {n} rows in {secs:.1f}s", flush=True)
    if not args.dry_run:
        print(f"[sync] 完成, 共 {total} rows")
    src.close()
    admin.close()


if __name__ == "__main__":
    main()
