# -*- coding: utf-8 -*-
"""CRDB / TiDB 双引擎一键切换(运行时通过 env, 无需重新构建镜像)。
用法:
  python tools/switch_datasource.py tidb     # 切到 TiDB(默认)
  python tools/switch_datasource.py crdb     # 切回 CRDB
  python tools/switch_datasource.py status   # 查看当前各服务数据源

原理: application.yml 已 env 化(DATASOURCE_URL/DB_DRIVER/HIBERNATE_DIALECT/DB_USERNAME/DB_PASSWORD),
kubectl set env 优先级高于 ConfigMap, 滚动重启后生效。两类数据源均已实现 TiDB 兼容层:
  * uuid→char(36) + @JdbcTypeCode(SqlTypes.CHAR)
  * jsonb→json + AttributeConverter
  * COUNT(t.id) / count()>0 default 方法(兼容 TiDB SQL 严格模式)
  * native query 全蛇形列名
"""
import sys

sys.path.insert(0, "D:/studyings/personalized-learning-platform-plus/tools")
from remote import run  # noqa: E402

SERVICES = ["tenant-service", "iam-service", "course-service", "teaching-service"]

TIDB = [
    "DATASOURCE_URL=jdbc:mysql://tidb.infra.svc.cluster.local:4000/{db}?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&rewriteBatchedStatements=true",
    "DB_DRIVER=com.mysql.cj.jdbc.Driver",
    "HIBERNATE_DIALECT=org.hibernate.dialect.MySQLDialect",
    "DB_USERNAME=root",
    "DB_PASSWORD=",
]

CRDB = [
    "DATASOURCE_URL=jdbc:postgresql://{host}/{db}?sslmode=disable",
    "DB_DRIVER=org.postgresql.Driver",
    "HIBERNATE_DIALECT=org.hibernate.dialect.CockroachDialect",
    "DB_USERNAME=root",
    "DB_PASSWORD=",
]

CRDB_HOST = "cockroachdb.infra.svc.cluster.local:26257"


def apply(engine):
    for svc in SERVICES:
        db = svc.replace("-service", "") + "_db"
        tmpl = TIDB if engine == "tidb" else CRDB
        envs = []
        for e in tmpl:
            e = e.format(db=db, host=CRDB_HOST)
            envs.append(e)
        env_cmd = " ".join("'%s'" % v for v in envs)
        o, e = run("10.167.2.175",
                   "kubectl -n qianrenqianmian set env deploy/%s %s && "
                   "kubectl -n qianrenqianmian rollout status deploy/%s --timeout=420s 2>&1 | tail -1"
                   % (svc, env_cmd, svc), timeout=480)
        print("%s -> %s: %s" % (svc, engine, o.strip().splitlines()[-1][:80]))


def status():
    for svc in SERVICES:
        o, e = run("10.167.2.175",
                   "kubectl -n qianrenqianmian get deploy %s -o jsonpath='{.spec.template.spec.containers[0].env[?(@.name==\"DATASOURCE_URL\")].value}' 2>/dev/null" % svc)
        url = o.strip() or "(默认 ConfigMap)"
        print("%-18s %s" % (svc, url[:90]))


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return
    cmd = sys.argv[1].lower()
    if cmd == "status":
        status()
    elif cmd in ("tidb", "crdb"):
        apply(cmd)
    else:
        print("unknown:", cmd)


if __name__ == "__main__":
    main()
