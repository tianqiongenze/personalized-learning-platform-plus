# -*- coding: utf-8 -*-
"""分布式压测基础设施部署器:
1. 上传 load.js 与 locustfile_cluster.py 到 master 生成 ConfigMap
2. 部署 K6 Loadgen(8 副本) + Locust master/worker(6 worker)
3. 支持不同并发级别: scale K6_VU 或改 K6_STAGES
"""
import json
import sys

sys.path.insert(0, "D:/studyings/personalized-learning-platform-plus/tools")
from remote import run, conn  # noqa: E402

BASE = "D:/studyings/personalized-learning-platform-plus"
MASTER = "10.167.2.175"


def deploy_k6(vus=12500, duration="6m"):
    """部署/更新 K6 压测实例"""
    with open(BASE + "/tests/distributed/load.js", encoding="utf-8") as f:
        load_js = f.read()
    # ConfigMap 用 shell heredoc 写入(避免 kubectl create configmap 需要单独文件)
    c = conn(MASTER)
    sftp = c.open_sftp()
    with sftp.open("/root/load.js", "w") as f:
        f.write(load_js)
    sftp.close()
    c.close()
    o, e = run(MASTER, "kubectl -n qianrenqianmian create configmap k6-loadgen-script "
                       "--from-file=load.js=/root/load.js -o yaml --dry-run=client | kubectl apply -f - && echo CM_OK")
    print("configmap:", o.strip())
    # patch 环境变量(VU 与时长) + 部署
    stages = json.dumps([{"duration": duration, "target": vus}])
    o, e = run(MASTER,
               "kubectl -n qianrenqianmian set env deploy/k6-loadgen K6_STAGES='%s' K6_VU=%d && "
               "kubectl -n qianrenqianmian rollout restart deploy/k6-loadgen && "
               "kubectl -n qianrenqianmian rollout status deploy/k6-loadgen --timeout=300s 2>&1 | tail -1"
               % (stages, vus), timeout=360)
    print("k6:", o.strip())


def deploy_locust():
    with open(BASE + "/tests/distributed/locustfile_cluster.py", encoding="utf-8") as f:
        loc = f.read()
    c = conn(MASTER)
    sftp = c.open_sftp()
    with sftp.open("/root/locustfile_cluster.py", "w") as f:
        f.write(loc)
    sftp.close()
    c.close()
    o, e = run(MASTER, "kubectl -n qianrenqianmian create configmap locust-cluster-script "
                       "--from-file=locustfile_cluster.py=/root/locustfile_cluster.py -o yaml --dry-run=client | "
                       "kubectl apply -f - && echo CM_OK")
    print("locust configmap:", o.strip())


def main():
    vus = int(sys.argv[1]) if len(sys.argv) > 1 else 12500
    duration = sys.argv[2] if len(sys.argv) > 2 else "6m"
    deploy_k6(vus, duration)
    deploy_locust()
    print("DISTRIBUTED LOADTEST INFRA READY")


if __name__ == "__main__":
    main()
