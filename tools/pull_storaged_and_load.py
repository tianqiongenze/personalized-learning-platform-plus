# -*- coding: utf-8 -*-
"""storaged 拉取(重试) → 扩容 nebula → 灌库 33618 → 验证。"""
import sys
import time

sys.path.insert(0, "tools")
from remote import run

ok_s = False
for i in range(10):
    cmd = ("timeout 270 docker pull docker.1ms.run/vesoft/nebula-storaged:v3.8.0 >/dev/null 2>&1 && "
           "docker tag docker.1ms.run/vesoft/nebula-storaged:v3.8.0 vesoft/nebula-storaged:v3.8.0 && echo STORAGED_OK || echo R%d") % i
    o, e = run("10.167.2.175", cmd, timeout=300)
    print("storaged try", i, "->", o.strip()[-14:])
    sys.stdout.flush()
    if "OK" in o:
        ok_s = True
        break
    time.sleep(3)

print("metad: 已有, storaged:", ok_s)
sys.stdout.flush()

if ok_s:
    print("== 导入两节点 containerd ==")
    run("10.167.2.175",
        "docker save vesoft/nebula-metad:v3.8.0 vesoft/nebula-storaged:v3.8.0 -o /tmp/mdst.tar && "
        "ctr -n k8s.io images import /tmp/mdst.tar 2>&1 | tail -1 && "
        "docker save vesoft/nebula-graphd:v3.8.0 vesoft/nebula-metad:v3.8.0 vesoft/nebula-storaged:v3.8.0 -o /tmp/all3.tar && "
        "scp -o StrictHostKeyChecking=no /tmp/all3.tar root@10.167.2.176:/tmp/ && "
        "ssh -o StrictHostKeyChecking=no root@10.167.2.176 'ctr -n k8s.io images import /tmp/all3.tar 2>&1 | tail -1' && echo IMPORT_OK", timeout=280)
    print("== 扩容 nebula 三组件 ==")
    run("10.167.2.175", "kubectl -n infra scale deploy nebula-graphd --replicas=1; "
        "kubectl -n infra scale sts nebula-metad --replicas=1; "
        "kubectl -n infra scale sts nebula-storaged --replicas=1")
    ready = False
    for attempt in range(12):
        time.sleep(20)
        o, e = run("10.167.2.175", "kubectl -n infra get pods --no-headers 2>/dev/null | grep nebula | awk '{print $3}' | grep -c Running")
        print("running=%s" % o.strip())
        sys.stdout.flush()
        if o.strip() == "3":
            ready = True
            break
    if ready:
        print("== 灌库 33618 ==")
        o, e = run("10.167.2.175",
                   "kubectl -n infra exec cockroachdb-a-0 -- python3 /tmp/kg_load.py /tmp/kg_schema.ngql /tmp/kg_seed.ngql nebula-graphd.infra.svc.cluster.local 9669 2>&1 | tail -8",
                   timeout=280)
        print(o[-900:])
    else:
        print("nebula pods 未全部 Running")
else:
    print("storaged 拉取失败")
