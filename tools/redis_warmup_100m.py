# -*- coding: utf-8 -*-
"""Redis warmup(集群版, python3.6 兼容): 复用 dify-plus Redis Cluster。
与 tools/datagen_100m.py 同构: 同 Zipf 分布、同 stable_uuid → 热键与压测/数据可互hit。
用法(集群内 pod): python3 redis_warmup_100m.py <hotkeys> <host> <port> <password>
"""
import hashlib
import json
import random
import sys
import time

TENANT_COUNT = 20
COURSE_PER_TENANT = 500
STUDENT_PER_TENANT = 5000
ZIPF_THETA = 1.1


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


def build_plan(hotkeys, seed=42):
    rng = random.Random(seed)
    z_tenant = zipf_sampler(TENANT_COUNT, ZIPF_THETA * 0.5)
    z_course = zipf_sampler(COURSE_PER_TENANT, ZIPF_THETA)
    z_student = zipf_sampler(STUDENT_PER_TENANT, ZIPF_THETA)
    plan = []
    for i in range(hotkeys):
        g = random.Random("hot:%d" % i)
        t = z_tenant()
        c = z_course()
        tenant = "tenant-%02d" % (t + 1)
        kind = i % 4
        if kind == 0:
            key = "t:%s:course:%s" % (tenant, stable_uuid("course", tenant, c))
            vk, ttl = "course_json", 3600
        elif kind == 1:
            key = "t:%s:mastery:%s" % (tenant, stable_uuid("student", tenant, z_student()))
            vk, ttl = "mastery_json", 600
        elif kind == 2:
            key = "t:%s:reco:%s:%s" % (tenant, stable_uuid("student", tenant, z_student()),
                                       stable_uuid("course", tenant, c))
            vk, ttl = "reco_list", 1800
        else:
            key = "t:%s:quota:%s" % (tenant, g.choice(["users", "courses", "api_min"]))
            vk, ttl = "counter", 90
        plan.append((key, vk, ttl))
    return plan


VALUE_TPL = {
    "course_json": lambda k: json.dumps({"id": k.split(":")[-1], "status": "PUBLISHED",
                                         "chapters": 12, "kpCount": 40}),
    "mastery_json": lambda k: json.dumps({"overall": 0.543, "weakKps": 10}),
    "reco_list": lambda k: json.dumps([stable_uuid("reco", k, i) for i in range(10)]),
    "counter": lambda k: str(random.Random(k).randint(50, 1100)),
}


def main():
    hotkeys = int(sys.argv[1]) if len(sys.argv) > 1 else 200000
    host = sys.argv[2] if len(sys.argv) > 2 else "redis.dify-plus.svc.cluster.local"
    port = int(sys.argv[3]) if len(sys.argv) > 3 else 6379
    password = sys.argv[4] if len(sys.argv) > 4 else "difyai123456"

    import redis
    r = redis.Redis(host=host, port=port, password=password, socket_timeout=5)
    r.ping()
    print("PING OK ->", host, port)

    plan = build_plan(hotkeys)
    PIPE = 10000
    t0 = time.time()
    done = 0
    for i in range(0, len(plan), PIPE):
        pipe = r.pipeline(transaction=False)
        for key, vk, ttl in plan[i:i + PIPE]:
            pipe.set(key, VALUE_TPL[vk](key), ex=ttl)
        pipe.execute()
        done += min(PIPE, len(plan) - i)
        rate = done / max(time.time() - t0, 1e-6)
        print("warmup %d/%d rate=%.0f keys/s" % (done, len(plan), rate))
        sys.stdout.flush()
    print("WARMUP DONE keys=%d elapsed=%.1fs" % (done, time.time() - t0))


if __name__ == "__main__":
    main()
