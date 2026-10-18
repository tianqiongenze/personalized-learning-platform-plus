#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把"单文件多个 public 顶层类型"的 Java 文件拆分为一个类型一个文件(符合 JLS)。"""
import io, os, re, sys

TYPE_START = re.compile(r'^(?:(?:public|protected|private|final|abstract|sealed|non-sealed|static)\s+)*(class|interface|record|enum)\s+(\w+)')

def split_file(path):
    lines = io.open(path, encoding="utf-8").read().split("\n")
    # header: package + imports + 顶层注释(不含类型)
    header_end = 0
    for i, l in enumerate(lines):
        if TYPE_START.match(l):
            header_end = i
            break
    else:
        return 0
    header = lines[:header_end]
    # 从 header_end 开始收集类型块: 注释/注解行跟随下一个类型
    blocks = []  # (name, [lines])
    cur_name, cur = None, []
    pending = []  # 注释/注解/空行缓冲
    for l in lines[header_end:]:
        m = TYPE_START.match(l)
        if m:
            if cur_name:
                blocks.append((cur_name, cur + pending))
                pending = []
            else:
                pending = []  # 丢弃 header 后的杂散
            cur_name, cur = m.group(2), [l]
        elif cur_name is not None and (l.startswith("}") or l.strip() == "}" or TYPE_START.match(l) is None):
            if l.strip().startswith(("}", "//", "/*", "*", "*/", "@")) or l.strip() == "" or TYPE_START.match(l):
                # 可能是块尾或下一个类型的注释 -> 简单策略: 遇到 '}' 结尾的独立行归入当前块
                cur.append(l)
            else:
                cur.append(l)
        else:
            if cur_name is None:
                pending.append(l)
            else:
                cur.append(l)
    if cur_name:
        blocks.append((cur_name, cur))
    if len(blocks) <= 1:
        return 0
    dirname = os.path.dirname(path)
    out = 0
    names = set(b[0] for b in blocks)
    for name, body in blocks:
        target = os.path.join(dirname, name + ".java")
        content = "\n".join(header) + "\n" + "\n".join(body).rstrip() + "\n"
        if not os.path.exists(target):
            io.open(target, "w", encoding="utf-8", newline="\n").write(content)
            out += 1
        elif target == path:
            io.open(target, "w", encoding="utf-8", newline="\n").write(content)
    # 原文件: 保留第一个类型对应的文件, 删除其余重复
    if os.path.exists(path) and path != os.path.join(dirname, blocks[0][0] + ".java"):
        os.remove(path)
    return out

total = 0
for service in ["iam-service", "course-service", "teaching-service", "tenant-service"]:
    for root, _, files in os.walk(f"{service}/src/main/java"):
        for f in files:
            if f.endswith(".java"):
                total += split_file(os.path.join(root, f))
print("split files:", total)
