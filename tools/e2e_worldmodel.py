# -*- coding: utf-8 -*-
"""E2E 增补: 世界模型 + A2A + AG-UI(经 curlpod)"""
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
run("10.167.2.175", "sleep 15")
B = "http://agent-service:8090"
PASS, FAIL = [], []


def req(name, method, url, body=None, parse=None, timeout=30):
    cmd = "curl -s -m%d -o /dev/stdout -w '\\n%%{http_code}' -X %s %s" % (timeout, method, url)
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
    print(("PASS" if ok else "FAIL"), name, code, str(extra)[:180])
    return body_full


# 1) 世界模型: what_if / simulate / recommend_next / fail-loud
req("world-what-if", "POST", B + "/api/v1/world/what_if",
    '{"masteries":{"动力电池":0.4,"PLC":0.6},"knowledgePoint":"动力电池","prereqs":["PLC"]}',
    parse=lambda s: "rec=%s" % json.loads(s)["result"]["recommended"])
req("world-simulate", "POST", B + "/api/v1/world/simulate",
    '{"masteries":{"kp1":0.3},"plan":[{"action":"exercise","knowledgePoint":"kp1"},'
    '{"action":"review","knowledgePoint":"kp1"}],"horizon":2}',
    parse=lambda s: "reward=%s" % json.loads(s)["result"]["totalReward"])
req("world-recommend", "POST", B + "/api/v1/world/recommend_next",
    '{"masteries":{"a":0.2,"b":0.8},"candidates":["a","b"]}',
    parse=lambda s: "pick=%s" % json.loads(s)["result"]["knowledgePoint"])
req("world-fail-loud", "POST", B + "/api/v1/world/simulate",
    '{"plan":[{"action":"bad","knowledgePoint":"x"}]}',
    parse=lambda s: "rejected" if json.loads(s).get("ok") is False else "NOT-rejected")

# 2) A2A: Agent Card 发现 + 任务委派 + 查询
req("a2a-card", "GET", B + "/.well-known/agent-card.json",
    parse=lambda s: "skills=%d proto=%s" % (len(json.loads(s)["skills"]),
                                            json.loads(s)["protocolVersion"]))
raw = req("a2a-send", "POST", B + "/a2a/v1/tasks",
          '{"skillId":"learning_analytics","message":{"parts":[{"text":"e2e-stu-1"}]}}',
          parse=lambda s: "state=%s" % json.loads(s).get("status", {}).get("state"))
tid = ""
try:
    tid = json.loads(raw.split("\n")[0])["id"]
except Exception:
    pass
if tid:
    req("a2a-get", "GET", B + "/a2a/v1/tasks/" + tid,
        parse=lambda s: "artifacts=%d" % len(json.loads(s).get("artifacts", [])))
req("a2a-bad-skill", "POST", B + "/a2a/v1/tasks",
    '{"skillId":"nope","message":{}}',
    parse=lambda s: "rejected" if "error" in json.loads(s) else "NOT-rejected")

# 3) AG-UI 标准事件流
raw = req("agui-run", "POST", B + "/agui/runs",
          '{"message":"你好, 介绍一下自己","pageContext":"dashboard"}',
          parse=lambda s: "events=%d run_finished=%s" % (
              s.count("RUN_"), "RUN_FINISHED" in s))
run("10.167.2.175",
    "kubectl -n qianrenqianmian delete pod curlpod7 --grace-period=0 --force >/dev/null 2>&1")
print("\n== worldmodel/protocol E2E: %d/%d passed ==" % (len(PASS), len(PASS) + len(FAIL)))
