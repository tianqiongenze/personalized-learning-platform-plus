# -*- coding: utf-8 -*-
"""E2E 增补: P4 续(a2a async/世界模型接线) + 科研采集器(经 curlpod)"""
import sys, json
sys.path.insert(0, "tools")
from remote import run

SETUP = (
    "kubectl -n qianrenqianmian delete pod curlpod7 --grace-period=0 --force >/dev/null 2>&1; "
    "kubectl -n qianrenqianmian apply -f - <<'YEOF'\n"
    "apiVersion: v1\nkind: Pod\nmetadata: {name: curlpod7, namespace: qianrenqianmian}\n"
    "spec:\n  nodeName: k8s-master\n"
    "  containers: [{name: c, image: curlimages/curl:latest, command: [\"sleep\",\"3600\"]}]\n"
    "  restartPolicy: Never\nYEOF"
)
run("10.167.2.175", SETUP)
for _i in range(12):
    _o, _e = run("10.167.2.175", "kubectl -n qianrenqianmian get pod curlpod7 -o jsonpath='{.status.phase}' 2>/dev/null")
    if _o.strip() == "Running":
        break
    run("10.167.2.175", "sleep 5")
B = "http://agent-service:8090"
PASS, FAIL = [], []


def req(name, method, url, body=None, parse=None):
    cmd = "curl -s -m40 -o /dev/stdout -w '\\n%%{http_code}' -X %s %s" % (method, url)
    if body:
        cmd += " -H 'Content-Type: application/json' -d '%s'" % body
    o, e = run("10.167.2.175",
               "kubectl -n qianrenqianmian exec curlpod7 -- sh -c \"%s\"" % cmd.replace('"', '\\"'))
    out = o.strip()
    code = out.rsplit("\n", 1)[-1]
    body_full = out.rsplit("\n", 1)[0] if "\n" in out else out
    ok = code in ("200", "201")
    extra = ""
    if ok and parse:
        try:
            extra = parse(body_full)
        except Exception as ex:
            ok, extra = False, "parse-fail: %s" % ex
    (PASS if ok else FAIL).append(name)
    print(("PASS" if ok else "FAIL"), name, code, str(extra)[:170])
    return body_full


# P4 续: A2A 异步队列(本地降级) + recommend_next 技能接世界模型(同步路径)
# rec-skill body 含嵌套引号, 写入 pod 内文件后 curl -d @file 绕过 shell 转义
_upload_rec_body = json.dumps({
    "skillId": "recommend_next",
    "message": {"parts": [{"text": json.dumps(
        {"masteries": {"a": 0.2, "b": 0.8}, "candidates": ["a", "b"]})}]}})
from remote import upload as _up
open("/tmp/rec_body.json", "w", encoding="utf-8").write(_upload_rec_body)
_up("10.167.2.175", "/tmp/rec_body.json", "/tmp/rec_body.json")
run("10.167.2.175", "kubectl -n qianrenqianmian cp /tmp/rec_body.json curlpod7:/tmp/rec_body.json")
_o, _e = run("10.167.2.175",
             "kubectl -n qianrenqianmian exec curlpod7 -- "
             "curl -s -m 40 -o /dev/stdout -w '\\n%{http_code}' -X POST "
             "http://agent-service:8090/a2a/v1/tasks -H 'Content-Type: application/json' "
             "-d @/tmp/rec_body.json")
_out = _o.strip()
_code = _out.rsplit("\n", 1)[-1]
_ok = _code in ("200", "201")
try:
    _extra = ("rec=%s" % json.loads(_out.rsplit("\n", 1)[0])
              ["artifacts"][0]["content"][:60]) if _ok else "bad"
except Exception as ex:
    _ok, _extra = False, "parse-fail: %s" % ex
(PASS if _ok else FAIL).append("a2a-rec-skill")
print(("PASS" if _ok else "FAIL"), "a2a-rec-skill", _code, _extra[:170])
req("a2a-async-queue", "POST", B + "/a2a/v1/tasks?mode=async",
    '{"skillId":"learning_analytics","message":{"parts":[{"text":"e2e-stu-1"}]}}',
    parse=lambda s: "queued=%s backend=%s" % (
        json.loads(s).get("queue", {}).get("queued"),
        json.loads(s).get("queue", {}).get("backend")))

# 科研采集器: 登记 / 列表 / 导出
req("research-record", "POST", B + "/api/v1/research/experiments",
    '{"tag":"e2e-baseline","condition":{"worldModel":true},"metrics":{"ruleScore":0.86}}',
    parse=lambda s: "expId=%s" % json.loads(s)["experiment"]["expId"][:8])
req("research-list", "GET", B + "/api/v1/research/experiments?tag=e2e-baseline",
    parse=lambda s: "count>=1 len=%d" % len(json.loads(s)["experiments"]))
req("research-export", "POST", B + "/api/v1/research/datasets/export",
    '{"kind":"learning_events","limit":50}',
    parse=lambda s: "anonymized=%s" % json.loads(s)["anonymized"])

run("10.167.2.175",
    "kubectl -n qianrenqianmian delete pod curlpod7 --grace-period=0 --force >/dev/null 2>&1")
print("\n== p4c/research E2E: %d/%d passed ==" % (len(PASS), len(PASS) + len(FAIL)))
