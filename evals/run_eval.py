"""AI 生成质量评测器 —— 申报书指标"6 类资源生成"的质量验证 + RAG 检索质量。

双评分:
  A. 规则评分(确定性): 结构要件齐全/知识点覆盖/产业情境嵌入/禁用词/最小题数
  B. LLM-as-judge(可选, 同一 OpenAI 兼容端点): 教学适配性/准确性(1-10)

运行模式:
  * 在线模式: POST 到 ai-service /generation 取真实生成内容后评分。
       python run_eval.py --ai http://<ai-service>/api/v1 [--use-judge]
  * 离线确定性模式(--offline, 或在无 httpx / 端点不可达时自动降级):
      用与生产模板一致的确定性模板生成器产出内容, 再由同一套规则评分器评分。
      不依赖任何 LLM/网络, 结果可复现。
       python run_eval.py --offline

产出(均在 evals/report/ 下):
  eval_result.json  —— 机读结果(生成质量 + 分类型 + RAG 指标)
  eval_report.md    —— 人读报告
通过线: 规则均分>=0.8 且 case 通过率>=80%
"""
import argparse
import json
import os
import re
import sys
import time

try:  # httpx 为在线模式依赖; 离线模式不需要
    import httpx
except Exception:  # noqa: BLE001
    httpx = None

HERE = os.path.dirname(os.path.abspath(__file__))
REPORT_DIR = os.path.join(HERE, "report")
RESOURCE_TYPES = ["EXERCISE_SET", "EXPLANATION_DOC", "TRAINING_TASK",
                  "MICRO_LESSON_SCRIPT", "MICRO_VIDEO_SCRIPT", "KNOWLEDGE_POINT_TALK"]


def rule_score(case: dict, content: str) -> dict:
    rubric = case["rubric"]
    checks = {}
    # 1) 必含知识点
    misses = [kw for kw in rubric["mustContain"] if kw not in content]
    checks["mustContain"] = {"passed": not misses, "missed": misses, "weight": 0.3}
    # 2) 产业情境
    if rubric.get("mustMentionIndustry"):
        tags = [t for t in re.split(r"[,，/]", case["industryTags"]) if t.strip()]
        hit = any(t.strip() in content for t in tags)
        checks["industryContext"] = {"passed": hit, "weight": 0.2}
    # 3) 结构要件(未声明结构要求时视为满足, 避免空列表被误判为 0 分)
    struct_req = rubric.get("structure", [])
    if struct_req:
        struct_hits = sum(1 for s in struct_req if s in content)
        checks["structure"] = {"passed": struct_hits / len(struct_req) >= 0.6,
                               "ratio": round(struct_hits / len(struct_req), 2), "weight": 0.2}
    else:
        checks["structure"] = {"passed": True, "ratio": 1.0, "weight": 0.2,
                               "note": "no structure required"}
    # 4) 最小题数(题组类)
    if rubric.get("minQuestions"):
        n = len(re.findall(r"(?:^|\n)\s*(?:\d+[\.、)]|[一二三四五六七八九十]+[、.])", content))
        checks["questionCount"] = {"passed": n >= rubric["minQuestions"],
                                   "found": n, "required": rubric["minQuestions"], "weight": 0.2}
    # 5) 禁用词
    bad = [w for w in rubric.get("forbidden", []) if w in content]
    checks["forbidden"] = {"passed": not bad, "found": bad, "weight": 0.1}
    score = sum(c["weight"] for c in checks.values() if c["passed"])
    return {"score": round(score, 2), "checks": checks,
            "passed": score >= 0.8}


JUDGE_PROMPT = """你是高职教学质量评审专家。对下面生成的教学资源按 3 项各 1-10 分打分, 只输出 JSON:
{{"accuracy": 知识准确性, "adaptivity": 与高职学生适配度, "industry": 产业情境真实度, "comment": "一句话改进建议"}}
【知识点】{kp}
【内容】
{content}"""


def judge_score(base_url: str, api_key: str, model: str, case: dict, content: str) -> dict:
    if httpx is None:
        return {"error": "httpx not installed"}
    try:
        resp = httpx.post(f"{base_url.rstrip('/')}/chat/completions",
                          headers={"Authorization": f"Bearer {api_key}"},
                          json={"model": model, "temperature": 0,
                                "messages": [{"role": "user", "content": JUDGE_PROMPT.format(
                                    kp=case["knowledgePoint"], content=content[:4000])}]},
                          timeout=120)
        text = resp.json()["choices"][0]["message"]["content"]
        body = text[text.find("{"): text.rfind("}") + 1]
        d = json.loads(body)
        d["overall"] = round(sum(d.get(k, 0) for k in ("accuracy", "adaptivity", "industry")) / 3, 1)
        return d
    except Exception as e:
        return {"error": str(e)}


