# -*- coding: utf-8 -*-
"""持续拉取 metad + storaged(层增量下载, 多次重试直到成功), 成功后扩容 nebula + 灌库。"""
import sys
import time

sys.path.insert(0, "tools")
from remote import run

LOG = []


def pull_until_ok(img, max_tries=12):
    for i in range(max_tries):
        cmd = ("timeout 270 docker pull docker.1ms.run/%s >/dev/null 2>&1 && "
               "docker tag docker.1ms.run/%s %s && echo %s_OK || echo TRY_%d") % (img, img, img, img.split("/")[1].split(":")[0].upper(), i)
        o, e = run("10.167.2.175", cmd, timeout=300)
        s = o.strip()[-20:]
        LOG.append("%s try%d -> %s" % (img.split("/")[1], i, s))
        print(LOG[-1])
        sys.stdout.flush()
        if "_OK" in s:
            return True
        time.sleep(3)
    return False


ok_m = pull_until_ok("vesoft/nebula-metad:v3.8.0")
ok_s = pull_until_ok("vesoft/nebula-storaged:v3.8.0")
print("metad:", ok_m, "storaged:", ok_s)
sys.stdout.flush()

if ok_m and ok_s:
    print("== 导入 containerd(两节点) ==")
    run("10.167.2.175",
        "docker save vesoft/nebula-metad:v3.8.0 vesoft/nebula-storaged:v3.8.0 -o /tmp/md_st.tar && "
        "ctr -n k8s.io images import /tmp/md_st.tar 2>&1 | tail -1 && "
        "scp /tmp/md_st.tar 10.167.2.176:/tmp/ 2>/dev/null; "
        "ssh 10.167.2.176 'ctr -n k8s.io images import /tmp/md_st.tar 2>&1 | tail -1'")
    print("== 扩容 nebula ==")
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
    print("镜像拉取未完成: metad=%s storaged=%s" % (ok_m, ok_s))
