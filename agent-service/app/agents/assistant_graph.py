"""页面 AI 助手智能体: 每个前端页面都有 AI 助手(统一经 realtime-gateway 分发到三端)。
按页面上下文路由到不同 agent: 首页推荐 / 课程学习 / 学习路径 / 运维平台。
ReAct 模式: LLM 决定调用工具(检索/学情/图谱) → 综合 → 回答; SSE 流式输出。
"""
import json
from typing import Annotated, Literal, TypedDict

from langgraph.graph import StateGraph, MessagesState
from langgraph.prebuilt import ToolNode, tools_condition

from app.config import build_llm
from app.tools.edu_tools import ALL_TOOLS

# 主模型 + fallback 链(LLM_FALLBACK_MODELS 配置时启用; OpenMAIC 借鉴)
_llm = build_llm(temperature=0.7, streaming=True)

SYSTEM_PROMPT = """你是"千人千面"个性化学习平台的 AI 学习助手。
- 页面上下文: {page_context}
- 学生 ID: {student_id}
- 优先调用工具获取真实数据(检索资源/查学情/查图谱), 不编造数据;
- 回答要简洁、鼓励性、适配职教学生; 涉及操作建议时给出可点击的路径。"""


class AssistantState(MessagesState):
    page_context: str
    student_id: str


def _bind_model(state: AssistantState):
    return _llm.bind_tools(ALL_TOOLS).invoke([
        {"role": "system",
         "content": SYSTEM_PROMPT.format(page_context=state.get("page_context", "dashboard"),
                                         student_id=state.get("student_id", "guest"))},
        *state["messages"],
    ])


def build_assistant_graph() -> StateGraph:
    g = StateGraph(AssistantState)
    g.add_node("assistant", _bind_model)
    g.add_node("tools", ToolNode(ALL_TOOLS))
    g.set_entry_point("assistant")
    g.add_conditional_edges("assistant", tools_condition, {"tools": "tools", "__end__": "__end__"})
    g.add_edge("tools", "assistant")   # 工具结果回喂, ReAct 循环
    return g


assistant_graph = build_assistant_graph().compile()
