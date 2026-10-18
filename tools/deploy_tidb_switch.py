# -*- coding: utf-8 -*-
"""TiDB 切换部署: 上传 4 jar → 构建 10.1.0 镜像 → 注入 TiDB env → 滚动更新。
env 注入通过 kubectl set env (优先级高于 ConfigMap envFrom)。
"""
import sys
import time

sys.path.insert(0, "D:/studyings/personalized-learning-platform-plus/tools")
from remote import run, conn  # noqa: E402

SERVICES = [
    ("tenant-service", "tenant-build", "8081", 31965, "/api/v1/actuator/health"),
    ("iam-service", "iam-build", "8082", 31826, "/api/v1/actuator/health"),
    ("course-service", "course-build", "8083", 31429, "/api/v1/actuator/health"),
    ("teaching-service", "teach-build", "8084", 32471, "/api/v1/actuator/health"),
]

TIDB_ENV = (
    "DATASOURCE_URL=jdbc:mysql://tidb.infra.svc.cluster.local:4000/{db}"
    "?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&rewriteBatchedStatements=true,"
    "DB_DRIVER=com.mysql.cj.jdbc.Driver,"
    "HIBERNATE_DIALECT=org.hibernate.dialect.MySQLDialect,"
    "DB_USERNAME=root,DB_PASSWORD="
)
# HIBERNATE_DIALECT env 值里的逗号不能出现在 set env 值中 —— 分开设置


def main():
    master = "10.167.2.175"
    reg = "10.100.135.132:5000"

    c = conn(master)
    sftp = c.open_sftp()
    for svc, bdir, port, np, health in SERVICES:
        run(master, "mkdir -p /root/%s" % bdir)
        jar = "%s/target/%s-1.0.0-SNAPSHOT.jar" % (svc, svc)
        print("upload", jar)
        sftp.put(jar, "/root/%s/app.jar" % bdir)
    sftp.close()
    c.close()

    for svc, bdir, port, np, health in SERVICES:
        db = svc.replace("-service", "") + "_db"
        dockerfile = (
            "FROM eclipse-temurin:25-jre-alpine\n"
            "RUN apk add --no-cache curl\n"
            "RUN addgroup -g 1000 -S appgroup && adduser -u 1000 -S appuser -G appgroup\n"
            "WORKDIR /app\n"
            "COPY app.jar app.jar\n"
            "RUN chown -R appuser:appgroup /app\n"
            "USER appuser\n"
            "EXPOSE %s\n"
            "HEALTHCHECK --interval=30s --timeout=5s --start-period=150s --retries=10 "
            "CMD curl -f http://localhost:%s%s || exit 1\n"
            'ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]\n' % (port, port, health)
        )
        c = conn(master)
        sftp = c.open_sftp()
        with sftp.open("/root/%s/Dockerfile" % bdir, "w") as f:
            f.write(dockerfile)
        sftp.close()
        c.close()

        o, e = run(master,
                   "cd /root/%s && docker build -q -t platform/%s:10.1.0 -f Dockerfile . && "
                   "docker tag platform/%s:10.1.0 %s/platform/%s:10.1.0 && "
                   "docker push -q %s/platform/%s:10.1.0 && echo PUSHED" % (bdir, svc, svc, reg, svc, reg, svc),
                   timeout=280)
        print(svc, "build:", o.strip()[-60:], "| ERR:", e[:150] if e.strip() else "none")

        # env 注入(逐个设置, 避免逗号歧义)
        envs = [
            "DATASOURCE_URL=jdbc:mysql://tidb.infra.svc.cluster.local:4000/%s?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&rewriteBatchedStatements=true" % db,
            "DB_DRIVER=com.mysql.cj.jdbc.Driver",
            "HIBERNATE_DIALECT=org.hibernate.dialect.MySQLDialect",
            "DB_USERNAME=root",
            "DB_PASSWORD=",
        ]
        env_cmd = " ".join("'%s'" % v for v in envs)
        o, e = run(master,
                   "kubectl -n qianrenqianmian set image deploy/%s %s=%s/platform/%s:10.1.0 && "
                   "kubectl -n qianrenqianmian set env deploy/%s %s && "
                   "kubectl -n qianrenqianmian rollout status deploy/%s --timeout=420s 2>&1 | tail -1"
                   % (svc, svc, reg, svc, svc, env_cmd, svc),
                   timeout=480)
        print(svc, "deploy:", o.strip()[-120:], "| ERR:", e[:200] if e.strip() else "none")


if __name__ == "__main__":
    main()
