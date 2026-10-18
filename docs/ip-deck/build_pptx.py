# -*- coding: utf-8 -*-
"""IP 蓝图 PPTX 生成器: 融合 IP_STRATEGY_PLAN + IP_SUPPORT_REFACTOR_PLAN
10 页, 16:9, python-pptx 原生矢量(可编辑), 蓝图风配色与 HTML 版一致。
运行: python docs/ip-deck/build_pptx.py → docs/ip-deck/千人千面知识产权蓝图.pptx
"""
import os

from pptx import Presentation
from pptx.util import Inches, Pt, Emu
from pptx.dml.color import RGBColor
from pptx.enum.text import PP_ALIGN, MSO_ANCHOR
from pptx.enum.shapes import MSO_SHAPE

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "千人千面知识产权蓝图.pptx")

# 蓝图配色(与 ip-deck/index.html 一致)
BG = RGBColor(0xF0, 0xEA, 0xE0)
INK = RGBColor(0x1A, 0x1A, 0x1A)
INK2 = RGBColor(0x55, 0x55, 0x55)
INK3 = RGBColor(0xAA, 0xAA, 0xAA)
RUST = RGBColor(0xB5, 0x39, 0x2A)
RUST_SOFT = RGBColor(0xF3, 0xDF, 0xDB)
LINE = RGBColor(0xCE, 0xC8, 0xBE)
WHITE = RGBColor(0xFF, 0xFF, 0xFF)
GREEN = RGBColor(0x55, 0x6B, 0x2F)
BLUE = RGBColor(0x4A, 0x67, 0x85)
BROWN = RGBColor(0x8B, 0x73, 0x55)
TAN = RGBColor(0xE8, 0xA8, 0x7C)
TAN_INK = RGBColor(0x7A, 0x3C, 0x1E)

prs = Presentation()
prs.slide_width = Inches(13.333)
prs.slide_height = Inches(7.5)
BLANK = prs.slide_layouts[6]
SW, SH = prs.slide_width, prs.slide_height


def add_slide() -> "Slide":
    s = prs.slides.add_slide(BLANK)
    r = s.shapes.add_shape(MSO_SHAPE.RECTANGLE, 0, 0, SW, SH)
    r.fill.solid(); r.fill.fore_color.rgb = BG
    r.line.fill.background(); r.shadow.inherit = False
    return s


def txt(slide, x, y, w, h, text, size=18, bold=False, color=INK, align=PP_ALIGN.LEFT,
        font="Inter", line_spacing=1.15, anchor=MSO_ANCHOR.TOP):
    tb = slide.shapes.add_textbox(x, y, w, h)
    tf = tb.text_frame
    tf.word_wrap = True
    tf.vertical_anchor = anchor
    lines = text.split("\n")
    for i, ln in enumerate(lines):
        p = tf.paragraphs[0] if i == 0 else tf.add_paragraph()
        p.text = ln
        p.alignment = align
        p.line_spacing = line_spacing
        for run in p.runs:
            run.font.size = Pt(size)
            run.font.bold = bold
            run.font.color.rgb = color
            run.font.name = font
    return tb


def kicker(slide, text, y=Inches(0.45)):
    txt(slide, Inches(0.8), y, Inches(11), Inches(0.4), text.upper(),
        size=13, bold=True, color=RUST, font="Inter")


def title(slide, text, y=Inches(0.85), size=40):
    txt(slide, Inches(0.8), y, Inches(11.8), Inches(0.9), text,
        size=size, bold=True, color=INK)


def footer(slide, left: str, idx: str):
    txt(slide, Inches(0.8), Inches(7.02), Inches(6), Inches(0.35), left,
        size=11, color=INK3)
    txt(slide, Inches(11.8), Inches(7.02), Inches(0.8), Inches(0.35), idx,
        size=11, color=INK3, align=PP_ALIGN.RIGHT)


def box(slide, x, y, w, h, fill=WHITE, line=INK, line_w=1.5, radius=True):
    shp = slide.shapes.add_shape(
        MSO_SHAPE.ROUNDED_RECTANGLE if radius else MSO_SHAPE.RECTANGLE, x, y, w, h)
    shp.fill.solid(); shp.fill.fore_color.rgb = fill
    if line is None:
        shp.line.fill.background()
    else:
        shp.line.color.rgb = line; shp.line.width = Pt(line_w)
    shp.shadow.inherit = False
    return shp


