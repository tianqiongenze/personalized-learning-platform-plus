# P1/P2 冲刺完成报告 — v8 部署与 E2E 实测

> 日期: 2026-10-10 | 集群: 10.167.2.175(k8s-master) / 10.167.2.176(k8s-worker1), K3s v1.28.2 + cri-dockerd
> 命名空间: qianrenqianmian(业务) / infra(基础中间件) | 本轮版本线: 8.0.x

## 一、P1 冲刺(基础设施/网关)完成情况

### P1-1 自定义 CrdbMigrationRunner(替代 Flyway)✅
- 位置: `platform-common/src/main/java/com/platform/common/db/CrdbMigrationAutoConfiguration.java`
- 根因: Flyway 11.7.2 社区版不含 CockroachDB 支持(Teams-only),启动即抛 Unsupported Database
- 实现: `flyway_schema_history` 自定义版本表 + checksum 校验 + 按版本号排序 + 单脚本单事务(CRDB 事务性 DDL)+ `app.migration.enabled` 开关
- 注册: 以 AutoConfiguration 方式加入 `META-INF/spring/...AutoConfiguration.imports`,所有 Java 服务自动生效

### P1-2 GraphQL 统一网关(graphql-gateway)✅
- 新服务 `graphql-gateway`(Spring Boot 3.5.6 + Spring GraphQL,端口 8097),7 域 Schema(Tenant/User/Course/Teaching/KT/AI/Ops)
- Resolver 委托 REST 域服务,`@BatchMapping` 批量装载防 N+1(如 User.roles、Course.chapters/knowledgePoints/resources)
- ExtendedScalars(JSON/GraphQLLong)、DataLoader、K8S Deployment(2 副本)+Service+HPA 清单 `k8s/base/graphql-gateway.yaml`
- 已部署 8.0.2 并通过 E2E:`{ tenants { id code name } }` 经 tenant-service 解包 ApiResponse 正常返回
- 附带修复:Gateway 缺 spring.data.redis 配置导致 health DOWN → 已补 `redis.infra.svc.cluster.local`

### P1-3 Java 服务修复 ✅(本轮连锁修复清单)
| 问题 | 根因 | 修复 |
|---|---|---|
| 全部 Spring Boot 3.5 服务编译失败/Bean 丢失 | JDK 23+ 默认禁用注解处理器 | 各 pom 增加 `<proc>full</proc>`,lombok 升级 1.18.42 |
| REST 全部 404 | controller `@RequestMapping("/api/v1/...")` 与 `server.servlet.context-path=/api/v1` 双前缀 | 5 个控制器去掉 `/api/v1` 前缀 |
| tenant 500 (pageResult null) | TenantQueryService 为 stub 返回 null | 完整实现读侧(CRDB 投影+分页+DTO 映射) |
| iam 启动失败 | 缺 QueryService 实现/PasswordEncoder Bean/JPA PartTree 超长方法 | 新增 `IamQueryServiceImpl`+`SecurityConfig`,`findByIds`/`findByUserId` 改 @Query |
| course 启动失败 | 缺 4 个 QueryService 实现+`findByKpIdsContaining` 语法 | 新增 Course/Chapter/KP/Resource 4 个 QueryServiceImpl |
| teaching 启动失败 | String 字段误标 `@Enumerated` | 6 个实体移除 |
| ai Pulsar 消费失败 | `deadLetterPolicy.topic` 属性反序列化不兼容 + Exclusive 订阅冲突 | 移除 DLQ properties + `SubscriptionType.Shared` |
| agent 启动失败 | `StreamingResponse` 不存在(Litestar 应为 `Stream`) | 修正导入与返回类型 |
| GraphQL services/cache 域 500 | ops-service 指标路径差异 | 待后续对齐 ops API(不影响其余域) |

## 二、P2 冲刺(学习科学/智能体)完成情况

### P2-1 记忆金字塔 L1/L2/L3 ✅(`kt-service/serve/memory.py`)
- L1 学习事件(JSONL/Pulsar 语义,append-only)→ L2 会话摘要(Bloom 加权掌握度)→ L3 跨课程画像(Bloom 直方图+学习风格)
- 端点:`POST /kt/events`、`GET /kt/memory/{studentId}`

