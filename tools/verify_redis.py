# -*- coding: utf-8 -*-
"""验证 dify-plus Redis Cluster 可用性 + 密码探测"""
from kubernetes import client, config
from kubernetes.stream import stream

config.load_kube_config()
core = client.CoreV1Api()


def exec_in(pod, ns, cmd):
    resp = stream(core.connect_get_namespaced_pod_exec, pod, ns,
                  command=["sh", "-c", cmd], stderr=True, stdin=False,
                  stdout=True, tty=False, _preload_content=False)
    return resp.read_stdout()


out = exec_in("redis8-0", "dify-plus",
              "redis-cli ping; "
              "redis-cli -a difyai123456 cluster info 2>/dev/null | head -4; "
              "redis-cli cluster info 2>/dev/null | head -4; "
              "redis-cli -a difyai123456 cluster nodes 2>/dev/null | head -3")
print(out)