def card(slide, x, y, w, h, head, body, head_size=15, body_size=11.5,
         fill=WHITE, head_color=INK, body_color=INK2):
    box(slide, x, y, w, h, fill=fill)
    txt(slide, x + Inches(0.2), y + Inches(0.12), w - Inches(0.4), Inches(0.4),
        head, size=head_size, bold=True, color=head_color)
    txt(slide, x + Inches(0.2), y + Inches(0.55), w - Inches(0.4), h - Inches(0.7),
        body, size=body_size, color=body_color, line_spacing=1.2)


def pipeline(slide, y, steps, hero_idx=None, h=Inches(1.5)):
    n = len(steps)
    gap = Inches(0.15)
    w = (SW - Inches(1.6) - gap * (n - 1)) / n
    x = Inches(0.8)
    for i, (num, name, body) in enumerate(steps):
        hero = (i == hero_idx)
        f = RUST if hero else WHITE
        box(slide, x, y, w, h, fill=f, line=RUST if hero else INK,
            line_w=1.5)
        txt(slide, x + Inches(0.12), y + Inches(0.08), w - Inches(0.24), Inches(0.3),
            num, size=10, bold=True, color=RGBColor(0xF0, 0xEA, 0xE0) if hero else RUST)
        txt(slide, x + Inches(0.12), y + Inches(0.36), w - Inches(0.24), Inches(0.4),
            name, size=15, bold=True, color=WHITE if hero else INK)
        txt(slide, x + Inches(0.12), y + Inches(0.76), w - Inches(0.24), h - Inches(0.85),
            body, size=10.5, color=RGBColor(0xF7, 0xE8, 0xE4) if hero else INK2,
            line_spacing=1.15)
        x += w + gap


# ================= 1 封面 =================
s = add_slide()
kicker(s, "重庆市教委重点项目 · 千人千面", y=Inches(0.7))
txt(s, Inches(0.8), Inches(1.1), Inches(8.6), Inches(1.9),
    "个性化智能课程资源系统的\n知识产权蓝图", size=44, bold=True, color=INK)
txt(s, Inches(0.8), Inches(3.05), Inches(8.2), Inches(0.9),
    "世界模型 × 多协议开放层 × 记忆金字塔\n—— 从工程实现到 专利 · 软著 · 论文 · 专著 的完整产出路线",
    size=15, color=INK2)
box(s, Inches(9.6), Inches(1.15), Inches(3.0), Inches(1.75), fill=RUST, line=None)
txt(s, Inches(9.8), Inches(1.3), Inches(2.6), Inches(1.5),
    "KEY INSIGHT\n系统 P0–P4 全部构建闭环\n69 项 E2E 全绿\n10 项创新点完成 IP 评级",
    size=12, bold=True, color=WHITE)
kicker(s, "ROADMAP · 四类产出", y=Inches(4.35))
pipeline(s, Inches(4.8), [
    ("PATENT", "发明专利", "3 项核心 + 1 项储备\n2026-11 交底"),
    ("PAPER", "学术论文", "1 SCI + 1 核心 + 2 EI"),
    ("CORE · P0", "学习世界模型", "参数化动力学模拟器\n无需 GPU · what-if 选教案"),
    ("BOOK", "专著 + 软著", "12 章专著\n4 项软著"),
], hero_idx=2, h=Inches(1.7))
footer(s, "千人千面 IP Blueprint · v1.2 · 2026.10", "1 / 10")

# ================= 2 章节 =================
s = add_slide()
kicker(s, "Chapter One", y=Inches(2.2))
txt(s, Inches(0.8), Inches(2.7), Inches(11.5), Inches(2.2),
    "10 项创新点，谁最值钱？", size=60, bold=True, color=INK)
txt(s, Inches(0.8), Inches(4.9), Inches(10.5), Inches(1.2),
    "不是所有代码都能变成知识产权。我们按 新颖性 × 非显而易见性 × 可专利性 × 发表价值\n给全部资源评了级 —— 两个 P0 双料创新点亮出。",
    size=17, color=INK2)
footer(s, "Section · Chapter 1", "2 / 10")

