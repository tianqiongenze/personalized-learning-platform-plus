# -*- coding: utf-8 -*-
"""33618 图谱灌库(集群版): 从 CRDB notebook pod 连 NebulaGraph graphd。
用法: python3 kg_load_33618.py <schema.ngql> <seed.ngql> [host] [port]
"""
import sys
import time

from nebula3.gclient.net import ConnectionPool
from nebula3.Config import Config


def run_file(pool, path, split_per_statement=True):
    with open(path, encoding="utf-8") as f:
        content = f.read()
    # 去注释
    lines = []
    for line in content.split("\n"):
        s = line.strip()
        if s and not s.startswith("#") and not s.startswith("--"):
            lines.append(line)
    text = "\n".join(lines)
    stmts = [s.strip() for s in text.split(";") if s.strip()]
    ok = fail = 0
    errs = []
    session = pool.session_interface()
    for st in stmts:
        try:
            result = session.execute(st)
            if result.is_succeeded():
                ok += 1
            else:
                fail += 1
                if len(errs) < 5:
                    errs.append(result.error_msg()[:120])
        except Exception as e:  # noqa: BLE001
            fail += 1
            if len(errs) < 5:
                errs.append(str(e)[:120])
    return ok, fail, errs


def main():
    schema = sys.argv[1]
    seed = sys.argv[2]
    host = sys.argv[3] if len(sys.argv) > 3 else "nebula-graphd.infra.svc.cluster.local"
    port = int(sys.argv[4]) if len(sys.argv) > 4 else 9669

    cfg = Config()
    cfg.max_connection_pool_size = 2
    pool = ConnectionPool()
    assert pool.init([(host, port)], cfg), "connect failed"

    t0 = time.time()
    ok, fail, errs = run_file(pool, schema)
    print("schema: ok=%d fail=%d %s" % (ok, fail, ("errs=" + str(errs)) if errs else ""))
    time.sleep(2)  # 等 space 创建同步

    ok, fail, errs = run_file(pool, seed)
    print("seed: ok=%d fail=%d %s" % (ok, fail, ("errs=" + str(errs[:3])) if errs else ""))
    print("ELAPSED %.1fs" % (time.time() - t0))

    # 统计验证
    session = pool.session_interface()
    for q in ["USE kg33618", "SUBMIT JOB STATS",
              "MATCH (n:knowledge_point) RETURN count(n) AS kps",
              "MATCH ()-[e:requires]->() RETURN count(e) AS reqs"]:
        try:
            r = session.execute(q)
            if r.is_succeeded():
                print(">>", q[:40], "->", r.values() if "MATCH" in q else "OK")
            else:
                print(">>", q[:40], "-> ERR", r.error_msg()[:80])
        except Exception as e:  # noqa: BLE001
            print(">>", q[:40], "EXC", str(e)[:80])


if __name__ == "__main__":
    main()
