"""资源生成工作流(LangGraph 状态机):
意图解析 → 检索(RAG+KG) → 生成 → 质量审核 → (不通过→重写, 最多2轮) → 发布
对应申报书创新点三: LLM+RAG 以 "33618" 产业知识图谱为底座的 6 类资源生成。
"""
import operator
from typing import Annotated, Literal, TypedDict

from langchain_core.messages import SystemMessage, HumanMessage
from langgraph.graph import StateGraph, END

from app.config import build_llm
from app.tools.edu_tools import search_course_resources, query_knowledge_graph

RESOURCE_TYPES = {
    "EXERCISE_SET": "差异化练习题组",
    "EXPLANATION_DOC": "个性化讲解文档",
    "TRAINING_TASK": "实训任务书",
    "MICRO_LESSON_SCRIPT": "微课脚本",
    "MICRO_VIDEO_SCRIPT": "微课视频脚本",
    "KNOWLEDGE_POINT_TALK": "知识点讲解",
}


class GenState(TypedDict):
    resource_type: str            # RESOURCE_TYPES key
    student_id: str
    course_id: str
    knowledge_point: str
    requirements: str
    pedagogy: str                 # 教育层级 id(vocational|research-undergrad|graduate), 可空
    pedagogy_profile: dict        # resolve() 后的画像(parse_intent 填充)
    intent: dict
    retrieved_context: Annotated[list, operator.add]  # 累积检索上下文
    drafts: Annotated[list, operator.add]
    review: dict                  # {"passed": bool, "score": int, "issues": [..]}
    revision_round: int
    final_content: str
    messages: Annotated[list, operator.add]


# 主模型 + fallback 链(LLM_FALLBACK_MODELS 配置时启用); 快速失败→模板降级语义保留
_llm = build_llm(temperature=0.3, streaming=False,
                 timeout=60, max_retries=0)


def parse_intent(state: GenState) -> dict:
    """节点1: 意图与约束解析(题型分布/难度梯度/产业场景标签)
    教育层级画像(OpenMAIC 吸收3): profile 决定 Bloom 天花板/受众/交互设计指令"""
    from app import pedagogy
    profile = state.get("pedagogy_profile") or pedagogy.resolve(
        state.get("pedagogy"), state.get("course_id", ""))
    directives = pedagogy.generation_directives(profile)
    prompt = f"""解析资源生成意图并输出 JSON。资源类型: {RESOURCE_TYPES.get(state["resource_type"], state["resource_type"])}。
教育层级约束(必须遵守):
{directives}
知识点: {state['knowledge_point']}; 用户要求: {state['requirements']}
输出 JSON 字段: audience_level(取值 beginner/intermediate/advanced), difficulty_gradient(题组时为难度数组),
bloom_target(不超过该档天花板 {profile['bloomCeiling']}),
industry_tags(33618 产业场景标签数组, 如 智能网联新能源汽车/新一代电子信息制造), constraints(其他约束)。"""
    try:
        resp = _llm.invoke([SystemMessage(content="你是教学资源规划专家, 只输出 JSON。"),
                            HumanMessage(content=prompt)])
    except Exception:
        resp = None  # LLM 不可用/超时 → 模板降级, 端点不 500
    import json
    try:
        body = resp.content if resp is not None else ""
        intent = json.loads(body[body.find("{"): body.rfind("}") + 1])
    except Exception:
        intent = {"audience_level": "intermediate", "industry_tags": [], "constraints": []}
    intent["bloom_target"] = intent.get("bloom_target") or profile["bloomCeiling"]
    return {"intent": intent, "messages": [("intent_parsed",)],
            "pedagogy_profile": profile}


def retrieve_context(state: GenState) -> dict:
    """节点2: 并行 RAG 检索 + 知识图谱先验(产业情境深度嵌入)"""
    context = []
    hits = search_course_resources.invoke({
        "query": f"{state['knowledge_point']} {state['intent'].get('industry_tags', [])}",
        "top_k": 8,
    })
    context.extend({"source": "vector", "doc": h} for h in hits)
    try:
        kg = query_knowledge_graph.invoke({"entity": state["knowledge_point"], "relation_hops": 1})
        context.append({"source": "kg", "doc": kg})
    except Exception:
        context.append({"source": "kg", "doc": {}})
    return {"retrieved_context": context, "messages": [("retrieved", len(context))]}


