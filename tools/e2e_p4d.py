# -*- coding: utf-8 -*-
"""E2E 增补: P4 后续 — A2A JSONRPC(官方必选传输) + MCP streamable HTTP(经 curlpod)"""
import sys, json
sys.path.insert(0, "tools")
from remote import run, upload

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
    _o, _e = run("10.167.2.175",
                 "kubectl -n qianrenqianmian get pod curlpod7 -o jsonpath='{.status.phase}' 2>/dev/null")
    if _o.strip() == "Running":
        break
    run("10.167.2.175", "sleep 5")

B = "http://agent-service:8090"
PASS, FAIL = [], []


def finish(name, code, body_full, parse=None):
    ok = code in ("200", "201")
    extra = ""
    if ok and parse:
        try:
            extra = parse(body_full)
        except Exception as ex:
            ok, extra = False, "parse-fail: %s" % ex
    (PASS if ok else FAIL).append(name)
    print(("PASS" if ok else "FAIL"), name, code, str(extra)[:170])


def post_file(name, path, body, parse=None, accept=None):
    """经文件 body 绕过 shell 转义"""
    open("/tmp/e2e_body.json", "w", encoding="utf-8").write(json.dumps(body))
    upload("10.167.2.175", "/tmp/e2e_body.json", "/tmp/e2e_body.json")
    run("10.167.2.175", "kubectl -n qianrenqianmian cp /tmp/e2e_body.json curlpod7:/tmp/e2e_body.json")
    acc = " -H 'Accept: text/event-stream'" if accept else ""
    o, e = run("10.167.2.175",
               "kubectl -n qianrenqianmian exec curlpod7 -- "
               "curl -s -m 40 -o /dev/stdout -w '\\n%{http_code}' -X POST "
               f"{B}{path} -H 'Content-Type: application/json'{acc} -d @/tmp/e2e_body.json")
    out = o.strip()
    finish(name, out.rsplit("\n", 1)[-1],
           out.rsplit("\n", 1)[0] if "\n" in out else out, parse)


# --- A2A JSONRPC: message/send → tasks/get → unknown method ---
post_file("a2a-rpc-send", "/a2a/rpc",
          {"jsonrpc": "2.0", "id": 1, "method": "message/send",
           "params": {"message": {"role": "user",
                                  "parts": [{"kind": "text", "text": "e2e-stu-1"}]},
                      "metadata": {"skillId": "learning_analytics"}}},
          parse=lambda s: "state=%s" % json.loads(s)["result"]["status"]["state"])
post_file("a2a-rpc-badmethod", "/a2a/rpc",
          {"jsonrpc": "2.0", "id": 2, "method": "x/y", "params": {}},
          parse=lambda s: "code=%s" % json.loads(s)["error"]["code"])

# --- A2A message/stream(SSE) ---
post_file("a2a-rpc-stream", "/a2a/rpc/stream",
          {"jsonrpc": "2.0", "id": 3, "method": "message/stream",
           "params": {"message": {"role": "user",
                                  "parts": [{"kind": "text", "text": "kp"}]},
                      "metadata": {"skillId": "deep_interaction"}}},
          parse=lambda s: "final=%s" % ("\"final\": true" in s or "'final': True" in s or "final" in s),
          accept=True)

# --- A2A AgentCard 传输声明 ---
o, e = run("10.167.2.175",
           f"kubectl -n qianrenqianmian exec curlpod7 -- curl -s -m 20 {B}/.well-known/agent-card.json")
finish("a2a-card-transport", "200", o.strip(),
       parse=lambda s: "preferred=%s" % json.loads(s)["preferredTransport"])

# --- MCP streamable HTTP: initialize / tools/list / tools/call ---
post_file("mcp-init", "/mcp", {"jsonrpc": "2.0", "id": 1, "method": "initialize"},
          parse=lambda s: "proto=%s" % json.loads(s)["result"]["protocolVersion"])
post_file("mcp-tools", "/mcp", {"jsonrpc": "2.0", "id": 2, "method": "tools/list"},
          parse=lambda s: "tools=%d" % len(json.loads(s)["result"]["tools"]))
post_file("mcp-call", "/mcp",
          {"jsonrpc": "2.0", "id": 3, "method": "tools/call",
           "params": {"name": "course_search", "arguments": {"query": "PLC"}}},
          parse=lambda s: "isError=%s" % json.loads(s)["result"]["isError"])
post_file("mcp-badmethod", "/mcp",
          {"jsonrpc": "2.0", "id": 4, "method": "resources/list"},
          parse=lambda s: "code=%s" % json.loads(s)["error"]["code"])

run("10.167.2.175",
    "kubectl -n qianrenqianmian delete pod curlpod7 --grace-period=0 --force >/dev/null 2>&1")
print("\n== p4d(rpc/mcp) E2E: %d/%d passed ==" % (len(PASS), len(PASS) + len(FAIL)))
