# -*- coding: utf-8 -*-
"""镜像分发: master docker -> tar -> 两节点 ctr -n k8s.io import"""
import sys, os
sys.path.insert(0, r"D:\studyings\personalized-learning-platform-plus\tools")
from remote import conn, run

IMAGES = [
    # 应用镜像
    "platform/tenant-service:2.0.0", "platform/iam-service:2.0.0",
    "platform/course-service:2.0.0", "platform/teaching-service:2.0.0",
    "platform/ops-service:2.0.0", "platform/ai-service:2.0.0",
    "platform/kt-service:2.0.0", "platform/agent-service:2.0.0",
    "platform/vector-reranker:2.0.0", "platform/realtime-gateway:2.0.0",
    # 中间件镜像
    "apachepulsar/pulsar:4.0.5",
    "milvusdb/milvus:v2.6.6", "zilliz/attu:v2.6.0",
    "quay.io/coreos/etcd:v3.6.5", "minio/minio:RELEASE.2025-09-07T16-13-09Z",
    "vesoft/nebula-metad:v3.8.3", "vesoft/nebula-graphd:v3.8.3",
    "vesoft/nebula-storaged:v3.8.3", "vesoft/nebula-console:v3.8.0",
]

MASTER = "10.167.2.175"
WORKER = "10.167.2.176"

def main():
    # 1) master: docker save 全部镜像 -> 单个 tar(带 gzip 太慢, 用原生 tar)
    imgs = " ".join(IMAGES)
    print("[1/4] master docker save ...", flush=True)
    out, err = run(MASTER,
        f"docker save {imgs} -o /opt/images-qrr.tar && ls -la /opt/images-qrr.tar", 3600)
    print(out, err)

    # 2) master: ctr import
    print("[2/4] master ctr import ...", flush=True)
    out, err = run(MASTER, "ctr -n k8s.io images import /opt/images-qrr.tar 2>&1 | tail -3", 3600)
    print(out, err)

    # 3) master -> worker 传输(内网 scp 经 sshpass 不存在; 用 python sftp 中转: master 下载 -> 本机 -> worker)
    print("[3/4] transfer master -> worker via local relay ...", flush=True)
    c = conn(MASTER)
    sftp = c.open_sftp()
    sftp.get("/opt/images-qrr.tar", r"D:\studyings\qrr-images.tar")
    sftp.close(); c.close()
    print("downloaded to local, size:", os.path.getsize(r"D:\studyings\qrr-images.tar") // 1024 // 1024, "MB")
    c = conn(WORKER)
    sftp = c.open_sftp()
    sftp.put(r"D:\studyings\qrr-images.tar", "/opt/images-qrr.tar")
    sftp.close(); c.close()
    print("uploaded to worker")

    # 4) worker: ctr import
    print("[4/4] worker ctr import ...", flush=True)
    out, err = run(WORKER, "ctr -n k8s.io images import /opt/images-qrr.tar 2>&1 | tail -3", 3600)
    print(out, err)
    print("DONE")

if __name__ == "__main__":
    main()