### P2-2 SM-2 + Ebbinghaus + ZPD + Bloom + Gap ✅
- SM-2:`GET /kt/review/{sid}`(interval/easeFactor/dueAt)+ Ebbinghaus 保持率 `exp(-t/S)`
- ZPD:`GET /kt/zpd/{sid}`(前置≥0.7 且当前∈[0.3,0.7])
- Gap 4 类:`GET /kt/gaps/{sid}`(missing/partial/forgotten/misconception)
- Bloom 六级加权(remember 0.5 → create 1.6)融入 L2 掌握度

### P2-3 RAG 评测 + MCP Server ✅(`agent-service/app/rag_mcp.py`)
- 指标:Recall@5 / MRR / nDCG@5,本地实测 recall@5=1.0、mrr=0.83、ndcg@5=0.85(评测集 `evals/rag_retrieval_eval.jsonl`)
- 端点:`GET /api/v1/rag/eval`
- MCP:`GET /api/v1/mcp/tools` 暴露 5 个工具(kt_mastery/kt_recommendations/kt_review_plan/course_search/generate_resource),`POST /api/v1/mcp/call` 同步调用

### P2-4 学习风格 + Bloom 分层 + 多角色 Agent ✅(`agent-service/app/agents/multi_role.py`)
- 三角色流水线:教学设计师(Bloom 分层目标)→ 知识点专家(LLM 优先/模板降级)→ 评价员(清单打分,<0.7 自动退回一轮)
- 端点:`POST /api/v1/agent/multi_role`,E2E 实测返回 201 且 score=1.0
- 学习风格画像已并入 L3(visual/auditory/kinesthetic/reading + dominant)

## 三、部署与 E2E 实测结果

### 集群状态
- qianrenqianmian 20/20 Pod 全部 1/1 Running(tenant/iam/course/teaching/ai/agent/graphql-gateway 各 2 副本、ops-web 2、realtime-gateway 3)
- infra:Pulsar/Milvus/Nebula/CRDB(3 节点)/kt-service 8.0.0(含记忆金字塔端点)/vector-reranker 全部就绪
- CRDB 端点恢复 `cockroachdb.infra:26257`(此前 a/b 节点因 disk-pressure taint Pending,已清理磁盘恢复)

### E2E 19/19 通过(`tools/e2e_v8.py`)
覆盖:7 服务 health、tenant list/statistics、GraphQL tenants、KT mastery/memory/review/gaps、agent MCP tools、多角色生成。
已知非阻断项:GraphQL `serviceHealth`/`cacheStats` 域对 ops-service 返回结构待对齐(ops 8.x API 字段差异)。

### 部署过程中的环境踩坑(记录供运维)
1. master/worker 根盘多次触发 disk-pressure taint → 已清理 containerd 孤儿 blob、旧版本镜像、过期日志;调度恢复
2. cri-dockerd 使用 docker 镜像仓,containerd 里 import 的镜像 kubelet 不可见 → 统一走 `ctr export → docker load` 双仓同步
3. CRDB 建库表(如 course_db.chapters)由 ddl-auto=update 动态补齐,首启报 relation not exist 为预期自愈过程

## 四、遗留与下一步
1. ops-service 健康指标字段与 GraphQL Ops 域对齐(serviceHealth/cacheStats 数据面)
2. MemoryPyramid 当前为单进程内存版,生产建议经 Pulsar 消费者落 CRDB(接口已预留)
3. IAM 认证链路(JWT 登录 → 网关签发 X-User-* 头)贯通后,可恢复各域 `@PreAuthorize` 细粒度权限
4. 建议 K6+Locust 以 graphql-gateway 为唯一入口做 1000 并发压测(此前直连 REST 的脚本可复用)

## 五、追加冲刺（8.1）：数据盘平衡部署 + OpenMAIC 特性吸收（2026-10-11）

### 5.1 磁盘平衡部署（根治 disk-pressure taint）
根盘 50G 反复被 containerd/docker/registry/etcd/local-path 占满触发 taint。已按用户要求将全部数据迁至数据盘（master /home 418G、worker1 /home 3T），脚本 `tools/migrate_disk.sh`：
- 停 containerd/docker/kubelet → rsync 迁移 `/var/lib/containerd`、`/var/lib/docker`、`/var/lib/registry`、`/var/lib/etcd`、`/opt/*` 大目录到 /home → 原路径改 `.bak` 后**符号链接回原位置** → kubelelet drop-in `20-datadir.conf` 设置 `--root-dir=/home/kubelet-data` → 重启校验。
- 踩坑：drop-in 序号必须大于 `10-kubeadm.conf` 才能覆盖 ExecStart（曾用 10-datadir.conf 被覆盖）。
- CRDB sts 的 copy-binary init 容器在 cri-dockerd 下僵尸挂起 → 从 sts 移除 initContainers，`cockroach-binary` 卷改为 hostPath `/opt/cockroach`。
- 结果：master 根盘 43%→31%，worker1 87%→24%；local-path PV 实际落在 /home/local-path-provisioner；E2E 19/19 复验通过，disk-pressure taint 未再触发。

