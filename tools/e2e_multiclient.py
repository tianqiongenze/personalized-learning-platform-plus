# -*- coding: utf-8 -*-
"""E2E 增补: 多端交付物 — 设计 tokens 三端产物 + web-student/edu-app 资产完整性(本地静态检查)"""
import json, os, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PASS, FAIL = [], []


def check(name, cond, extra=""):
    (PASS if cond else FAIL).append(name)
    print(("PASS" if cond else "FAIL"), name, extra)


# 1) tokens 单一来源 + 三端产物
tk = json.load(open(os.path.join(ROOT, "design-tokens.json"), encoding="utf-8"))
check("tokens-source", len(tk["color"]["bloom"]) == 6, "bloom 6 级")
for f, kw in [("web-student/src/styles/tokens.css", "--brand-primary"),
              ("edu-app/src/styles/tokens.scss", "$brand-primary"),
              ("ops-web/src/tokens.ts", "'brand-primary'")]:
    p = os.path.join(ROOT, f)
    check(f"tokens-{f.split('/')[0]}", os.path.isfile(p) and kw in open(p, encoding="utf-8").read())

# 2) web-student 资产: 6 页 + AG-UI 助手 + 契约层 + 路由
ws = os.path.join(ROOT, "web-student")
pages = [f for f in os.listdir(os.path.join(ws, "src/pages")) if f.endswith(".vue")]
check("web-6pages", len(pages) == 6, ",".join(sorted(pages)))
check("web-agui-assistant", "aguiRun" in open(os.path.join(ws, "src/components/AiAssistant.vue"), encoding="utf-8").read()
      and "aguiRun" in open(os.path.join(ws, "src/api/index.ts"), encoding="utf-8").read()
      and "TEXT_MESSAGE_CONTENT" in open(os.path.join(ws, "src/components/AiAssistant.vue"), encoding="utf-8").read())
check("web-contract", os.path.isfile(os.path.join(ws, "src/api/index.ts"))
      and "worldWhatIf" in open(os.path.join(ws, "src/api/index.ts"), encoding="utf-8").read())
check("web-router", len(open(os.path.join(ws, "src/router/index.ts"), encoding="utf-8").read().split("path:")) - 1 == 6)
check("web-docker", os.path.isfile(os.path.join(ws, "Dockerfile"))
      and "location /agent/" in open(os.path.join(ws, "deploy/nginx.conf"), encoding="utf-8").read())

# 3) edu-app: review 页注册 + tokens 引用 + 跨端助手
pj = json.load(open(os.path.join(ROOT, "edu-app/src/pages.json"), encoding="utf-8"))
check("uni-review-page", any(p["path"] == "pages/review/index" for p in pj["pages"]))
check("uni-tabbar-5", len(pj["tabBar"]["list"]) == 5)
rv = open(os.path.join(ROOT, "edu-app/src/pages/review/index.vue"), encoding="utf-8").read()
check("uni-review-sm2", "SM-2" in rv and "AiAssistant" in rv)
check("uni-tokens-import", "@import '../../styles/tokens.scss'" in rv)

# 4) 设计文档与 K8s 清单
spec = open(os.path.join(ROOT, "docs/MULTI_CLIENT_DESIGN_SPEC.md"), encoding="utf-8").read()
check("spec-sections", all(k in spec for k in ("产品分析", "架构设计", "设计系统", "ADR-019", "AG-UI")))
k8s = open(os.path.join(ROOT, "k8s/base/new-services.yaml"), encoding="utf-8").read()
check("k8s-web-student", "web-student" in k8s and "30090" in k8s)

print("\n== multiclient E2E: %d/%d passed ==" % (len(PASS), len(PASS) + len(FAIL)))
