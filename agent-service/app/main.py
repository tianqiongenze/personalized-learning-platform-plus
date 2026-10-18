"""智能体服务(Litestar + Granian 全异步高吞吐; LangGraph 编排保持不变)
  POST /api/v1/agent/generation          6 类资源生成工作流(LangGraph 状态机)
  POST /api/v1/agent/assistant/stream    页面 AI 助手 SSE 流式(ReAct + 工具调用)
  GET  /health /metrics
"""
import json
from collections.abc import AsyncGenerator

from litestar import Litestar, get, post
from litestar.datastructures import State
from litestar.response import Response, Stream
import json
from collections.abc import AsyncGenerator

from pydantic import BaseModel

from app.agents.generation_graph import generation_graph
from app.agents.assistant_graph import assistant_graph
from app import config
from app.slots import SlotConfig, SlotResolutionError
from app.skills import SkillRegistry


class GenerationRequest(BaseModel):
    resourceType: str
    studentId: str = "guest"
    courseId: str = ""
    knowledgePoint: str
    requirements: str = ""
    pedagogy: str = ""             # 教育层级: vocational|research-undergrad|graduate(空=课程绑定/默认)
    pedagogyOverride: dict = {}    # 请求级白名单覆盖(bloomCeiling/interaction/scaffolding/workflow)


class AssistantRequest(BaseModel):
    message: str
    studentId: str = "guest"
    pageContext: str = "dashboard"


@get("/health")
async def health(state: State) -> dict:
    return {"status": "UP", "service": "agent-service", "llm": config.LLM_MODEL}


# ========== OpenMAIC 借鉴: Skills 动态加载 + 课程级模型槽位 ==========

@get("/api/v1/skills")
async def skills_list(state: State) -> dict:
    """技能清单(SkillRegistry 热加载, ConfigMap 更新 30s 内自动生效)"""
    reg: SkillRegistry = state["skills"]
    return {"count": len(reg.list_skills()), "skills": reg.list_skills()}


@get("/api/v1/skills/{skill_name:str}/prompt")
async def skill_prompt(skill_name: str, state: State, references: str = "") -> dict:
    """渲染技能注入提示词(可选 references 懒加载)"""
    reg: SkillRegistry = state["skills"]
    refs = [r for r in references.split(",") if r]
    prompt = reg.get_prompt(skill_name, refs)
    if prompt is None:
        return {"error": f"skill not found: {skill_name}", "available": [s["name"] for s in reg.list_skills()]}
    return {"skill": skill_name, "prompt": prompt}


@get("/api/v1/slots")
async def slots_info(state: State, courseId: str = "") -> dict:
    """解析后的模型槽位(课程覆盖链: global → course)"""
    sc: SlotConfig = state["slots"]
    slots = {}
    for s in ("llm", "course.outline", "exercise", "grading", "rag.rewrite", "tts", "workflow"):
        slots[s] = sc.resolve(s, courseId or None)
    return {"courseId": courseId, "resolved": slots,
            "overlay": sc.course_overlay(courseId) if courseId else {}}


@post("/api/v1/slots/reload")
async def slots_reload(state: State) -> dict:
    sc: SlotConfig = state["slots"]
    try:
        sc.reload()
    except SlotResolutionError as e:
        return {"ok": False, "errors": str(e)}
    skills: SkillRegistry = state["skills"]
    n = skills.reload()
    return {"ok": True, "skillsLoaded": n}


# ========== OpenMAIC 借鉴 3: 教育层级动态切换 + 深度交互引擎 ==========

@get("/api/v1/pedagogy/profiles")
async def pedagogy_profiles() -> dict:
    """可用教育层级清单(职教/研究型本科/研究生 + profiles/*.yml 扩展档)"""
    from app import pedagogy
    return {"count": 0, "profiles": pedagogy.list_profiles()}


@get("/api/v1/pedagogy/resolve")
async def pedagogy_resolve(profileId: str = "", courseId: str = "",
                           bloomCeiling: str = "", workflow: str = "") -> dict:
    """解析生效画像(覆盖链: 默认→档 yml→课程 pedagogy→请求参数, fail-loud)"""
    from app import pedagogy
    try:
        ov = {}
        if bloomCeiling:
            ov["bloomCeiling"] = bloomCeiling
        if workflow:
            ov["workflow"] = workflow
        p = pedagogy.resolve(profileId or None, courseId, ov)
        return {"ok": True, "resolved": p}
    except pedagogy.PedagogyError as e:
        return {"ok": False, "error": str(e)}