# ================= 3 评级矩阵 =================
s = add_slide()
kicker(s, "Assessment · 10 Innovations")
title(s, "IP 评级矩阵：两个 P0 双料胜出")
rows = [
    ("① 学习世界模型（参数化动力学 + what-if 选教案）", "★★★★★", "专利核心 + SCI 核心", "P0"),
    ("② 三层覆盖链配置（层级画像+槽位+白名单守卫）", "★★★★", "发明专利", "P0"),
    ("③ 深度交互状态机（Bloom 爬升 + 广度分支）", "★★★★", "专利 + 软著", "P1"),
    ("④ 多协议开放层（MCP + A2A v1.0 + AG-UI）", "★★★☆", "软著 + EI", "P1"),
    ("⑦ 记忆金字塔（L1→L3 + SM-2×Ebbinghaus + ZPD）", "★★★★", "专利 + 核心期刊", "P0"),
    ("⑤⑥⑧⑨⑩ Durable Session/增量同步/生成工作流/技能包/平台", "★★~★★★☆", "软著(并入)/EI", "P1-P3"),
]
ty = Inches(1.75)
col_x = [Inches(0.8), Inches(7.2), Inches(8.9), Inches(11.3)]
col_w = [Inches(6.3), Inches(1.6), Inches(2.3), Inches(1.2)]
heads = ["创新点", "新颖性", "IP 类型", "优先级"]
for cx, cw, hd in zip(col_x, col_w, heads):
    txt(s, cx, ty, cw, Inches(0.35), hd, size=13, bold=True, color=INK)
ln = s.shapes.add_shape(MSO_SHAPE.RECTANGLE, Inches(0.8), ty + Inches(0.38), Inches(11.7), Pt(2))
ln.fill.solid(); ln.fill.fore_color.rgb = INK; ln.line.fill.background(); ln.shadow.inherit = False
ry = ty + Inches(0.5)
for name, stars, iptype, prio in rows:
    p0 = prio == "P0"
    if p0:
        hl = s.shapes.add_shape(MSO_SHAPE.RECTANGLE, Inches(0.75), ry - Inches(0.04), Inches(11.8), Inches(0.52))
        hl.fill.solid(); hl.fill.fore_color.rgb = RUST_SOFT; hl.line.fill.background(); hl.shadow.inherit = False
    txt(s, col_x[0], ry, col_w[0], Inches(0.5), name, size=12, bold=p0, color=INK)
    txt(s, col_x[1], ry, col_w[1], Inches(0.5), stars, size=12, color=RUST if p0 else INK2)
    txt(s, col_x[2], ry, col_w[2], Inches(0.5), iptype, size=12, bold=p0, color=INK)
    txt(s, col_x[3], ry, col_w[3], Inches(0.5), prio, size=13, bold=True, color=RUST if p0 else INK2)
    ry += Inches(0.58)
txt(s, Inches(0.8), ry + Inches(0.15), Inches(11.5), Inches(0.6),
    "■  P0 = 先专利后论文（2026-11 启动交底）    评级维度：新颖性 / 非显而易见性 / 可专利性 / 发表价值",
    size=12, color=INK2)
footer(s, "Content · Matrix", "3 / 10")

# ================= 4 世界模型 =================
s = add_slide()
kicker(s, "P0 · Patent 1 + SCI Paper A")
title(s, "学习世界模型：最轻也最稀缺", size=38)
txt(s, Inches(0.8), Inches(1.6), Inches(11.6), Inches(0.7),
    "把「学生-知识-掌握度」环境建成纯参数化模拟器 —— 生成资源前先推演, 选期望奖励最高的教案。无需 GPU, 可解释、可审计。",
    size=14, color=INK2)
card(s, Inches(0.8), Inches(2.5), Inches(5.7), Inches(3.1),
     "转移函数（实施例核心）",
     "T(s'|s,a):  gain = G × k_a × (1-m) × 前置传递(0.35×Pr)\n"
     "            练习再乘熟练激励 (0.5+0.5m)\n"
     "            遗忘: m × (1-λ)^t,  λ=0.06/日\n"
     "R = gain − 时间成本(0.05×min/20)\n\n"
     "四动作 what-if: explain / exercise / review / skip\n多步推演 simulate(plan, horizon=3) → 教案排序",
     body_size=12, body_color=INK)
box(s, Inches(6.8), Inches(2.5), Inches(5.7), Inches(3.1), fill=RUST_SOFT, line=RUST)
txt(s, Inches(7.0), Inches(2.62), Inches(5.3), Inches(0.4),
    "区别于现有技术（答审要点）", size=15, bold=True, color=RUST)
txt(s, Inches(7.0), Inches(3.1), Inches(5.3), Inches(2.4),
    "三处非显而易见：\n"
    "① 熟练激励项 (0.5+0.5m) 与前置传递系数显式入模\n"
    "② 推演结果直接驱动「资源生成」选教案 —— 不只是推荐排序\n"
    "③ 教育层级画像（职教/本科/研究生）条件化参数分档 + Bloom 天花板一致性守卫（fail-loud）",
    size=12, color=INK, line_spacing=1.3)