def generate(state: GenState) -> dict:
    """节点3: 依据类型模板生成资源草稿"""
    tpl = _template_for(state["resource_type"], state.get("pedagogy_profile"))
    ctx_text = "\n".join(str(c.get("doc"))[:800] for c in state["retrieved_context"][-6:])
    review = state.get("review") or {}
    revise_hint = ""
    if review and not review.get("passed", True):
        revise_hint = f"\n上一稿存在问题, 请针对性修改: {review.get('issues')}"
    prompt = tpl.format(
        kp=state["knowledge_point"], intent=state["intent"],
        audience=state["intent"].get("audience_level", "intermediate"),
        industry=state["intent"].get("industry_tags", []), context=ctx_text,
        requirements=state["requirements"], revise=revise_hint)
    try:
        resp = _llm.invoke([SystemMessage(content="你是职业教育教学资源生成引擎, 内容须深度嵌入真实产业场景。"),
                            HumanMessage(content=prompt)])
        draft = resp.content
    except Exception:
        draft = ""  # LLM 不可用 → 空稿走 review 不通过 → publish 保底
    return {"drafts": [draft], "messages": [("generated",)]}


def review(state: GenState) -> dict:
    """节点4: 教学质量审核 agent(事实性/难度适配/产业情境/敏感内容)"""
    draft = state["drafts"][-1]
    prompt = f"""作为教学质量审核专家, 按 4 维度打分(0-100):
1 知识准确性 2 与学习者水平适配({state['intent'].get('audience_level')}) 3 产业情境真实度 4 内容安全性。
输出 JSON: {{"score": 总分, "passed": 是否>=80, "issues": [未达标项及修改建议]}}
待审内容:\n{draft[:3000]}"""
    try:
        resp = _llm.invoke([SystemMessage(content="你是严格的教学质量审核官, 只输出 JSON。"),
                            HumanMessage(content=prompt)])
        body = resp.content
    except Exception:
        body = ""
    import json
    try:
        review = json.loads(body[body.find("{"): body.rfind("}") + 1])
        review.setdefault("passed", review.get("score", 0) >= 80)
    except Exception:
        review = {"score": 0, "passed": False, "issues": ["审核输出解析失败"]}
    return {"review": review, "messages": [("reviewed", review.get("score"))]}


def route_after_review(state: GenState) -> Literal["revise", "publish"]:
    review = state.get("review", {})
    rounds = state.get("revision_round", 0)
    if not review.get("passed", False) and rounds < 2:
        return "revise"
    return "publish"


def revise(state: GenState) -> dict:
    """重写一轮后回到生成节点"""
    return {"revision_round": state.get("revision_round", 0) + 1}


def publish(state: GenState) -> dict:
    return {"final_content": state["drafts"][-1], "messages": [("published",)]}


def _template_for(resource_type: str, profile: dict | None = None) -> str:
    from app import pedagogy
    directives = pedagogy.generation_directives(profile) if profile else ""
    base = """生成《{kp}》教学资源。
- 受众水平: {audience}; 产业场景: {industry}
- 教育层级约束: """ + (directives or "默认职教档") + """
- 检索到的参考资料: {context}
- 用户要求: {requirements}{revise}
"""
    tail = {
        "EXERCISE_SET": "输出: 8 道题(基础2/进阶4/挑战2), 每题含题干/选项或作答要求/答案/考查知识点/解题思路。",
        "EXPLANATION_DOC": "输出: Markdown 讲解文档, 含导入情境、概念解析、例题、常见误区、小结。",
        "TRAINING_TASK": "输出: 实训任务书, 含任务背景(真实产线场景)、目标、任务分解、安全须知、评分标准。",
        "MICRO_LESSON_SCRIPT": "输出: 8 分钟微课逐字脚本, 含时间轴、口播、板书/画面提示。",
        "MICRO_VIDEO_SCRIPT": "输出: 视频分镜脚本表(镜头/画面/口播/时长)。",
        "KNOWLEDGE_POINT_TALK": "输出: 面向学生的口语化知识点讲解, 用类比帮助理解。",
    }
    return base + tail.get(resource_type, tail["KNOWLEDGE_POINT_TALK"]) + "\n要求: {requirements}"


def build_generation_graph() -> StateGraph:
    g = StateGraph(GenState)
    g.add_node("parse_intent", parse_intent)
    g.add_node("retrieve", retrieve_context)
    g.add_node("generate", generate)
    g.add_node("review", review)
    g.add_node("revise", revise)
    g.add_node("publish", publish)
    g.set_entry_point("parse_intent")
    g.add_edge("parse_intent", "retrieve")
    g.add_edge("retrieve", "generate")
    g.add_edge("generate", "review")
    g.add_conditional_edges("review", route_after_review,
                            {"revise": "revise", "publish": "publish"})
    # revise 后重新 generate(带上审核意见)
    g.add_edge("revise", "generate")
    g.add_edge("publish", END)
    return g


generation_graph = build_generation_graph().compile()
