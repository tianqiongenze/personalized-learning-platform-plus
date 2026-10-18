# -*- coding: utf-8 -*-
"""西南图谱灌库(循环版): 反复整文件执行直到 ok=41 或超时。
INSERT 幂等(OVERWRITE 语义), 可安全重入。
"""
import sys
import time

sys.path.insert(0, "/tmp")
from sw_kg_load import load  # noqa: E402

from nebula3.gclient.net import ConnectionPool  # noqa: E402
from nebula3.Config import Config  # noqa: E402


def one_round(session, stmts):
    ok = fail = 0
    for st in stmts:
        r = session.execute(st)
        if r.is_succeeded():
            ok += 1
        else:
            fail += 1
    return ok, fail


def main():
    path = sys.argv[1]
    target = int(sys.argv[2]) if len(sys.argv) > 2 else 41
    cfg = Config()
    cfg.max_connection_pool_size = 2
    pool = ConnectionPool()
    assert pool.init([("nebula-graphd.infra.svc.cluster.local", 9669)], cfg)
    session = pool.get_session("root", "nebula")
    session.execute("USE kg33618")
    stmts = load(path)

    t0 = time.time()
    last = (-1, -1)
    while time.time() - t0 < 720:
        ok, fail = one_round(session, stmts)
        last = (ok, fail)
        print("round ok=%d fail=%d elapsed=%.0fs" % (ok, fail, time.time() - t0))
        sys.stdout.flush()
        if fail == 0 and ok >= target:
            print("ALL_DONE ok=%d" % ok)
            return
        time.sleep(20)
    print("TIMEOUT last_ok=%d last_fail=%d" % last)


if __name__ == "__main__":
    main()
