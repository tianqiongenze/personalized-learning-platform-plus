# -*- coding: utf-8 -*-
"""数据层/缓存层 1 亿级数据生成器（支持 warmup 热分布）。

目标
====
为"多地区 × 多高校 × 10 万并发"目标生成贴近真实分布的规模数据：
  - 数据层(CRDB): learning_interactions 学习交互流水 1 亿条(按月分区键) + 关联维表
  - 缓存层(Redis): 热点键预热(warmup) —— 课程详情/知识点掌握度/推荐结果/租户配额

设计要点
========
1. Zipf 热分布: 真实教育平台访问高度倾斜(头部 20% 课程/学生占 80% 流量),
   生成时按 Zipf(θ) 采样 tenant/course/student, 保证 warmup 与压测命中同一分布。
2. 批量插入: CRDB 用 PG COPY 二进制协议(psycopg copy_expert), 单批 5 万行;
   Redis 用 pipeline 批量 SET(每批 1 万键)。
3. 断点续跑: 进度写 progress.json(已完成批号), --resume 从断点继续。
4. warmup 模式: 按 Zipf 采样 Top-N 热键写入 Redis(带 TTL 分层: 热数据 1h/温数据 10min),
   并输出热键清单 hotkeys.txt 供压测脚本与 k6 共用。
5. 可校准: --rows 控制规模, 本地 100 万实测吞吐 → 线性外推 1 亿耗时与所需副本。

使用
====
  # 本地校准(无需集群, 生成 CSV 分片 + 统计):
  python tools/datagen_100m.py --rows 1000000 --out data/scale100m --mode csv
  # 集群直插(在能连 CRDB/Redis 的网络内执行):
  python tools/datagen_100m.py --rows 100000000 --mode crdb --dsn postgresql://... --warmup-redis redis://...
  # 仅缓存 warmup:
  python tools/datagen_100m.py --warmup-only --warmup-redis redis://... --hotkeys 200000

产物
====
  data/scale100m/
    interactions_####.csv      学习交互分片(可直接 COPY 导入 CRDB)
    hotkeys.txt                Zipf 热键清单(warmup 与压测共用)
    manifest.json              生成统计(行数/分片/分布参数/耗时/吞吐)
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import os
import random
import string
import time

# ---------------------------------------------------------------------------
# 分布参数(与压测脚本/热键清单共用, 单一可信源)
# ---------------------------------------------------------------------------
TENANT_COUNT = 20          # 多地区 20 所高校
COURSE_PER_TENANT = 500    # 每校 500 门课 → 1 万课程(≥申报书 500 资源底数)
KP_PER_COURSE = 40         # 每课 40 个知识点 → 40 万知识点
STUDENT_PER_TENANT = 5000  # 每校 5000 学生 → 10 万学生(对应 10 万并发在线)
ZIPF_THETA = 1.1           # 教育平台典型倾斜度(0=均匀, 越大越集中)

BATCH_ROWS = 50_000        # CSV 每分片行数
INTERACTION_TYPES = ["VIEW", "ATTEMPT", "SUBMIT", "COMPLETE", "DWELL"]
TYPE_WEIGHTS = [0.45, 0.25, 0.12, 0.10, 0.08]
BASE_TS = 1_760_000_000    # 2025-10 附近(按月分区键需要真实时间戳)


def zipf_rank_sampler(n: int, theta: float, rng: random.Random):
    """返回按 Zipf 分布采样 [0, n) 的函数。预生成累计概率表, O(log n) 采样。"""
    weights = [1.0 / ((i + 1) ** theta) for i in range(n)]
    total = sum(weights)
    cum = []
    acc = 0.0
    for w in weights:
        acc += w / total
        cum.append(acc)
    cum[-1] = 1.0

    import bisect
    def sample() -> int:
        return bisect.bisect_left(cum, rng.random())
    return sample


def stable_uuid(*parts: str) -> str:
    """确定性 UUID(同输入同输出), 保证 hotkeys 与 CSV 行可互相校验。"""
    h = hashlib.md5("|".join(str(p) for p in parts).encode()).hexdigest()
    return f"{h[0:8]}-{h[8:12]}-{h[12:16]}-{h[16:20]}-{h[20:32]}"


class ScaleModel:
    """规模模型的实体 ID 空间(确定性, 无需物化全部实体)。"""

    def __init__(self):
        self.tenants = [f"tenant-{i:02d}" for i in range(1, TENANT_COUNT + 1)]

    def tenant_idx(self) -> int:
        return self.zipf_tenant() if hasattr(self, "zipf_tenant") else 0

    def build(self, rng: random.Random):
        self.zipf_tenant = zipf_rank_sampler(TENANT_COUNT, ZIPF_THETA * 0.5, rng)
        self.zipf_course = zipf_rank_sampler(COURSE_PER_TENANT, ZIPF_THETA, rng)
        self.zipf_student = zipf_rank_sampler(STUDENT_PER_TENANT, ZIPF_THETA, rng)
        return self

    def course_id(self, t_idx: int, c_rank: int) -> str:
        return stable_uuid("course", self.tenants[t_idx], c_rank)

    def student_id(self, t_idx: int, s_rank: int) -> str:
        return stable_uuid("student", self.tenants[t_idx], s_rank)

    def kp_id(self, t_idx: int, c_rank: int, k_idx: int) -> str:
        return stable_uuid("kp", self.tenants[t_idx], c_rank, k_idx)

    def resource_id(self, t_idx: int, c_rank: int, r_idx: int) -> str:
        return stable_uuid("resource", self.tenants[t_idx], c_rank, r_idx)


CSV_HEADER = ["id", "tenant_id", "user_id", "course_id", "resource_id", "kp_id",
              "interaction_type", "payload_json", "created_at"]


def gen_batch(model: ScaleModel, rng: random.Random, rows: int, batch_no: int) -> list:
    """生成一批交互行(确定性随机种子: 批号+行号)。"""
    out = []
    for i in range(rows):
        g = random.Random(f"{batch_no}:{i}")           # 每行独立确定性种子
        t_idx = model.zipf_tenant()
        c_rank = model.zipf_course()
        s_rank = model.zipf_student()
        kp_idx = g.randrange(KP_PER_COURSE)
        itype = g.choices(INTERACTION_TYPES, weights=TYPE_WEIGHTS, k=1)[0]
        ts = BASE_TS + batch_no * BATCH_ROWS + i       # 单调时间戳(分区友好)
        payload = json.dumps({
            "dwell_ms": g.randint(500, 900_000) if itype == "DWELL" else g.randint(100, 60_000),
            "correct": g.random() < 0.62 if itype in ("ATTEMPT", "SUBMIT") else None,
            "score": round(g.random() * 100, 1) if itype == "SUBMIT" else None,
            "device": g.choice(["pc", "h5", "mp-weixin", "app"]),
        }, ensure_ascii=False, separators=(",", ":"))
        out.append([
            stable_uuid("li", batch_no, i),
            model.tenants[t_idx],
            model.student_id(t_idx, s_rank),
            model.course_id(t_idx, c_rank),
            model.resource_id(t_idx, c_rank, g.randrange(20)),
            model.kp_id(t_idx, c_rank, kp_idx),
            itype,
            payload,
            ts,
        ])
    return out


def hot_key_plan(model: ScaleModel, rng: random.Random, hotkeys: int) -> list:
    """按 Zipf 生成热键计划: (redis_key, value_kind, ttl_seconds, source_desc)。"""
    plan = []
    for i in range(hotkeys):
        g = random.Random(f"hot:{i}")
        t_idx = model.zipf_tenant()
        c_rank = model.zipf_course()
        tenant = model.tenants[t_idx]
        kind = i % 4
        if kind == 0:      # 课程详情缓存: t:{tenant}:course:{id}
            key = f"t:{tenant}:course:{model.course_id(t_idx, c_rank)}"
            vk, ttl = "course_json", 3600
        elif kind == 1:    # 学生掌握度: t:{tenant}:mastery:{sid}
            key = f"t:{tenant}:mastery:{model.student_id(t_idx, model.zipf_student())}"
            vk, ttl = "mastery_json", 600
        elif kind == 2:    # 推荐结果: t:{tenant}:reco:{sid}:{c}
            key = f"t:{tenant}:reco:{model.student_id(t_idx, model.zipf_student())}:{model.course_id(t_idx, c_rank)}"
            vk, ttl = "reco_list", 1800
        else:              # 租户配额/限流计数: t:{tenant}:quota
            key = f"t:{tenant}:quota:{g.choice(['users','courses','api_min'])}"
            vk, ttl = "counter", 90
        plan.append((key, vk, ttl, f"tenant={tenant} zipf_rank_c={c_rank}"))
    return plan


# ---------------------------------------------------------------------------
# 落地后端
# ---------------------------------------------------------------------------

def write_csv(model, rng, args, manifest) -> None:
    os.makedirs(args.out, exist_ok=True)
    t0 = time.time()
    done_rows = 0
    start_batch = 0
    prog_path = os.path.join(args.out, "progress.json")
    if args.resume and os.path.exists(prog_path):
        with open(prog_path, encoding="utf-8") as f:
            p = json.load(f)
        start_batch, done_rows = p["next_batch"], p["done_rows"]
        print(f"[resume] 从批 {start_batch} 继续, 已完成 {done_rows} 行")

    total_batches = (args.rows + BATCH_ROWS - 1) // BATCH_ROWS
    for batch in range(start_batch, total_batches):
        rows = min(BATCH_ROWS, args.rows - batch * BATCH_ROWS)
        data = gen_batch(model, rng, rows, batch)
        path = os.path.join(args.out, f"interactions_{batch:04d}.csv")
        with open(path, "w", encoding="utf-8", newline="") as f:
            w = csv.writer(f)
            w.writerow(CSV_HEADER)
            w.writerows(data)
        done_rows += rows
        if batch % 4 == 0 or batch == total_batches - 1:
            with open(prog_path, "w", encoding="utf-8") as f:
                json.dump({"next_batch": batch + 1, "done_rows": done_rows}, f)
            rate = done_rows / max(time.time() - t0, 1e-6)
            print(f"[csv] batch {batch + 1}/{total_batches} rows={done_rows:,} rate={rate:,.0f} rows/s")

    manifest["rows"] = done_rows
    manifest["mode"] = "csv"
    manifest["batches"] = total_batches


def write_crdb(model, rng, args, manifest) -> None:
    """集群直插: COPY 二进制协议。需 psycopg(broker 网络内运行)。"""
    try:
        import psycopg  # type: ignore
    except ImportError as e:
        raise SystemExit("需要 psycopg3: pip install 'psycopg[binary]'") from e
    assert args.dsn, "--mode crdb 需要 --dsn"
    t0 = time.time()
    done_rows = 0
    with psycopg.connect(args.dsn) as conn, conn.cursor() as cur:
        total_batches = (args.rows + BATCH_ROWS - 1) // BATCH_ROWS
        for batch in range(total_batches):
            rows = min(BATCH_ROWS, args.rows - batch * BATCH_ROWS)
            data = gen_batch(model, rng, rows, batch)
            buf = io_string(data)
            cur.copy_expert(
                f"COPY learning_interactions ({','.join(CSV_HEADER)}) FROM STDIN WITH (FORMAT csv, HEADER true)",
                buf)
            conn.commit()
            done_rows += rows
            rate = done_rows / max(time.time() - t0, 1e-6)
            print(f"[crdb] batch {batch + 1}/{total_batches} rows={done_rows:,} rate={rate:,.0f} rows/s")
    manifest.update({"mode": "crdb", "rows": done_rows})


def io_string(data: list) -> "object":
    import io
    s = io.StringIO()
    w = csv.writer(s)
    w.writerow(CSV_HEADER)
    w.writerows(data)
    s.seek(0)
    return s


def warmup_redis(model, args, manifest) -> None:
    """缓存预热: pipeline 批量写热键, 并落 hotkeys.txt 供压测共用。"""
    plan = hot_key_plan(model, random.Random(42), args.hotkeys)
    os.makedirs(args.out, exist_ok=True)
    hk_path = os.path.join(args.out, "hotkeys.txt")
    with open(hk_path, "w", encoding="utf-8") as f:
        for key, vk, ttl, desc in plan:
            f.write(f"{key}\t{vk}\t{ttl}\t{desc}\n")
    manifest["hotkeys"] = len(plan)
    manifest["hotkeys_file"] = hk_path

    if not args.warmup_redis:
        print(f"[warmup] 未提供 --warmup-redis, 仅输出热键计划({len(plan):,} 条) → {hk_path}")
        return
    try:
        import redis  # type: ignore
    except ImportError as e:
        raise SystemExit("需要 redis-py: pip install redis") from e

    r = redis.Redis.from_url(args.warmup_redis, decode_responses=False)
    VALUE_TPL = {
        "course_json": lambda k: json.dumps({"id": k.split(":")[-1], "status": "PUBLISHED",
                                             "chapters": 12, "kpCount": KP_PER_COURSE}),
        "mastery_json": lambda k: json.dumps({"overall": 0.543, "weakKps": 10}),
        "reco_list": lambda k: json.dumps([stable_uuid("reco", k, i) for i in range(10)]),
        "counter": lambda k: str(random.Random(k).randint(50, 1100)),
    }
    t0 = time.time()
    done = 0
    PIPE = 10_000
    for i in range(0, len(plan), PIPE):
        pipe = r.pipeline(transaction=False)
        for key, vk, ttl, _ in plan[i:i + PIPE]:
            pipe.set(key, VALUE_TPL[vk](key), ex=ttl)
        pipe.execute()
        done += min(PIPE, len(plan) - i)
        rate = done / max(time.time() - t0, 1e-6)
        print(f"[warmup] {done:,}/{len(plan):,} keys rate={rate:,.0f} keys/s")
    manifest["warmup_done"] = done


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--rows", type=int, default=100_000_000, help="目标行数(默认 1 亿)")
    ap.add_argument("--out", default="data/scale100m", help="产物目录")
    ap.add_argument("--mode", choices=["csv", "crdb"], default="csv",
                    help="csv=分片文件(校准/离线导入); crdb=COPY 直插(集群内)")
    ap.add_argument("--dsn", default="", help="CRDB/PG 连接串(mode=crdb 必填)")
    ap.add_argument("--warmup-only", action="store_true", help="仅做缓存 warmup")
    ap.add_argument("--warmup-redis", default="", help="Redis URL(redis://host:6379/0)")
    ap.add_argument("--hotkeys", type=int, default=200_000, help="warmup 热键数量(默认 20 万)")
    ap.add_argument("--resume", action="store_true", help="从断点续跑")
    ap.add_argument("--seed", type=int, default=42)
    args = ap.parse_args()

    rng = random.Random(args.seed)
    model = ScaleModel().build(rng)
    manifest = {"rows": 0, "zipf_theta": ZIPF_THETA, "tenants": TENANT_COUNT,
                "students": TENANT_COUNT * STUDENT_PER_TENANT,
                "courses": TENANT_COUNT * COURSE_PER_TENANT,
                "kps": TENANT_COUNT * COURSE_PER_TENANT * KP_PER_COURSE,
                "seed": args.seed}

    t0 = time.time()
    if not args.warmup_only:
        if args.mode == "crdb":
            write_crdb(model, rng, args, manifest)
        else:
            write_csv(model, rng, args, manifest)
    warmup_redis(model, args, manifest)

    manifest["elapsed_sec"] = round(time.time() - t0, 1)
    mpath = os.path.join(args.out, "manifest.json")
    os.makedirs(args.out, exist_ok=True)
    with open(mpath, "w", encoding="utf-8") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=2)
    print(f"[done] manifest → {mpath}")
    print(json.dumps(manifest, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