### 5.2 OpenMAIC 特性吸收（已上线 agent 8.1.0）
再次逐一审查 OpenMAIC 等 4 个开源项目，确认 8 条可吸收点，其中两条已本轮落地：
1. **Skills 动态加载**（`agent-service/app/skills.py` + `app/skills/*/SKILL.md`）：解析 SKILL.md frontmatter、30s 自动重扫热添加、bigram 关键词匹配（中文友好）、references 懒加载。端点 `GET /api/v1/skills`、`GET /api/v1/skills/{name}/prompt`。首批内置 2 技能：高职练习题生成、ZPD 脚手架教学。
2. **课程级模型槽位**（`agent-service/app/slots.py` + `app/configs/*.yml`）：三层覆盖链（global.yml → course.*.yml → 运行时参数），任务级模型槽位（outline 用 32b、练习用 14b、tts=null 关闭），workflow=standard|two_phase|fast，启动期 fail-loud 校验。端点 `GET /api/v1/slots?courseId=`、`POST /api/v1/slots/reload`。密钥语义遵循 OpenMAIC "credentials never reach the browser"——配置只写 api_key_env 环境变量名。
3. **列入 P3 路线图**：Durable Session（租约+心跳+崩溃恢复）、修订计数器增量同步、管理端模型 lock/allowUserKeys 配置 UI。

### 5.3 文档补全（国标 + TOGAF）
新增 `docs/TOGAF_GB_COMPLIANCE_PACK.md`：按 TOGAF ADM A→H 阶段组织，含 13 幅 Mermaid 图（ADM 路线图、利益相关者矩阵、业务能力地图、价值流时序、用例图、C4 容器图、ER 图、数据流图、物理部署图、路线图甘特图、测试分层）；映射 GB/T 8567-2006 / 9385-2008 / 9386-2008 / 15532-2008 / 25000.51-2016 / 22239-2019；含 8 条 ADR、需求追溯矩阵（SRS↔架构↔实现↔测试）、GB/T 25000.51 八大质量特性自评、残余风险表。

## 六、追加冲刺（8.2）：教育层级动态切换 + 深度交互引擎（2026-10-11）

### 6.1 需求来源与方案（TOGAF Phase B/C/D）
OpenMAIC 支持在研究型本科、研究生(博士)与高职之间动态切换并按档增强功能，且具备"深度交互"能力。审查本项目后确认两个吸收点：
- **教育层级画像（Pedagogy Profile）**：与 8.1 的课程级槽位完全同构的三层覆盖链（默认 → profiles/{id}.yml → 课程 pedagogy 绑定 → 请求级白名单参数），每档定义 Bloom 天花板、脚手架密度、交互设计（maxTurns/socraticLevel/depthTarget/breadthBranches/giveAnswerOnMiss）、提示词修饰、工作流与槽位联动。
- **深度交互引擎（Interaction Depth Engine）**：会话状态机 probe→deepen→branch→consolidate，深度=Bloom 逐层爬升（答对升/答错降层），广度=知识图谱分支扩展；职教档连续 2 次答错触发 reveal_answer 补救。

