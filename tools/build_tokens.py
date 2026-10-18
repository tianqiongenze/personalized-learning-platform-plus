# -*- coding: utf-8 -*-
"""设计 tokens 编译器: design-tokens.json 单一来源 → 三端样式文件
产出:
  web-student/src/styles/tokens.css   (CSS Variables, Vue3 Web)
  edu-app/src/styles/tokens.scss      (SCSS Variables, uni-app)
  ops-web/src/tokens.ts               (TS 常量, React 管理端)
用法: python tools/build_tokens.py
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
tokens = json.load(open(os.path.join(ROOT, "design-tokens.json"), encoding="utf-8"))

FLAT = {}


def _flatten(prefix: str, node: dict) -> None:
    for k, v in node.items():
        key = f"{prefix}-{k}" if prefix else k
        if isinstance(v, dict):
            _flatten(key, v)
        else:
            FLAT[key] = v


_flatten("", tokens["color"])
_flatten("", tokens["radius"])
_flatten("", tokens["spacing"])
_flatten("font", tokens["typography"]["fontFamily"] and {"family": tokens["typography"]["fontFamily"]})
for level in ("title", "body", "caption"):
    FLAT[f"font-{level}-size"] = tokens["typography"][level]["size"]
    FLAT[f"font-{level}-weight"] = tokens["typography"][level]["weight"]

# ---- Web CSS ----
web_dir = os.path.join(ROOT, "web-student", "src", "styles")
os.makedirs(web_dir, exist_ok=True)
css = [":root {"]
for k, v in FLAT.items():
    css.append(f"  --{k.replace('_', '-')}: {v};")
css.append("}")
open(os.path.join(web_dir, "tokens.css"), "w", encoding="utf-8").write("\n".join(css))

# ---- uni-app SCSS ----
uni_dir = os.path.join(ROOT, "edu-app", "src", "styles")
os.makedirs(uni_dir, exist_ok=True)
scss = []
for k, v in FLAT.items():
    scss.append(f"${k.replace('_', '-')}: {v};")
open(os.path.join(uni_dir, "tokens.scss"), "w", encoding="utf-8").write("\n".join(scss))

# ---- React TS ----
ops_dir = os.path.join(ROOT, "ops-web", "src")
os.makedirs(ops_dir, exist_ok=True)
ts = ["// AUTO-GENERATED from design-tokens.json — do not edit", "export const tokens = {"]
for k, v in FLAT.items():
    ts.append(f"  '{k}': '{v}',")
ts.append("} as const;")
open(os.path.join(ops_dir, "tokens.ts"), "w", encoding="utf-8").write("\n".join(ts))

print(f"tokens: {len(FLAT)} vars -> 3 targets (web-student / edu-app / ops-web)")
