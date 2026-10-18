# -*- coding: utf-8 -*-
"""上传指定服务的精简构建上下文(源码+Dockerfile+jar, 排除 target 中间物)到 /opt/build"""
import os, sys, time
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from remote import upload_tree, run

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

SERVICES = {
    # java: 源码 + pom + 预编译 jar(Dockerfile COPY target/xxx.jar)
    "tenant-service":  ["pom.xml", "src", "target/tenant-service-1.0.0-SNAPSHOT.jar"],
    "iam-service":     ["pom.xml", "src", "target/iam-service-1.0.0-SNAPSHOT.jar"],
    "course-service":  ["pom.xml", "src", "target/course-service-1.0.0-SNAPSHOT.jar"],
    "teaching-service":["pom.xml", "src", "target/teaching-service-1.0.0-SNAPSHOT.jar"],
    "ai-service":      ["pom.xml", "src", "target/ai-service-1.0.0-SNAPSHOT.jar"],
    "ops-service":     ["pom.xml", "src", "target/ops-service-1.0.0.jar"],
    "graphql-gateway": ["pom.xml", "src", "target/graphql-gateway-1.0.0-SNAPSHOT.jar", "Dockerfile"],
    "kt-service":      ["serve", "models", "requirements.txt", "Dockerfile", "train.Dockerfile", "train_all.py", "checkpoints"],
    "agent-service":   ["app", "requirements.txt", "Dockerfile"],
    "realtime-gateway":["main.go", "go.mod", "Dockerfile"],
    "ops-web":         ["Dockerfile", "nginx.conf", "dist"],
}


def upload_svc(host: str, svc: str, dry=False):
    items = SERVICES.get(svc)
    if items is None:
        print(f"{svc}: unknown"); return
    stage = os.path.join(BASE, f".buildctx/{svc}")
    os.makedirs(stage, exist_ok=True)
    # 清空 stage
    import shutil
    if os.path.exists(stage):
        shutil.rmtree(stage)
    os.makedirs(stage, exist_ok=True)
    for item in items:
        src = os.path.join(BASE, svc, item.replace("/", os.sep))
        dst = os.path.join(stage, item.replace("/", os.sep))
        if os.path.isdir(src):
            shutil.copytree(src, dst)
        elif os.path.isfile(src):
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            shutil.copy2(src, dst)
        else:
            print(f"  warn: {svc}/{item} missing")
    t0 = time.time()
    run(host, f"mkdir -p /opt/build/{svc}")
    upload_tree(host, stage, f"/opt/build/{svc}")
    print(f"{svc}: uploaded {time.time()-t0:.1f}s")


if __name__ == "__main__":
    host = sys.argv[1] if len(sys.argv) > 1 else "10.167.2.175"
    for svc in (sys.argv[2:] or SERVICES):
        upload_svc(host, svc)