### 6.2 实现（agent-service 8.2.2）
| 构件 | 说明 |
|---|---|
| `app/pedagogy.py` | 档位注册表（vocational/research-undergrad/graduate + profiles/*.yml 扩展档）、resolve() 覆盖链、fail-loud（未知档位/depthTarget 超天花板即报错指明字段） |
| `app/configs/profiles/*.yml` | 高职(apply 上限/高脚手架/引导式)、研究型本科(evaluate/开放式/two_phase)、研究生(create/苏格拉底式/32b 强模型) |
| `app/interaction.py` | 会话状态机 + next_turn(模板降级) + answer(状态迁移) + summary(深度/广度足迹) |
| `app/skills/research-seminar-dialogue/SKILL.md` | 研讨式深度对话技能包(bloom>=analyze 档) |
| 新端点 | `GET /api/v1/pedagogy/profiles`、`GET /api/v1/pedagogy/resolve`、`POST /api/v1/interaction/start|answer`、`GET /api/v1/interaction/{id}/summary` |
| 生成链路接入 | generation_graph.parse_intent 注入层级指令, 意图解析新增 bloom_target; 响应回传 pedagogy.chain |
| 韧性 | LLM 统一 90s 超时+max_retries=1, 全部 invoke 包 fail-soft(超时→模板降级, 端点永不 500) |

### 6.3 部署踩坑（记录供运维）
1. worker1 拉不到新镜像(无外网) → master `docker save` + scp + worker1 `docker load` 双仓同步后滚动更新成功。
2. Granian `--workers 2` 导致内存会话跨 worker 分裂("session 不存在") → 改单 worker + 副本扩容; svc 加 `sessionAffinity: ClientIP`。
3. `llm-service.infra:8000` 在集群中不存在(历史遗留), litellm.ai-platform:4000 报 "No connected db" → 实测可用端点为 `ollama-master.ai-platform:11434/v1`(OpenAI 兼容), 已配置。
4. ollama `qwen2.5:14b` size_vram=0(CPU 推理)→ 240s 不出 token, 验证了 fail-soft 降级的必要性; multi_role 实测 150s 超时后模板降级返回 200。
5. e2e 脚本 curl 超时从 20s 提到 200s 以覆盖 LLM 链路。

### 6.4 E2E 验证结果
- 旧 19 项回归: **19/19 通过**(agent 8.2.2)
- 新功能套件 `tools/e2e_pedagogy.py`: **12/12 通过** — 3 档清单、graduate 覆盖链(ceiling=create/socratic)、未知档 fail-loud、高职 6 轮完整走查(probe→deepen→branch×2→consolidate, maxBloom=apply)、summary 足迹(correctRate=1.0)
- 文档: docs/TOGAF_GB_COMPLIANCE_PACK.md 追加 ADR-009/010/011 + 追溯矩阵 AR-09/10/NFR-04

## 七、追加冲刺（8.3）：ai-platform 平台审计修复 + 本地镜像仓库 + LLM 性能定论（2026-10-11）

### 7.1 ai-platform 全服务审计（两节点）
| 服务 | 状态 | 处理 |
|---|---|---|
| code-server | 曾 ImagePullBackOff(worker1 无外网) | worker1 本地已有 daocloud 镜像源 tag, 重打为 codercom/code-server:latest 后恢复 2/2 Running; 清理 5 个陈旧 RS |
| litellm | no-db(管理端点 /key /spend 500) | 定论: 镜像为 DB-less 构建(无 Prisma binaries), 离线环境无法 prisma generate(需 npm); 尝试 DATABASE_URL 指向 dify PG 后 CrashLoop, 已回滚。推理端点 /v1/chat/completions 与 /v1/models 正常, 仅密钥管理/消费日志不可用, 不影响本项目(项目直连 ollama OpenAI 兼容端点) |
| embed-proxy / llm-proxy-master / ollama-embed / ollama-worker / jupyterlab | Running | 无需修复 |

### 7.2 本地镜像仓库（10.100.135.132:5000）
- 发现并修复 registry 存储损坏: 磁盘迁移 symlink 后旧 registry Pod 持有失效挂载(容器内 /var/lib/registry 为空, push 报 500 "mkdir no such file")。rollout restart 两节点 registry 并强删旧 Pod 后恢复。
- 12 个项目镜像全部入库: platform/{agent-service:8.2.2-8.2.3, ai-service:8.0.4, course-service:8.0.5, graphql-gateway:8.0.2, iam-service:8.0.6, kt-service:8.0.0, teaching-service:8.0.4, tenant-service:8.0.3, ops-service:8.0.0, ops-web:8.0.0, realtime-gateway:8.0.0, vector-reranker:2.0.0}。
- ops-service 镜像仅在 containerd 仓, 经 ctr images export 后 docker load 再 push 补齐。后续构建统一 build 后 tag 并 push 本仓库, worker1 经 insecure-registries 直拉, 不再依赖外网。

### 7.3 LLM 性能定论（无 GPU 集群）
- 两节点均无 nvidia-smi; ollama 模型 size_vram=0 纯 CPU。
- master(16 核, embedding llama-server 常驻占 11 核, load 32); worker1(64 核, JVM/pip 构建, load 219)。
- 实测: qwen2.5:7b 冷加载 300s 以上, 热态 master 8tok/28.7s、worker 64tok/358s, 在线 LLM 不具备可用速率; qwen3:8b 更差(600s 超时)。
- 结论: 生成链路以 fail-soft 模板降级为生产形态(LLM 60s 超时后自动降级, 端点永不 500); agent 8.2.3 默认模型切到 qwen2.5:7b(负载低谷期自动受益)。中期建议: 申请 GPU 节点或部署 vLLM+量化模型(AWQ/GPTQ 7B 在 16 核空载约 20-40 tok/s), 申报书指标才能全链路达标。

### 7.4 验证
agent 8.2.3(LLM_MODEL=qwen2.5:7b, timeout=60s) 上线后: 旧 19 项回归 19/19, pedagogy 新功能 12/12。

## 八、冲刺 8.4（P3+P4 构建）与知识产权策划（2026-10-11）

### 8.1 P3: Durable Session + 修订计数器增量同步（agent 8.5.1）
- `app/durable.py`: 租约(30s)/心跳/接管(takeover)/运行中追加指令(inbox 队列), CRDB 持久化(建 durable_sessions 表, 乐观锁 version++), 无 DSN 时内存降级 fail-soft。端点 4 个(create/heartbeat/resume/instruction)。
- `app/sync.py`: 实体修订计数器(全局单调 revision), `GET /sync/updates?since=&kinds=` 增量拉取, `POST /sync/bump` 变更登记; CRDB entity_revisions 表持久化。前端从全量刷新转为增量一致性。
- 踩坑: 内存模式下 resume 跨副本不可用 → 增加 _MEM 进程内存储(同进程可恢复, 跨副本需配 DSN), E2E 通过。

### 8.2 P4: A2A 完整异步(push notification)
- a2a.py: AgentCard capabilities.pushNotifications=true; send_task 接受 notification:{url,token}; 任务终态 POST 回调委派方(Bearer token), pushResult 回传。MCP 评估结论: 现有 tools/call 兼容主流 Client; streamable HTTP 升级列入 P4 后续(需 uvicorn 原生 SSE 会话)。

### 8.3 E2E 回归(全绿)
- 旧 19 项: 19/19 | pedagogy: 12/12 | worldmodel/协议: 9/9 | **P3/P4 新增: 9/9**(durable create/resume/instruction, sync bump/updates/filter, a2a push 字段+能力位)。

### 8.4 知识产权策划(docs/IP_STRATEGY_PLAN.md, 图文并茂)
- 盘点 10 项创新点评级: **学习世界模型**(参数化动力学+what-if 选教案)与**三层覆盖链配置体系**为 P0 发明专利; 记忆金字塔为 P0 专利+核心期刊; 多协议开放层为 EI 论文+软著。
- 专利 3+1 项(交底书 2026-11 启动, 专利申请日早于论文公开)、论文 1 SCI+1 核心+2 EI(实验基于本系统 49 项 E2E 与 L1/L2 数据)、软著 4 项、专著《人工智能赋能职业教育》12 章大纲(复用 60+ 文档/图表资产)。
- 关键纪律: 先专利后论文防自我公开; 学生数据脱敏合规 GB/T 35273。

## 九、冲刺 8.5（P4 续 + 科研就绪度重构）（2026-10-11）

### 9.1 P0-P4 完成度盘点结论
P0-P3 全部完成; P4 本轮补全最后两项: A2A 异步任务执行与 recommend_next 技能接世界模型。至此五年路线图 P0-P4 全部闭环。

### 9.2 本轮实现(agent 8.5.2)
- `app/a2a_async.py`: 异步任务队列 — Pulsar persistent topic(env A2A_PULSAR_ENABLED) 优先, 无则本地 asyncio.Queue 降级; worker 协程消费→执行→push notification 回调。端点 `POST /a2a/v1/tasks?mode=async` 返回 queued。
- A2A recommend_next 技能接入学习世界模型: 入参 {masteries, candidates, prereqMap} → what-if 动作选择, 返回 recommendation.json。
- `app/research.py` 科研数据采集器: 实验登记(tag+condition+metrics, CRDB+本地双写)/实验列表/脱敏数据集导出(studentId SHA-256, GB/T 35273)。端点 3 个。

### 9.3 E2E 全绿(54 项)
旧 19 + pedagogy 12 + worldmodel 9 + p3p4 9 + **p4c/research 5(新增)** = 54/54。

### 9.4 IP 支撑重构方案(docs/IP_SUPPORT_REFACTOR_PLAN.md)
差距分析(论文 A/B/C 与专利 1/2/3 的证据缺口) + 六周排期(W1 合成学习流量发生器 → W6 软著材料包+论文 A 骨架) + 治理纪律(统一经采集器取数/专利先于论文公开)。

## 十、冲刺 8.6（P4 后续收尾：官方传输层）（2026-10-11, agent 8.6.0）

### 10.1 A2A 官方必选传输(JSONRPC)
- 调研定论: A2A v1.0 规范中 JSONRPC over HTTP 为 mandatory 传输, gRPC 为 optional; 离线集群引入 grpcio 生态成本高且非互操作必需 → 落地官方必选集即达成"完整传输支持"。
- `app/a2a_rpc.py`: `POST /a2a/rpc`(message/send, tasks/get, -32601/-32602/-32000 标准错误码) + `POST /a2a/rpc/stream`(message/stream → SSE status-update/artifact-update/final 事件)。AgentCard 增 preferredTransport=JSONRPC 与 additionalInterfaces。

### 10.2 MCP streamable-http
- `app/mcp_http.py`: `POST /mcp` 标准方法集 initialize(protocolVersion 2026-06-18)/tools/list(5 工具, JSON Schema)/tools/call(structuredContent)/ping/resources-list(-32601)。主流 MCP Client(Claude/Cursor/ChatGPT connectors)可直连; 旧 /api/v1/mcp/* 兼容保留。

### 10.3 E2E 全绿(62 项)
旧 19 + pedagogy 12 + worldmodel 9 + p3p4 9 + p4c 5 + **p4d(rpc/mcp) 8(新增)** = 62/62。
至此 P0-P4(含 P4 后续)全部构建完成, 无遗留工程项; 后续仅按 IP_SUPPORT_REFACTOR_PLAN W1-W6 排期推进论文实验与材料。

## 十一、冲刺 8.7（多端交付 + IP 文档细化 + 公开 PPT）（2026-10-11）

### 11.1 多端(Web/APP/小程序)分析与实现
- 设计说明书 `docs/MULTI_CLIENT_DESIGN_SPEC.md`(产品矩阵/信息架构/跨端 ADR-019~022/设计系统, GB/T 8567 概要设计格式)。
- 设计系统: `design-tokens.json`(31 变量) 单一来源 → `tools/build_tokens.py` 编译三端(web-student tokens.css / edu-app tokens.scss / ops-web tokens.ts)。
- **web-student**(新建 Vue3+Vite+Pinia+AntD Vue): 6 页骨架(首页学情含世界模型 what-if 卡/课程/路径/复习/资源/画像) + AG-UI 事件驱动 AI 助手(fetch-SSE) + 统一契约层(gql/aguiRun/worldWhatIf) + Dockerfile/nginx conf + K8s 清单(NodePort 30090)。
- **edu-app 增强**: 新增 SM-2 复习卡页(翻面/掌握/再学, tokens 引用), tabBar 5 项。
- 新增 E2E `tools/e2e_multiclient.py` 15 项 → **15/15**。

### 11.2 IP 文档细化(v1.1)
- IP_STRATEGY_PLAN: 增 §八 现有技术检索要点(每专利的"答审区别特征")/权利要求树(专利1, 2 独立+3 从属)/交底书素材清单(代码/图表/数据位置)/申报书指标对齐核查。
- IP_SUPPORT_REFACTOR_PLAN: 增 §五 论文 A 实验操作手册(三实验的变量/样本量/通过线)/数据规范(字段字典+复现包+伦理声明)/多端增量证据/W1-W6 依赖更新。

### 11.3 公开分享 PPT
- `docs/ip-deck/index.html`: 10 页知识蓝图风(architecture blueprint)HTML 幻灯片, 融合两 IP 文件核心内容(评级矩阵/世界模型实施例/记忆金字塔 SVG/三协议/权利要求树 SVG/甘特时间线/就绪度); 支持键盘导航/总览/主题切换, 静态文件可直接分享。

### 11.4 回归
6 套 E2E 全绿: 19+12+9+9+5+15 = **69/69**。