@post("/api/v1/interaction/start")
async def interaction_start(data: dict) -> dict:
    """开启深度交互会话: {profileId?, courseId?, studentId?, knowledgePoint, seedMastery?}"""
    from app import pedagogy, interaction
    try:
        profile = pedagogy.resolve(data.get("profileId") or None,
                                   data.get("courseId", ""),
                                   data.get("pedagogyOverride") or {})
    except pedagogy.PedagogyError as e:
        return {"ok": False, "error": str(e)}
    sess = interaction.start(profile, data.get("knowledgePoint", "知识点"),
                             data.get("courseId", ""), data.get("studentId", "guest"),
                             float(data.get("seedMastery", 0.5)))
    first = interaction.next_turn(sess)
    return {"ok": True, "session": sess, "question": first}


@post("/api/v1/interaction/answer")
async def interaction_answer(data: dict) -> dict:
    """提交作答并推进状态机: {sessionId, correct(bool), studentText?}"""
    from app import interaction
    try:
        sess = interaction.get(data.get("sessionId", ""))
        result = interaction.answer(sess, data.get("correct"), data.get("studentText", ""))
    except interaction.InteractionError as e:
        return {"ok": False, "error": str(e)}
    out = {"ok": True, "result": result}
    if not result.get("finished"):
        out["question"] = interaction.next_turn(sess)
    return out


@get("/api/v1/interaction/{session_id:str}/summary")
async def interaction_summary(session_id: str) -> dict:
    """深度/广度足迹与掌握度报告"""
    from app import interaction
    try:
        return {"ok": True, "summary": interaction.summary(interaction.get(session_id))}
    except interaction.InteractionError as e:
        return {"ok": False, "error": str(e)}


# ========== 世界模型(学习环境模拟器) ==========

@post("/api/v1/world/simulate")
async def world_simulate(data: dict) -> dict:
    """多步推演: {masteries?, plan:[{action, knowledgePoint, minutes?, prereqs?}], horizon?}"""
    from app import world_model as wm
    try:
        st = wm.init_state(data.get("masteries"), data.get("learningStyle", "unknown"))
        return {"ok": True, "result": wm.simulate(st, data.get("plan", []),
                                                  int(data.get("horizon", 3)))}
    except wm.WorldModelError as e:
        return {"ok": False, "error": str(e)}


@post("/api/v1/world/what_if")
async def world_what_if(data: dict) -> dict:
    """反事实对比(4 动作): {masteries?, knowledgePoint, prereqs?}"""
    from app import world_model as wm
    kp = data.get("knowledgePoint", "")
    if not kp:
        return {"ok": False, "error": "knowledgePoint 必填"}
    st = wm.init_state(data.get("masteries"))
    return {"ok": True, "result": wm.what_if(st, kp, data.get("prereqs"))}


@post("/api/v1/world/recommend_next")
async def world_recommend(data: dict) -> dict:
    """动作选择: {masteries?, candidates:[kp], prereqMap?} → 期望增益最大的知识点+动作"""
    from app import world_model as wm
    cands = data.get("candidates", [])
    if not cands:
        return {"ok": False, "error": "candidates 必填"}
    st = wm.init_state(data.get("masteries"))
    return {"ok": True, "result": wm.recommend_next(st, cands, data.get("prereqMap"))}


# ========== A2A 协议(agent↔agent 开放) ==========

@get("/.well-known/agent-card.json")
async def a2a_agent_card() -> dict:
    """A2A v1.0 Agent Card — 能力发现"""
    from app import a2a
    return a2a.agent_card()


@post("/a2a/v1/tasks")
async def a2a_send(data: dict, mode: str = "sync") -> dict:
    """message/send: 外部智能体委派任务; mode=async → Pulsar/本地队列异步执行"""
    from app import a2a
    task = a2a.send_task(data)
    if task.get("error"):
        return task
    if mode == "async":
        from app import a2a_async
        r = await a2a_async.enqueue(task)
        return {**task, "queue": r}
    return a2a.execute_task(task)


@get("/a2a/v1/tasks/{task_id:str}")
async def a2a_get(task_id: str) -> dict:
    from app import a2a
    t = a2a.get_task(task_id)
    return t or {"error": f"task '{task_id}' 不存在"}


# ========== AG-UI 协议(agent↔前端 标准事件流) ==========

@post("/agui/runs", media_type="text/event-stream")
async def agui_runs(data: dict) -> Stream:
    """AG-UI 标准事件流(RUN_*/TEXT_MESSAGE_*), 供三端前端统一消费"""
    from app import agui

    async def gen() -> AsyncGenerator[bytes, None]:
        for ev in agui.run_events(data):
            yield agui.sse_format(ev).encode("utf-8")

    return Stream(gen(), media_type="text/event-stream")


# ========== P3: Durable Session(租约/心跳/续跑) + 修订计数器增量同步 ==========

