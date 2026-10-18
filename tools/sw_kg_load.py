# -*- coding: utf-8 -*-
"""西南图谱灌库(集群版): 逐条执行 seed_southwest.ngql, 失败样本输出定位。
用法: python3 sw_kg_load.py /tmp/kg_sw_seed.ngql
"""
import sys
import time

from nebula3.gclient.net import ConnectionPool
from nebula3.Config import Config


def load(path):
    with open(path, encoding="utf-8") as f:
        content = f.read()
    lines = []
    for line in content.split("\n"):
        s = line.strip()
        if s and not s.startswith("#") and not s.startswith("--"):
            lines.append(line)
    text = "\n".join(lines)
    return [s.strip() for s in text.split(";") if s.strip()]


def main():
    path = sys.argv[1]
    host = "nebula-graphd.infra.svc.cluster.local"
    port = 9669
    cfg = Config()
    cfg.max_connection_pool_size = 2
    pool = ConnectionPool()
    assert pool.init([(host, port)], cfg), "connect failed"
    session = pool.get_session("root", "nebula")
    session.execute("USE kg33618")

    stmts = load(path)
    ok = fail = 0
    fail_samples = []
    for st in stmts:
        for retry in range(3):  # 传播延迟重试
            r = session.execute(st)
            if r.is_succeeded():
                ok += 1
                break
            msg = r.error_msg()[:100]
            if "prop not found" in msg or "Tag not found" in msg:
                time.sleep(8)  # ALTER 传播
                continue
            if retry == 2:
                fail += 1
                if len(fail_samples) < 3:
                    fail_samples.append((st[:60], msg))
            break

    print("TOTAL stmts=%d ok=%d fail=%d" % (len(stmts), ok, fail))
    for s, m in fail_samples:
        print("FAIL_SAMPLE:", s.replace("\n", " ")[:80], "=>", m)


if __name__ == "__main__":
    main()