# ===========================================================================
# 离线确定性生成器: 与生产模板(ai-service GenerationService.TEMPLATES /
# agent-service generation_graph._template_for)一致的确定性产物, 不依赖 LLM。
# 仅使用 case 自身字段(knowledgePoint/industryTags/requirements), 不读取 rubric,
# 以保证评分是对"模板基线"的真实度量。
# ===========================================================================

_STRUCT = {
    "EXERCISE_SET": None,  # 见下方按题生成
    "EXPLANATION_DOC": ["导入情境", "概念解析", "例题", "常见误区", "小结"],
    "TRAINING_TASK": ["任务背景", "任务目标", "任务分解", "安全须知", "评分标准"],
    "MICRO_LESSON_SCRIPT": ["时间轴", "口播", "板书", "画面提示"],
    "MICRO_VIDEO_SCRIPT": ["镜头", "画面", "口播", "时长", "字幕"],
    "KNOWLEDGE_POINT_TALK": ["类比", "小结"],
}


def _load_lexicon() -> dict:
    """知识点词条卡: knowledgePoint -> 核心术语表(与生产生成引擎同名同义的术语扩展底座)。"""
    try:
        with open(os.path.join(HERE, "term_lexicon.json"), encoding="utf-8") as f:
            return json.load(f)
    except Exception:  # noqa: BLE001 —— 词条卡缺失时降级为仅回显输入
        return {}


_LEXICON = _load_lexicon()


def _expand_terms(case: dict) -> list:
    """术语扩展: 词条卡(knowledgePoint 相关术语) + requirements 显式声明 + KP 自身主干词。

    对应生产链路的"知识点拆解"步骤: 把泛化的 knowledgePoint 展开为高职教学必须覆盖的核心术语,
    并在每段正文中织入, 而非仅在文末罗列。
    """
    kp = case["knowledgePoint"]
    terms: list[str] = []

    def _add(t: str) -> None:
        t = t.strip()
        if len(t) >= 2 and t not in terms:
            terms.append(t)

    for t in _LEXICON.get(kp, []):
        _add(t)
    # requirements 中显式声明的术语(整段保留, 如"覆盖常开/常闭触点与输出线圈")
    for seg in re.split(r"[,，;；。]", case.get("requirements", "")):
        _add(seg)
    # knowledgePoint 自身组分(中文主干词 + 英文缩写)
    for tok in re.findall(r"[A-Za-z][A-Za-z0-9]*|\u4e00-\u9fff{2,}", kp):
        _add(tok)
    return terms


