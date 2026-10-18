# -*- coding: utf-8 -*-
"""E2E 增补: 教育层级切换 + 深度交互引擎(经 curlpod, 固定 master 节点用本地缓存镜像)"""
import sys, json
sys.path.insert(0, "tools")
from remote import run

SETUP = (
    "kubectl -n qianrenqianmian delete pod curlpod7 --grace-period=0 --force >/dev/null 2>&1; "
    "kubectl -n qianrenqianmian apply -f - <<'YEOF'\n"
    "apiVersion: v1\nkind: Pod\nmetadata: {name: curlpod7, namespace: qianrenqianmian}\n"
    "spec:\n"
    "  nodeName: k8s-master\n"
    "  containers: [{name: c, image: curlimages/curl:latest, command: [\"sleep\",\"3600\"]}]\n"
    "  restartPolicy: Never\nYEOF"
)
run("10.167.2.175", SETUP)
run("10.167.2.175", "sleep 15; kubectl -n qianrenqianmian get pod curlpod7 | tail -1")
B = "http://agent-service:8090"
PASS, FAIL = [], []


def req(name, method, url, body=None, parse=None):
    cmd = "curl -s -m30 -o /dev/stdout -w '\\n%%{http_code}' -X %s %s" % (method, url)
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
            extra = parse(json.loads(body_full))
        except Exception as ex:
            ok, extra = False, "parse-fail: %s" % ex
    (PASS if ok else FAIL).append(name)
    print(("PASS" if ok else "FAIL"), name, code, str(extra)[:200])
    return body_full


req("pedagogy-profiles", "GET", B + "/api/v1/pedagogy/profiles",
    parse=lambda d: "profiles=%s" % [p["id"] for p in d["profiles"]])
req("pedagogy-resolve-graduate", "GET",
    B + "/api/v1/pedagogy/resolve?profileId=graduate&workflow=fast",
    parse=lambda d: "ceiling=%s socratic=%s" % (d["resolved"]["bloomCeiling"],
                                                d["resolved"]["interaction"]["socraticLevel"]))
req("pedagogy-fail-loud", "GET", B + "/api/v1/pedagogy/resolve?profileId=nope",
    parse=lambda d: "rejected-as-expected" if d.get("ok") is False else "NOT-rejected")
req("interaction-start", "POST", B + "/api/v1/interaction/start",
    '{"profileId":"vocational","knowledgePoint":"PLC 梯形图"}',
    parse=lambda d: "ceiling=%s" % d["session"]["bloomCeiling"])
raw = req("interaction-start-2", "POST", B + "/api/v1/interaction/start",
          '{"profileId":"vocational","knowledgePoint":"PLC 梯形图"}')
sid = json.loads(raw.split("\n")[0])["session"]["sessionId"]
for i in range(6):
    req("interaction-answer-%d" % (i + 1), "POST", B + "/api/v1/interaction/answer",
        '{"sessionId":"%s","correct":true}' % sid,
        parse=lambda d: "stage=%s bloom=%s" % (d["result"]["stage"], d["result"]["bloomLevel"]))
req("interaction-summary", "GET", B + "/api/v1/interaction/%s/summary" % sid,
    parse=lambda d: "maxBloom=%s branches=%d correctRate=%s" % (
        d["summary"]["maxBloomReached"], len(d["summary"]["breadthFootprint"]),
        d["summary"]["correctRate"]))

print("\n== pedagogy E2E: %d/%d passed ==" % (len(PASS), len(PASS) + len(FAIL)))
run("10.167.2.175",
    "kubectl -n qianrenqianmian delete pod curlpod7 --grace-period=0 --force >/dev/null 2>&1")
