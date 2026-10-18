# -*- coding: utf-8 -*-
"""E2E 增补: P3 Durable Session + 增量同步 + P4 A2A push(经 curlpod)"""
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
            extra = parse(body_full)
        except Exception as ex:
            ok, extra = False, "parse-fail: %s" % ex
    (PASS if ok else FAIL).append(name)
    print(("PASS" if ok else "FAIL"), name, code, str(extra)[:170])
    return body_full


# P3: durable session(内存降级模式) + instruction 队列
raw = req("durable-create", "POST", B + "/api/v1/durable/session",
          '{"kind":"interaction","state":{"turn":1,"bloom":"remember"}}',
          parse=lambda s: "sid=%s" % json.loads(s)["sessionId"][:8])
sid = json.loads(raw.split("\n")[0])["sessionId"]
req("durable-resume", "POST", B + "/api/v1/durable/session/%s/resume" % sid,
    '{"kind":"interaction"}',
    parse=lambda s: "turn=%s" % json.loads(s)["state"].get("turn"))
req("durable-instruction", "POST", B + "/api/v1/durable/session/%s/instruction" % sid,
    '{"kind":"interaction","instruction":{"type":"steer","to":"branch"}}',
    parse=lambda s: "inbox=%d" % json.loads(s)["inboxLen"])

# P3: 增量同步 bump → updates
raw = req("sync-bump", "POST", B + "/sync/bump",
          '{"kind":"resource","entityId":"e2e-x","payload":{"title":"E2E"}}',
          parse=lambda s: "rev=%d" % json.loads(s)["revision"])
rev = json.loads(raw.split("\n")[0])["revision"]
req("sync-bump-2", "POST", B + "/sync/bump",
    '{"kind":"profile","entityId":"e2e-y","payload":{"v":2}}')
req("sync-updates", "GET", B + "/sync/updates?since=%d" % rev,
    parse=lambda s: "changed=%d latest=%s" % (json.loads(s)["count"],
                                              json.loads(s)["latestRevision"] >= rev))
req("sync-updates-filter", "GET", B + "/sync/updates?since=0&kinds=resource",
    parse=lambda s: "only-resource=%s" % all(
        c["entity"].startswith("resource") for c in json.loads(s)["changed"]))

# P4: A2A push notification 字段(pushResult 存在于任务终态)
req("a2a-push-field", "POST", B + "/a2a/v1/tasks",
    '{"skillId":"recommend_next","message":{"parts":[{"text":"demo"}]},'
    '"notification":{"url":"http://localhost:9/x"}}',
    parse=lambda s: "pushResult=%s" % ("pushResult" in json.loads(s)))
req("a2a-card-push-cap", "GET", B + "/.well-known/agent-card.json",
    parse=lambda s: "pushCap=%s" % json.loads(s)["capabilities"]["pushNotifications"])

run("10.167.2.175",
    "kubectl -n qianrenqianmian delete pod curlpod7 --grace-period=0 --force >/dev/null 2>&1")
print("\n== p3p4 E2E: %d/%d passed ==" % (len(PASS), len(PASS) + len(FAIL)))