@post("/api/v1/durable/session")
async def durable_create(data: dict) -> dict:
    """创建持久会话: {kind, state?} → {sessionId, persisted}"""
    from app.durable import DurableSession
    s = DurableSession(data.get("kind", "generic"), data.get("state", {}))
    rev = s.save()
    return {"ok": True, "sessionId": s.session_id, "persisted": bool(rev)}


@post("/api/v1/durable/session/{session_id:str}/heartbeat")
async def durable_heartbeat(session_id: str, data: dict) -> dict:
    """续租(会话归属副本调用)"""
    from app.durable import DurableSession, LeaseLost
    try:
        s = DurableSession.resume(data.get("kind", "generic"), session_id)
        s.heartbeat()
        return {"ok": True, "leased": True}
    except LeaseLost as e:
        return {"ok": False, "error": str(e)}


@post("/api/v1/durable/session/{session_id:str}/resume")
async def durable_resume(session_id: str, data: dict) -> dict:
    """恢复/接管会话: {kind, takeover?} → checkpoint 状态"""
    from app.durable import DurableSession, LeaseLost
    try:
        s = DurableSession.resume(data.get("kind", "generic"), session_id,
                                  bool(data.get("takeover")))
        return {"ok": True, "state": s.state, "inbox": s.inbox}
    except LeaseLost as e:
        return {"ok": False, "error": str(e)}


@post("/api/v1/durable/session/{session_id:str}/instruction")
async def durable_instruction(session_id: str, data: dict) -> dict:
    """运行中追加指令(排队)"""
    from app.durable import DurableSession, LeaseLost
    try:
        s = DurableSession.resume(data.get("kind", "generic"), session_id)
        s.push_instruction(data.get("instruction", {}))
        return {"ok": True, "inboxLen": len(s.inbox)}
    except LeaseLost as e:
        return {"ok": False, "error": str(e)}


@get("/sync/updates")
async def sync_updates(since: int = 0, kinds: str = "") -> dict:
    """修订计数器增量同步: ?since=<rev>&kinds=a,b"""
    from app import sync
    ks = [k for k in kinds.split(",") if k] or None
    return sync.updates(since, ks)


@post("/sync/bump")
async def sync_bump(data: dict) -> dict:
    """内部: 实体变更 bump revision {kind, entityId, payload}"""
    from app import sync
    rev = sync.bump(data.get("kind", ""), data.get("entityId", ""),
                    data.get("payload", {}))
    return {"ok": True, "revision": rev}


# ========== 科研数据采集(论文/专利可复现实验) ==========

@post("/api/v1/research/experiments")
async def research_record(data: dict) -> dict:
    """登记实验: {tag, condition:{}, metrics:{}} → expId"""
    from app import research
    if not data.get("tag"):
        return {"ok": False, "error": "tag 必填"}
    exp = research.record_experiment(data["tag"], data.get("condition", {}),
                                     data.get("metrics", {}))
    return {"ok": True, "experiment": exp}


@get("/api/v1/research/experiments")
async def research_list(tag: str = "") -> dict:
    from app import research
    return {"ok": True, "count": 0, "experiments": research.list_experiments(tag)}


@post("/api/v1/research/datasets/export")
async def research_export(data: dict) -> dict:
    """导出脱敏数据集: {kind: learning_events|interactions, limit?}"""
    from app import research
    r = research.export_dataset(data.get("kind", "learning_events"),
                                int(data.get("limit", 500)))
    return {"ok": True, **r}


# ========== P4 后续: A2A JSONRPC(官方必选传输) + MCP streamable HTTP ==========

@post("/a2a/rpc")
async def a2a_rpc(data: dict) -> dict:
    """A2A JSON-RPC 2.0: message/send | tasks/get"""
    from app import a2a_rpc
    return a2a_rpc.handle_rpc(data)


@post("/a2a/rpc/stream", media_type="text/event-stream")
async def a2a_rpc_stream(data: dict) -> Stream:
    """A2A message/stream → SSE(status-update / artifact-update)"""
    from app import a2a_rpc

    async def gen() -> AsyncGenerator[bytes, None]:
        for chunk in a2a_rpc.stream_rpc(data):
            yield chunk.encode("utf-8")

    return Stream(gen(), media_type="text/event-stream")


@post("/mcp")
async def mcp_streamable(data: dict) -> dict:
    """MCP streamable-http: initialize | tools/list | tools/call | ping"""
    from app import mcp_http
    svc_bases = {"kt": config.KT_BASE, "course": config.COURSE_BASE, "agent": ""}
    return mcp_http.handle_mcp(data, svc_bases)


