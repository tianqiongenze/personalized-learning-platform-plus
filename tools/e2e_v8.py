# -*- coding: utf-8 -*-
"""E2E 全链路功能验证(v8 部署后) — 在 master 节点上经 curl Pod 执行"""
import sys, os, json, time
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from remote import run

MANIFEST = """
apiVersion: v1
kind: Pod
metadata: {name: curlpod5, namespace: qianrenqianmian}
spec:
  nodeName: k8s-master
  containers:
  - name: c
    image: curlimages/curl:latest
    command: ["sleep","900"]
  restartPolicy: Never
"""

CHECKS = [
    ("tenant-health", "GET http://tenant-service:8081/api/v1/actuator/health", None),
    ("iam-health", "GET http://iam-service:8082/api/v1/actuator/health", None),
    ("course-health", "GET http://course-service:8083/api/v1/actuator/health", None),
    ("teaching-health", "GET http://teaching-service:8084/api/v1/actuator/health", None),
    ("ai-health", "GET http://ai-service:8085/api/v1/actuator/health", None),
    ("gateway-health", "GET http://graphql-gateway:8097/actuator/health", None),
    ("agent-health", "GET http://agent-service:8090/health", None),
    ("kt-health", "GET http://kt-service.infra:8086/health", None),
    ("tenant-list", "GET http://tenant-service:8081/api/v1/tenants", None),
    ("tenant-statistics", "GET http://tenant-service:8081/api/v1/tenants/statistics", None),
    ("gql-tenants", "POST http://graphql-gateway:8097/graphql", '{"query":"{ tenants(size:2){ id code name } }"}'),
    ("gql-services", "POST http://graphql-gateway:8097/graphql", '{"query":"{ serviceHealth { name status } }"}'),
    ("gql-cache", "POST http://graphql-gateway:8097/graphql", '{"query":"{ cacheStats { layer hitRate } }"}'),
    ("kt-mastery", "GET http://kt-service.infra:8086/kt/mastery?studentId=e2e-stu-1", None),
    ("kt-memory", "GET http://kt-service.infra:8086/kt/memory/e2e-stu-1", None),
    ("kt-review", "GET http://kt-service.infra:8086/kt/review/e2e-stu-1", None),
    ("kt-gaps", "GET http://kt-service.infra:8086/kt/gaps/e2e-stu-1", None),
    ("agent-mcp-tools", "GET http://agent-service:8090/api/v1/mcp/tools", None),
    ("agent-multirole", "POST http://agent-service:8090/api/v1/agent/multi_role",
     '{"knowledgePoint":"PLC梯形图","mastery":0.45,"resourceType":"EXERCISE_SET"}'),
]


def main():
    run("10.167.2.175", "kubectl apply -f /dev/stdin <<'EOF'" + MANIFEST + "EOF")
    time.sleep(22)
    results = []
    for name, spec, body in CHECKS:
        method, url = spec.split(" ", 1)
        cmd = "curl -s -m200 -o /dev/stdout -w '\\n%%{http_code}' -X %s %s" % (method, url)
        if body:
            cmd += " -H 'Content-Type: application/json' -d '%s'" % body
        o, e = run("10.167.2.175",
                   "kubectl -n qianrenqianmian exec curlpod5 -- sh -c \"%s\"" % cmd.replace('"', '\\"'))
        out = o.strip()
        code = out.rsplit("\n", 1)[-1]
        body_txt = out.rsplit("\n", 1)[0][:150] if "\n" in out else ""
        ok = code in ("200", "201")
        results.append((name, code, ok, body_txt))
        print(("PASS" if ok else "FAIL"), name, code, body_txt[:120])
    passed = sum(1 for r in results if r[2])
    print("\n== E2E: %d/%d passed ==" % (passed, len(results)))
    run("10.167.2.175", "kubectl -n qianrenqianmian delete pod curlpod5 --grace-period=0 --force >/dev/null 2>&1")


if __name__ == "__main__":
    main()
