# -*- coding: utf-8 -*-
"""拉取剩余 nebula 镜像并灌库 33618 图谱(一次跑完)。"""
import sys
import time

sys.path.insert(0, "tools")
from remote import run, upload

IMGS = ["vesoft/nebula-metad:v3.8.0", "vesoft/nebula-storaged:v3.8.0"]

for img in IMGS:
    for attempt in range(4):
        cmd = ("timeout 270 docker pull docker.1ms.run/%s >/dev/null 2>&1 && "
               "docker tag docker.1ms.run/%s %s && echo PULLED || echo RETRY") % (img, img, img)
        o, e = run("10.167.2.175", cmd, timeout=300)
        status = o.strip()[-10:]
        print(img.split("/")[1], "attempt", attempt, "->", status)
        if "PULLED" in status:
            break
        time.sleep(5)

# 检查镜像
o, e = run("10.167.2.175", "docker images --format '{{.Repository}}:{{.Tag}}' | grep vesoft")
print("images:", o.strip().replace("\n", ", "))

# 若三镜像齐, 扩容 nebula 并等待就绪
count = o.count("vesoft/nebula")
if count >= 3:
    print("== 扩容 nebula ==")
    run("10.167.2.175", "kubectl -n infra scale deploy nebula-graphd --replicas=1; "
        "kubectl -n infra scale sts nebula-metad --replicas=1; "
        "kubectl -n infra scale sts nebula-storaged --replicas=1")
    for attempt in range(10):
        time.sleep(20)
        o, e = run("10.167.2.175", "kubectl -n infra get pods --no-headers 2>/dev/null | grep nebula | awk '{print $3}' | grep -c Running")
        if o.strip() == "3":
            print("nebula 3 pods Running")
            break
        print("waiting...", o.strip())
    # 灌库
    o, e = run("10.167.2.175", "kubectl -n infra exec cockroachdb-a-0 -- python3 /tmp/kg_load.py /tmp/kg_schema.ngql /tmp/kg_seed.ngql nebula-graphd.infra.svc.cluster.local 9669 2>&1 | tail -6", timeout=280)
    print("== 灌库结果 ==")
    print(o[-800:])
else:
    print("镜像不足(%d/3), 跳过灌库" % count)