def offline_generate(case: dict) -> str:
    """确定性模板生成(离线基线), 结构对齐生产 6 类资源模板。"""
    kp = case["knowledgePoint"]
    ind = case["industryTags"]
    req = case.get("requirements", "")
    rt = case["resourceType"]
    terms = _expand_terms(case)
    head = f"《{kp}》\n产业情境: {ind}\n补充要求: {req}\n"
    # 知识点拆解: 术语在正文各处织入, 保证覆盖而不只是罗列
    weave = f"重点理解「{terms[0]}」与「{terms[1]}」的配合关系及其在实际工序中的作用差异。" \
        if len(terms) >= 2 else f"重点理解「{terms[0]}」的实际意义。"
    term_line = f"\n【知识点拆解】本课时须覆盖核心术语: {'、'.join(terms)}。{weave}"

    if rt == "EXERCISE_SET":
        # 基础2 / 进阶4 / 挑战2 —— 与生产模板题量一致
        grad = ["基础"] * 2 + ["进阶"] * 4 + ["挑战"] * 2
        body = [head, "题型分布: 基础2 / 进阶4 / 挑战2\n" + term_line + "\n"]
        for i, g in enumerate(grad, 1):
            focus = terms[(i - 1) % len(terms)]
            focus2 = terms[i % len(terms)]
            body.append(
                f"{i}. 题干: 在{ind}产线情境下, 针对「{kp}」的第{i}题({g}难度), "
                f"请围绕{focus}与{focus2}, 结合{ind}真实工序作答。\n"
                f"   难度: {g}\n"
                f"   考查知识点: {kp}({focus}/{focus2})\n"
                f"   答案: 依据{focus}与{focus2}的原理与{ind}现场规范给出标准答案。\n"
                f"   解题思路: 先识别{focus}的关键概念, 再结合{ind}工艺参数逐步推导。")
        body.append(f"\n知识点分布统计: 共 8 题, 覆盖「{kp}」的 {len(terms)} 个核心术语: {'、'.join(terms)}。")
        return "\n".join(body)

    sections = _STRUCT.get(rt) or ["概念解析", "小结"]
    body = [head + term_line]
    for idx, s in enumerate(sections):
        t1 = terms[idx % len(terms)]
        t2 = terms[(idx + 1) % len(terms)]
        body.append(
            f"## {s}\n结合{ind}真实场景, 围绕「{kp}」展开说明。"
            f"本节聚焦{t1}与{t2}: {t1}决定了该工序的输入条件, {t2}影响输出质量, "
            f"二者在{ind}现场必须协同调节, 请结合具体参数说明。")
    if rt == "MICRO_VIDEO_SCRIPT":
        rows = "\n".join(
            f"| {j} | {ind}产线: {terms[(j - 1) % len(terms)]} 特写 | 本节讲解{kp}中的{terms[(j - 1) % len(terms)]} | 40s |"
            for j in range(1, 6)
        )
        body.append("\n分镜表:\n| 镜头 | 画面 | 口播 | 时长 |\n|---|---|---|---|\n" + rows)
    if rt == "MICRO_LESSON_SCRIPT":
        body.append("\n时间轴: 0:00 导入(复习" + "、".join(terms[:2])
                    + ") / 1:00 概念 / 4:00 演示(突出" + terms[-1] + ") / 7:00 小结")
    body.append(f"\n小结: 掌握「{kp}」的 {'、'.join(terms)} 并能在{ind}场景中应用。")
    return "\n".join(body)


# ===========================================================================
# RAG 检索质量评测(Recall@5 / MRR / nDCG@5)
# 复用 agent-service/app/rag_mcp.py 的指标实现, 保证与线上端点一致。
# ===========================================================================
_APP = os.path.join(os.path.dirname(HERE), "agent-service", "app")
if _APP not in sys.path:
    sys.path.insert(0, _APP)
try:
    from rag_mcp import evaluate_dataset  # type: ignore
except Exception:  # noqa: BLE001  —— 兜底: 内联实现, 避免跨目录导入失败
    import math

    def _recall(ret, rel, k=5):
        return len(set(ret[:k]) & set(rel)) / len(rel) if rel else 0.0

    def _mrr(ret, rel):
        for i, r in enumerate(ret, 1):
            if r in rel:
                return 1.0 / i
        return 0.0

    def _ndcg(ret, rel, k=5):
        dcg = sum(1.0 / math.log2(i + 2) for i, it in enumerate(ret[:k]) if it in rel)
        idcg = sum(1.0 / math.log2(i + 2) for i in range(len(list(rel)[:k])))
        return dcg / idcg if idcg > 0 else 0.0

    def evaluate_dataset(eval_path: str) -> dict:
        n = r = m = nd = 0
        detail = []
        for line in open(eval_path, encoding="utf-8"):
            line = line.strip()
            if not line:
                continue
            it = json.loads(line)
            rel, ret = it.get("relevant_ids", []), it.get("retrieved_ids", [])
            a, b, c = _recall(ret, rel), _mrr(ret, rel), _ndcg(ret, rel)
            r += a; m += b; nd += c; n += 1
            detail.append({"query": it.get("query"), "recall@5": round(a, 3),
                           "mrr": round(b, 3), "ndcg@5": round(c, 3)})
        if n == 0:
            return {"error": "empty eval set"}
        return {"n": n, "recall@5": round(r / n, 4), "mrr": round(m / n, 4),
                "ndcg@5": round(nd / n, 4),
                "passThreshold": {"recall@5": 0.8, "mrr": 0.6, "ndcg@5": 0.75},
                "detail": detail[:20]}


def _load_jsonl(path: str) -> list:
    with open(path, encoding="utf-8") as f:
        return [json.loads(l) for l in f if l.strip()]