txt(s, Inches(0.8), Inches(5.85), Inches(11.6), Inches(0.8),
    "●  实测基线：KT AUC 0.7708；推演单步 <1ms（纯参数化）    论文 A 实验三件套：推演精度 MAE / 消融 / 系统效率（research.py 采集）",
    size=12, color=INK2)
footer(s, "Content · World Model", "4 / 10")

# ================= 5 记忆金字塔 =================
s = add_slide()
kicker(s, "P0 · Patent 2 + 中文核心")
title(s, "记忆金字塔：三级聚合的复习调度", size=38)
pyr = [(Inches(4.9), Inches(1.75), Inches(3.5), Inches(0.95), RUST, "L3 画像", WHITE, 16),
       (Inches(3.4), Inches(2.75), Inches(6.5), Inches(1.0), RGBColor(0xD9, 0x63, 0x4F), "L2 会话摘要 · Bloom 加权掌握度", WHITE, 15),
       (Inches(1.9), Inches(3.8), Inches(9.5), Inches(1.0), TAN, "L1 学习事件流（逐条作答）", TAN_INK, 15)]
for x, y, w, h, f, label, tc, fs in pyr:
    shp = s.shapes.add_shape(MSO_SHAPE.ISOSCELES_TRIANGLE, x, y - Inches(0.55), w, h + Inches(0.6))
    shp.fill.solid(); shp.fill.fore_color.rgb = f; shp.line.fill.background(); shp.shadow.inherit = False
    txt(s, x, y, w, Inches(0.5), label, size=fs, bold=True, color=tc, align=PP_ALIGN.CENTER)
txt(s, Inches(1.9), Inches(4.95), Inches(9.5), Inches(0.4),
    "SM-2 × Ebbinghaus 双模型融合调度 · ZPD 桥接释放（前置≥0.7 且当前∈[0.3,0.7]）· 缺口四分类驱动干预",
    size=13, bold=True, color=TAN_INK, align=PP_ALIGN.CENTER)
card(s, Inches(0.8), Inches(5.5), Inches(5.7), Inches(1.3),
     "区别于 Anki / SuperMemo",
     "单层 SM-2 → 三级聚合 + 双遗忘模型融合 + 缺口四分类(missing/partial/forgotten/misconception)驱动差异化干预。",
     body_size=11)
card(s, Inches(6.8), Inches(5.5), Inches(5.7), Inches(1.3),
     "论文 B 数据底座",
     "KT 全链路 69 项 E2E + L1/L2 真实事件, 经 research.py 脱敏导出(SHA-256, GB/T 35273)。",
     body_size=11)
footer(s, "Content · Memory Pyramid", "5 / 10")

# ================= 6 三协议 =================
s = add_slide()
kicker(s, "P1 · Paper C (EI) + 软著")
title(s, "三大开放协议，一次接全", size=38)
txt(s, Inches(0.8), Inches(1.6), Inches(11.6), Inches(0.7),
    "业界已收敛为三协议分工：MCP 管智能体↔工具, A2A 管智能体↔智能体, AG-UI 管智能体↔前端。本系统三协议全部落地官方传输层。",
    size=14, color=INK2)
pipeline(s, Inches(2.6), [
    ("MCP", "↔ 工具", "streamable-http /mcp\ninitialize · tools/list\ntools/call\n(2026-06-18 协议版)"),
    ("A2A v1.0", "↔ 智能体", "AgentCard 发现\nJSONRPC(必选传输)\nmessage/stream SSE\npush notification\nPulsar 异步队列"),
    ("AG-UI", "↔ 前端", "标准事件流 /agui/runs\nRUN_* · TEXT_MESSAGE_*\n三端统一消费\n(Web/APP/小程序)"),
], hero_idx=1, h=Inches(2.6))
txt(s, Inches(0.8), Inches(5.6), Inches(11.6), Inches(0.9),
    "●  E2E 69 项全绿（含官方传输 8 项专项）      论文 C 指标：发现时延 / 委派成功率 / 事件完整性 / sync-vs-async 吞吐",
    size=12, color=INK2)
footer(s, "Content · Protocols", "6 / 10")

# ================= 7 权利要求树 =================
s = add_slide()
kicker(s, "Patent Drafting · 交底书骨架")
title(s, "权利要求树（专利1 示例）", size=38)