@get("/metrics")
async def metrics() -> Response:
    from prometheus_client import CONTENT_TYPE_LATEST, generate_latest
    return Response(generate_latest(), media_type=CONTENT_TYPE_LATEST)


@post("/api/v1/agent/generation")
async def generate(data: GenerationRequest) -> dict:
    state = {
        "resource_type": data.resourceType,
        "student_id": data.studentId,
        "course_id": data.courseId,
        "knowledge_point": data.knowledgePoint,
        "requirements": data.requirements,
        "pedagogy": data.pedagogy,
        "retrieved_context": [], "drafts": [], "messages": [],
        "revision_round": 0,
    }
    final = await generation_graph.ainvoke(state)
    prof = final.get("pedagogy_profile") or {}
    return {
        "content": final.get("final_content", ""),
        "review": final.get("review", {}),
        "revisionRounds": final.get("revision_round", 0),
        "resourceType": data.resourceType,
        "pedagogy": {"profileId": prof.get("id"), "bloomCeiling": prof.get("bloomCeiling"),
                     "chain": prof.get("_resolvedChain", [])},
    }


@post("/api/v1/agent/assistant/stream", media_type="text/event-stream")
async def assistant_stream(data: AssistantRequest) -> Stream:
    async def gen() -> AsyncGenerator[bytes, None]:
        inputs = {
            "messages": [("user", data.message)],
            "page_context": data.pageContext,
            "student_id": data.studentId,
        }
        try:
            async for chunk, _meta in assistant_graph.astream(inputs, stream_mode="messages"):
                content = getattr(chunk, "content", "")
                if content:
                    payload = json.dumps({"type": "token", "content": content}, ensure_ascii=False)
                    yield f"data: {payload}\n\n".encode()
        except Exception as e:  # 工具失败不中断流, 下发 error 帧由前端提示
            payload = json.dumps({"type": "error", "content": str(e)}, ensure_ascii=False)
            yield f"data: {payload}\n\n".encode()
        yield b"data: [DONE]\n\n"

    return Stream(gen(), headers={
        "Cache-Control": "no-cache",
        "X-Accel-Buffering": "no",
    })


# ========== P2: RAG 质量评测 + MCP Server ==========

@get("/api/v1/rag/eval")
async def rag_eval() -> dict:
    """RAG 检索质量指标(Recall@5/MRR/nDCG@5) — 评测集在镜像内 evals/ 目录"""
    from app.rag_mcp import evaluate_dataset
    import os
    path = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                        "..", "evals", "generation_quality_eval.jsonl")
    if not os.path.exists(path):
        return {"status": "NO_EVAL_SET", "path": path}
    return evaluate_dataset(path)


@get("/api/v1/mcp/tools")
async def mcp_tools() -> dict:
    """MCP 工具清单 — 任意 MCP Client 可发现平台 KT/课程/生成能力"""
    from app.rag_mcp import MCP_TOOLS
    return {"protocol": "mcp/1.0", "server": "qianrenqianmian-agent", "tools": MCP_TOOLS}


@post("/api/v1/mcp/call")
async def mcp_call(data: dict) -> dict:
    """MCP 工具同步调用: {tool: name, args: {...}}"""
    from app.rag_mcp import mcp_call as _call, MCP_TOOLS  # noqa: F841
    svc_bases = {
        "kt": config.KT_BASE, "course": config.COURSE_BASE, "agent": "",
    }
    return _call(svc_bases, data.get("tool", ""), data.get("args", {}))


@post("/api/v1/agent/multi_role")
async def multi_role(data: dict) -> dict:
    """多 Agent 协作: 教学设计师 -> 知识点专家 -> 评价员 三角色流水线"""
    from app.agents.multi_role import run_multi_role
    return await run_multi_role(data)


async def on_startup(app: Litestar) -> None:
    app.state["slots"] = SlotConfig()          # 课程级模型槽位(启动期 fail-loud 校验)
    app.state["skills"] = SkillRegistry()      # 教学技能包(SKILL.md 热加载)


app = Litestar(route_handlers=[health, metrics, generate, assistant_stream,
                               rag_eval, mcp_tools, mcp_call, multi_role,
                               skills_list, skill_prompt, slots_info, slots_reload,
                               pedagogy_profiles, pedagogy_resolve,
                               interaction_start, interaction_answer, interaction_summary,
                               world_simulate, world_what_if, world_recommend,
                               a2a_agent_card, a2a_send, a2a_get, agui_runs,
                               durable_create, durable_heartbeat, durable_resume,
                               durable_instruction, sync_updates, sync_bump,
                               research_record, research_list, research_export,
                               a2a_rpc, a2a_rpc_stream, mcp_streamable],
               on_startup=[on_startup])
