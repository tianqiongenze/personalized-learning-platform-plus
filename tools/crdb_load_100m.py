# -*- coding: utf-8 -*-
"""CRDB 1亿行直插(集群版, python3.6 兼容): psycopg2 copy_expert + Zipf 分布。
与 tools/datagen_100m.py 同构(同分布参数/同 stable_uuid), 保证与 warmup 热键可互相命中。

用法(集群 master 上):
  python3 crdb_load_100m.py <rows> [dsn]
  默认: 100000000  postgresql://root@127.0.0.1:30257/scale_db?sslmode=disable
"""
import hashlib
import io
import json
import random
import sys
import time

import psycopg2

TENANT_COUNT = 20
COURSE_PER_TENANT = 500
KP_PER_COURSE = 40
STUDENT_PER_TENANT = 5000
ZIPF_THETA = 1.1
BATCH_ROWS = 50000
INTERACTION_TYPES = ["VIEW", "ATTEMPT", "SUBMIT", "COMPLETE", "DWELL"]
TYPE_WEIGHTS = [0.45, 0.25, 0.12, 0.10, 0.08]
BASE_TS = 1760000000

HEADER = "id,tenant_id,user_id,course_id,resource_id,kp_id,interaction_type,payload_json,created_at"


def zipf_sampler(n, theta):
    weights = [1.0 / ((i + 1) ** theta) for i in range(n)]
    total = sum(weights)
    cum, acc = [], 0.0
    for w in weights:
        acc += w / total
        cum.append(acc)
    cum[-1] = 1.0
    import bisect
    return lambda: bisect.bisect_left(cum, random.random())


def stable_uuid(*parts):
    h = hashlib.md5("|".join(str(p) for p in parts).encode()).hexdigest()
    return "%s-%s-%s-%s-%s" % (h[0:8], h[8:12], h[12:16], h[16:20], h[20:32])


def choices(rng, seq, weights):
    x = rng.random()
    acc = 0.0
    total = sum(weights)
    for v, w in zip(seq, weights):
        acc += w / total
        if x <= acc:
            return v
    return seq[-1]


def gen_batch(z_tenant, z_course, z_student, rows, batch_no):
    out = []
    for i in range(rows):
        g = random.Random("%d:%d" % (batch_no, i))
        t = z_tenant()
        c = z_course()
        s = z_student()
        kp = g.randrange(KP_PER_COURSE)
        it = choices(g, INTERACTION_TYPES, TYPE_WEIGHTS)
        ts = BASE_TS + batch_no * BATCH_ROWS + i
        payload = json.dumps({
            "dwell_ms": g.randint(500, 900000) if it == "DWELL" else g.randint(100, 60000),
            "correct": (g.random() < 0.62) if it in ("ATTEMPT", "SUBMIT") else None,
            "score": round(g.random() * 100, 1) if it == "SUBMIT" else None,
            "device": g.choice(["pc", "h5", "mp-weixin", "app"]),
        }, separators=(",", ":"))
        tenant = "tenant-%02d" % (t + 1)
        out.append((stable_uuid("li", batch_no, i), tenant,
                    stable_uuid("student", tenant, s),
                    stable_uuid("course", tenant, c),
                    stable_uuid("resource", tenant, c, g.randrange(20)),
                    stable_uuid("kp", tenant, c, kp),
                    it, payload, ts))
    return out


def rows_to_csv(data):
    import csv as _csv
    buf = io.StringIO()
    w = _csv.writer(buf, quoting=_csv.QUOTE_MINIMAL)
    w.writerow(HEADER.split(","))
    for row in data:
        w.writerow(row)
    buf.seek(0)
    return buf


def main():
    rows_target = int(sys.argv[1]) if len(sys.argv) > 1 else 100000000
    dsn = sys.argv[2] if len(sys.argv) > 2 else "postgresql://root@127.0.0.1:30257/scale_db?sslmode=disable"
    shard = int(sys.argv[3]) if len(sys.argv) > 3 else 0
    shards = int(sys.argv[4]) if len(sys.argv) > 4 else 1
    conn = psycopg2.connect(dsn)
    conn.autocommit = True
    cur = conn.cursor()
    cur.execute("CREATE DATABASE IF NOT EXISTS scale_db")
    time.sleep(1)
    cur.execute("CREATE TABLE IF NOT EXISTS scale_db.learning_interactions ("
                "id UUID PRIMARY KEY, tenant_id STRING, user_id UUID, course_id UUID, "
                "resource_id UUID, kp_id UUID, interaction_type STRING, "
                "payload_json JSONB, created_at INT8)")
    cur.execute("SELECT count(*) FROM scale_db.learning_interactions")
    done_rows = cur.fetchone()[0]
    print("existing rows:", done_rows)
    if done_rows >= rows_target:
        print("already loaded, exit")
        return

    z_tenant = zipf_sampler(TENANT_COUNT, ZIPF_THETA * 0.5)
    z_course = zipf_sampler(COURSE_PER_TENANT, ZIPF_THETA)
    z_student = zipf_sampler(STUDENT_PER_TENANT, ZIPF_THETA)

    total_batches = (rows_target + BATCH_ROWS - 1) // BATCH_ROWS
    # 分片: 每实例只做 batch % shards == shard 的批(批粒度负载均衡, 断点安全)
    my_batches = [b for b in range(total_batches) if b % shards == shard]
    start_batch = None
    done_rows = 0  # 分片模式按完成批计数
    print("shard %d/%d batches=%d" % (shard, shards, len(my_batches)))
    t0 = time.time()
    done = 0
    skipped = [0]
    for batch in my_batches:
        if start_batch is None:
            start_batch = batch
        rows = min(BATCH_ROWS, rows_target - batch * BATCH_ROWS)
        data = gen_batch(z_tenant, z_course, z_student, rows, batch)
        buf = rows_to_csv(data)
        try:
            cur.copy_expert(
                "COPY scale_db.learning_interactions (id,tenant_id,user_id,course_id,"
                "resource_id,kp_id,interaction_type,payload_json,created_at) "
                "FROM STDIN WITH (FORMAT csv, HEADER true)", buf)
            done += rows
        except psycopg2.errors.UniqueViolation:
            conn.rollback()
            skipped[0] += 1
            print("batch %d skipped(dup, test-load overlap)" % batch)
        rate = done / max(time.time() - t0, 1e-6)
        if done % (BATCH_ROWS * 20) == 0 or batch == my_batches[-1]:
            print("batch %d/%d rows=%d rate=%.0f rows/s" % (batch + 1, total_batches, done, rate))
            sys.stdout.flush()
    print("DONE shard=%d rows=%d skipped_batches=%d elapsed=%.1fs" % (shard, done, skipped[0], time.time() - t0))


if __name__ == "__main__":
    main()