def arrow(s, x1, y1, x2, y2, color=INK, dash=False):
    from pptx.enum.shapes import MSO_CONNECTOR
    c = s.shapes.add_connector(MSO_CONNECTOR.STRAIGHT, x1, y1, x2, y2)
    c.line.color.rgb = color; c.line.width = Pt(1.5)
    if dash:
        from pptx.enum.dml import MSO_LINE_DASH_STYLE
        c.line.dash_style = MSO_LINE_DASH_STYLE.DASH
    ln = c.line._get_or_add_ln()
    from pptx.oxml.ns import qn
    tail = ln.makeelement(qn('a:tailEnd'), {'type': 'triangle', 'w': 'med', 'len': 'med'})
    ln.append(tail)
    return c


box(s, Inches(0.8), Inches(1.6), Inches(5.2), Inches(0.85), fill=RUST, line=None)
txt(s, Inches(1.0), Inches(1.7), Inches(4.9), Inches(0.65),
    "独立权利要求 1（方法）\n基于学习世界模型的资源生成方法", size=13, bold=True, color=WHITE)
steps4 = [("S1 状态空间", "掌握度向量+风格+衰减"),
          ("S2 转移函数", "增益×激励×前置−遗忘"),
          ("S3 推演选教案", "H 步推演+奖励排序"),
          ("S4 审核闭环", "推演差异修正生成")]
sx = Inches(0.8)
sw = Inches(2.75)
for i, (h1, b1) in enumerate(steps4):
    x = sx + i * (sw + Inches(0.13))
    arrow(s, Inches(3.4), Inches(2.45), x + sw / 2, Inches(3.0))
    box(s, x, Inches(3.0), sw, Inches(0.95))
    txt(s, x + Inches(0.1), Inches(3.08), sw - Inches(0.2), Inches(0.35), h1, size=12, bold=True)
    txt(s, x + Inches(0.1), Inches(3.42), sw - Inches(0.2), Inches(0.5), b1, size=10.5, color=INK2)
deps = [("从属1: 层级画像分档参数", "职教/本科/研究生 Bloom 天花板", Inches(0.8)),
        ("从属2: what-if 四动作对比", "explain/exercise/review/skip", Inches(4.0)),
        ("从属3: SM-2 遗忘挽回奖励", "复习动作按挽回量计奖", Inches(7.2))]
for h2, b2, x in deps:
    arrow(s, x + Inches(1.15), Inches(3.95), x + Inches(1.15), Inches(4.4), color=RUST, dash=True)
    box(s, x, Inches(4.4), Inches(2.6), Inches(0.95), fill=RUST_SOFT, line=RUST, line_w=1.2)
    txt(s, x + Inches(0.1), Inches(4.48), Inches(2.4), Inches(0.35), h2, size=11, bold=True, color=RUST)
    txt(s, x + Inches(0.1), Inches(4.82), Inches(2.4), Inches(0.5), b2, size=10, color=RUST)
box(s, Inches(10.2), Inches(1.6), Inches(2.5), Inches(0.85))
txt(s, Inches(10.4), Inches(1.7), Inches(2.2), Inches(0.65),
    "独立权利要求 2（系统）\n世界模型+生成引擎+审核器", size=12, bold=True, color=INK)
footer(s, "Diagram · Claims Tree", "7 / 10")

# ================= 8 时间线 =================
s = add_slide()
kicker(s, "Execution · 2026-2028")
title(s, "四年 IP 产出路线图", size=38)
bars = [
    (Inches(0.9), Inches(1.9), Inches(2.6), RUST, "专利1+2 交底/提交", WHITE),
    (Inches(3.0), Inches(2.35), Inches(3.1), RGBColor(0xD9, 0x63, 0x4F), "专利3 提交 · 论文A投稿(SCI)", WHITE),
    (Inches(0.9), Inches(2.8), Inches(2.3), TAN, "软著 1+2 提交", TAN_INK),
    (Inches(3.2), Inches(2.8), Inches(2.6), TAN, "软著 3+4 · 论文B/C/D", TAN_INK),
    (Inches(5.9), Inches(3.25), Inches(4.2), BROWN, "实验三件套: 合成流量→推演精度→消融", WHITE),
    (Inches(4.7), Inches(3.7), Inches(7.8), GREEN, "专著《人工智能赋能职业教育》12 章: 签约→初稿→出版(2028H1)", WHITE),
    (Inches(0.9), Inches(4.15), Inches(7.0), BLUE, "专利实审 + 论文返修周期（并行推进）", WHITE),
]
for x, y, w, f, label, tc in bars:
    box(s, x, y, w, Inches(0.42), fill=f, line=None, radius=False)
    txt(s, x + Inches(0.1), y + Inches(0.04), w - Inches(0.2), Inches(0.35), label,
        size=11.5, bold=True, color=tc)