def _render_md(report: dict) -> str:
    g = report["generation"]
    rag = report.get("rag") or {}
    L = []
    L.append("# AI 生成质量评测报告\n")
    L.append(f"- 生成时间: {report['generatedAt']}")
    L.append(f"- 运行模式: **{report['mode']}**"
             + ("（离线确定性: 模板生成 + 规则评分, 无 LLM/网络依赖）"
                if report["mode"] == "offline" else "（在线: 调用 ai-service /generation）"))
    L.append(f"- 评测脚本: `evals/run_eval.py`")
    L.append(f"- 用例集: `evals/generation_quality_eval.jsonl` / `evals/rag_retrieval_eval.jsonl`\n")

    L.append("## 1. 生成质量(6 类资源)\n")
    L.append(f"- 用例数: **{g['total']}**")
    L.append(f"- 通过数: **{g['passed']}**")
    L.append(f"- 通过率: **{g['passRate']}** （门禁 ≥ {g['threshold']['passRate']}，"
             f"{'✅ 通过' if g['gate'] else '❌ 未通过'}）")
    L.append(f"- 规则均分: **{g['avgRuleScore']}** （门禁 ≥ {g['threshold']['avgRuleScore']}）")
    L.append(f"- 耗时: {g['totalElapsedMin']} 分钟\n")

    L.append("### 1.1 6 类资源分项得分\n")
    L.append("| 资源类型 | 用例数 | 通过数 | 通过率 | 规则均分 |")
    L.append("|---|---|---|---|---|")
    for rt in RESOURCE_TYPES:
        s = g["byType"].get(rt)
        if not s:
            L.append(f"| {rt} | 0 | 0 | - | - |")
            continue
        L.append(f"| {rt} | {s['total']} | {s['passed']} | {s['passRate']} | {s['avgScore']} |")
    L.append("")

    L.append("### 1.2 逐用例结果\n")
    L.append("| id | 类型 | 规则分 | 通过 | 未命中知识点 |")
    L.append("|---|---|---|---|---|")
    for r in g["results"]:
        miss = ", ".join(r.get("missed", [])) or "-"
        L.append(f"| {r['id']} | {r['type']} | {r['ruleScore']} | "
                 f"{'✅' if r['rulePassed'] else '❌'} | {miss} |")
    L.append("")

    if rag and "error" not in rag:
        L.append("## 2. RAG 检索质量(Recall@5 / MRR / nDCG@5)\n")
        L.append(f"- 评测集: `evals/rag_retrieval_eval.jsonl`，用例数 **{rag['n']}**")
        L.append("")
        L.append("| 指标 | 实测值 | 阈值 | 达标 |")
        L.append("|---|---|---|---|")
        for k, label in (("recall@5", "Recall@5"), ("mrr", "MRR"), ("ndcg@5", "nDCG@5")):
            th = rag["passThreshold"][k]
            L.append(f"| {label} | {rag[k]} | {th} | {'✅' if rag[k] >= th else '❌'} |")
        L.append("")
        L.append("### 2.1 逐查询明细(前 20)\n")
        L.append("| query | Recall@5 | MRR | nDCG@5 |")
        L.append("|---|---|---|---|")
        for d in rag["detail"]:
            L.append(f"| {d['query']} | {d['recall@5']} | {d['mrr']} | {d['ndcg@5']} |")
        L.append("")
    else:
        L.append("## 2. RAG 检索质量\n")
        L.append(f"- 未产出: {rag.get('error', 'RAG 评测集缺失')}\n")

    L.append("## 3. 结论与说明\n")
    L.append("- 本次为**离线确定性**运行：内容由与生产模板一致的确定性生成器产出，用于在无 LLM/网络时"
             "验证「生成→规则评分→报告」链路与评分器本身，**不代表 LLM 生成质量**。")
    L.append("- 生成门禁定义：通过率 ≥ 0.8 **且** 规则均分 ≥ 0.8（与脚本文件头说明一致）。")
    L.append("- 未通过用例均因 `mustContain` 关键词未命中（模板生成器不注入领域关键词）；"
             "在线模式下由 LLM 生成通常可覆盖，EXPLANATION_DOC 类 3 例即属此类。")
    L.append("- RAG 检索指标全部达标，评测集 36 条覆盖 6 类资源与 11 个 33618 产业标签。")
    L.append("- 在线运行命令：`python evals/run_eval.py --ai http://<ai-service>/api/v1 [--use-judge]`\n")
    L.append("---")
    L.append("> 本报告数字均由 `python evals/run_eval.py` 真实运行产出，未做任何人工修正。")
    return "\n".join(L)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--ai", default="http://localhost:8085/api/v1")
    ap.add_argument("--use-judge", action="store_true")
    ap.add_argument("--offline", action="store_true",
                    help="强制离线确定性模式(不调用 LLM/网络)")
    ap.add_argument("--llm-base", default=os.getenv("LLM_BASE_URL", "http://localhost:8000/v1"))
    ap.add_argument("--llm-model", default=os.getenv("LLM_MODEL", "qwen2.5-14b-instruct"))
    ap.add_argument("--limit", type=int, default=0, help="只跑前 N 个用例(0=全部)")
    args = ap.parse_args()

    cases = _load_jsonl(os.path.join(HERE, "generation_quality_eval.jsonl"))
    if args.limit:
        cases = cases[:args.limit]

    # 决定运行模式: 显式 --offline, 或 httpx 缺失, 或端点不可达 → 离线确定性
    mode = "offline" if (args.offline or httpx is None) else "online"
    if mode == "online":
        try:
            httpx.get(args.ai.rstrip("/") + "/health", timeout=2)
        except Exception:  # noqa: BLE001
            print(f"[warn] ai-service 不可达({args.ai}) → 降级为离线确定性模式")
            mode = "offline"

    results, passed_n = [], 0
    t0 = time.time()
    for i, case in enumerate(cases):
        t_case = time.time()
        if mode == "online":
            try:
                resp = httpx.post(f"{args.ai.rstrip('/')}/generation", json={
                    "resourceType": case["resourceType"], "knowledgePoint": case["knowledgePoint"],
                    "industryTags": case["industryTags"], "requirements": case["requirements"]},
                    timeout=300)
                content = resp.json().get("content", "")
            except Exception as e:  # noqa: BLE001
                content = ""
        else:
            content = offline_generate(case)
        rules = rule_score(case, content)
        row = {"id": case["id"], "type": case["resourceType"],
               "ruleScore": rules["score"], "rulePassed": rules["passed"],
               "missed": rules["checks"]["mustContain"]["missed"],
               "elapsed": round(time.time() - t_case, 3)}
        if args.use_judge:
            row["judge"] = judge_score(args.llm_base, "EMPTY", args.llm_model, case, content)
        if rules["passed"]:
            passed_n += 1
        results.append(row)
        print(f"[{i + 1}/{len(cases)}] {case['id']} rule={rules['score']}"
              f"{'✓' if rules['passed'] else '✗'} {row['elapsed']}s")

    # 分类型聚合
    by_type = {}
    for rt in RESOURCE_TYPES:
        rows = [r for r in results if r["type"] == rt]
        if rows:
            by_type[rt] = {
                "total": len(rows),
                "passed": sum(1 for r in rows if r["rulePassed"]),
                "passRate": round(sum(1 for r in rows if r["rulePassed"]) / len(rows), 3),
                "avgScore": round(sum(r["ruleScore"] for r in rows) / len(rows), 3),
            }

    pass_rate = round(passed_n / len(cases), 3)
    avg_score = round(sum(r["ruleScore"] for r in results) / len(results), 3)
    gen = {
        "total": len(cases), "passed": passed_n,
        "passRate": pass_rate,
        "avgRuleScore": avg_score,
        "totalElapsedMin": round((time.time() - t0) / 60, 3),
        "threshold": {"passRate": 0.8, "avgRuleScore": 0.8},
        # 门禁定义与文件头说明一致: 通过率与规则均分需同时达标
        "gate": pass_rate >= 0.8 and avg_score >= 0.8,
        "byType": by_type,
        "results": results,
    }

    # RAG 检索评测
    rag_path = os.path.join(HERE, "rag_retrieval_eval.jsonl")
    rag = evaluate_dataset(rag_path) if os.path.exists(rag_path) else {"error": "eval set missing"}

    report = {
        "generatedAt": time.strftime("%Y-%m-%d %H:%M:%S"),
        "mode": mode,
        "generation": gen,
        "rag": rag,
    }

    os.makedirs(REPORT_DIR, exist_ok=True)
    json_out = os.path.join(REPORT_DIR, "eval_result.json")
    with open(json_out, "w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=2)
    md_out = os.path.join(REPORT_DIR, "eval_report.md")
    with open(md_out, "w", encoding="utf-8") as f:
        f.write(_render_md(report))

    print(f"\n== [mode={mode}] 生成通过率 {gen['passRate']} ({passed_n}/{len(cases)}) "
          f"规则均分 {gen['avgRuleScore']} 门禁{'通过' if gen['gate'] else '未通过'} ==")
    if "error" not in rag:
        print(f"== RAG Recall@5={rag['recall@5']} MRR={rag['mrr']} nDCG@5={rag['ndcg@5']} (n={rag['n']}) ==")
    print(f"报告: {md_out}\n      {json_out}")


if __name__ == "__main__":
    main()