marks = [(Inches(0.9), "2026-10"), (Inches(4.6), "2027-06"), (Inches(8.0), "2027-12"), (Inches(12.0), "2028-06")]
for x, label in marks:
    txt(s, x - Inches(0.5), Inches(1.55), Inches(1.2), Inches(0.3), label,
        size=10.5, color=INK2, align=PP_ALIGN.CENTER)
txt(s, Inches(0.8), Inches(5.0), Inches(11.8), Inches(1.4),
    "关键纪律: 专利申请日 → 早于论文任何公开（含预印本）\n"
    "数据合规: 学生数据 SHA-256 脱敏（GB/T 35273）\n"
    "工程证据: 69 项 E2E 全绿, 可复现实验经 research.py 统一采集",
    size=13, color=INK2, line_spacing=1.5)
footer(s, "Timeline · Gantt", "8 / 10")

# ================= 9 就绪度 =================
s = add_slide()
kicker(s, "Engineering Readiness")
title(s, "发表就绪度：工程侧已无欠账", size=38)
box(s, Inches(0.8), Inches(1.7), Inches(5.7), Inches(3.6))
txt(s, Inches(1.0), Inches(1.85), Inches(5.3), Inches(0.35),
    "已具备（W1-W6 无阻塞依赖）", size=14, bold=True, color=INK2)
txt(s, Inches(1.0), Inches(2.35), Inches(5.3), Inches(2.9),
    "✔ 世界模型/金字塔/交互 全代码 + 69 项 E2E\n"
    "✔ research.py: 实验登记+指标快照+脱敏导出\n"
    "✔ 消融开关路径（env, ADR-016）\n"
    "✔ 多端(网页/APP/小程序) AG-UI 一致事件\n"
    "✔ 13 幅 TOGAF 架构图 → 专利附图素材\n"
    "✔ 软著申报书底稿已就绪(v1.2 对齐实测口径)",
    size=13, color=INK, line_spacing=1.8)
box(s, Inches(6.8), Inches(1.7), Inches(5.7), Inches(3.6), fill=RUST_SOFT, line=RUST)
txt(s, Inches(7.0), Inches(1.85), Inches(5.3), Inches(0.35),
    "六周冲刺排期  W1 → W6", size=14, bold=True, color=RUST)
txt(s, Inches(7.0), Inches(2.35), Inches(5.3), Inches(2.9),
    "W1 合成学习流量发生器（200 虚拟学生×8 周）\n"
    "W2 推演精度实验（MAE≤0.05, ρ≥0.7）\n"
    "W3 基线消融开关\n"
    "W4 协议互操作基准\n"
    "W5 专利交底书×3\n"
    "W6 软著材料包×5(含2025版)+申报书修订+论文 A 骨架",
    size=13, color=INK, line_spacing=1.6)
txt(s, Inches(0.8), Inches(5.6), Inches(11.8), Inches(0.9),
    "●  风险对冲: GPU 缺失 → 论文 A 以「轻量可解释」路线本身为贡献      治理: 实验数据一律经采集器, 禁止手工导出原始数据",
    size=12, color=INK2)
footer(s, "Content · Readiness", "9 / 10")

# ================= 10 结尾 =================
s = add_slide()
kicker(s, "END · 千人千面 IP Blueprint", y=Inches(1.8))
txt(s, Inches(0.8), Inches(2.4), Inches(11.7), Inches(2.2),
    "让每一行代码，都成为知识产权", size=54, bold=True, color=INK, align=PP_ALIGN.CENTER)
txt(s, Inches(0.8), Inches(4.8), Inches(11.7), Inches(0.8),
    "详细方案：docs/IP_STRATEGY_PLAN.md  ·  docs/IP_SUPPORT_REFACTOR_PLAN.md",
    size=15, color=INK2, align=PP_ALIGN.CENTER)
footer(s, "Thanks", "10 / 10")

prs.save(OUT)
print("saved:", OUT, f"({os.path.getsize(OUT)//1024} KB, {len(prs.slides.__iter__.__self__._sldIdLst)} slides)")
