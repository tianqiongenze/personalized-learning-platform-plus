# PLPP 开源项目对标分析与需求/架构补充(阶段9)

> 版本: v1.0 | 日期: 2026-10-23
> 方法: 4 个开源项目逐一 WebFetch 实测调研(README/技术栈/license/版本),映射 TOGAF ADM 增量 + GB/T 8567 文档体系
> 基线: docs/TOGAF_GB_COMPLIANCE_PACK.md v8.1、docs/TOGAF_ALIGNMENT.md v1.0
> 补充检索(pyBKT / GraphRAG)超时未纳入,标 DEFERRED,结论不受影响(定位重叠,风险低)

---

## 1. 对标项目总览

| 项目 | 定位 | 技术栈 | License / 版本 | Stars | 对标价值 |
|---|---|---|---|---|---|
| THU-MAIC/OpenMAIC | 清华多智能体互动课堂(虚拟教师/助教) | Next.js 16 + React 19 + Node 22 + PG 16;REST + SSE(无 GraphQL) | MIT(mathml2omml 捆绑 LGPL-3.0) | 41.3k | 高(AI 会话韧性/模型配置) |
| HKUDS/DeepTutor | 港大 AI 永久个性化辅导 | FastAPI(8001) + Next.js(3782);WebSocket/SSE | Apache-2.0,v1.6.15 | 42.3k | 高(记忆体系/检索评估/书生成) |
| fenago/LearnGraph | 图自适应学习(画像+KT+SM-2) | Next.js 14 + LevelDB + React Flow | MIT,Phase 1-5 | 14 | 中(业务域高度重合,可作镜像校验) |
| magicidea/ourclass-lite | 班级管理桌面小工具 | Python(win64 打包) | gitee v1.1.5 | 7 | 低(仅组织管理参考) |

## 2. 逐项差距分析(GAP)与借鉴点 → 落地清单

### 2.1 OpenMAIC(会话韧性与模型配置)

| OpenMAIC 设计 | PLPP 现状 | GAP | 建议落地(P0/P1/P2) |
|---|---|---|---|
| `openmaic.yml` slot 模型配置:llm/image/tts 解耦槽位 + fallback 链 + lock | LLM_BASE_URL/LLM_MODEL 单一 env(CM) | 模型无 fallback、无槽位抽象 | **P1**:configmap-secret.yaml 增加 `LLM_FALLBACK_MODELS`(逗号分隔),agent-service 调用失败按序切换 |
| `<CAP>_<PREFIX>_ENABLED=false` 能力强制关 | 部分服务有能力开关,不成体系 | 无统一命名规约 | **P2**:立能力开关命名规约 `PLP_<SVC>_<CAP>_ENABLED` |
| 持久会话:lease + heartbeat + crash resume + revision 增量刷新 | realtime-gateway 无会话 lease,断线即丢上下文 | **核心 GAP** | **P0**:agent/realtime 会话表加 lease(owner+ttl)+heartbeat,网关重启后会话可恢复 |
| 离线导出内联资产 | 无 | 低优 | P2(报告导出场景) |

### 2.2 DeepTutor(记忆/评估/内容生成)

| DeepTutor 设计 | PLPP 现状 | GAP | 建议落地 |
|---|---|---|---|
| 三层记忆(L1 事件/L2 事实/L3 综合)+ Memory Graph 可检查 | 记忆金字塔已有(student-service MemoryProfile) | 不可检查、无图可视化 | **P1**:GraphQL 增加 `memoryGraph(tenantId,userId)` 查询暴露三层+关系边 |
| 类型化块 + 大纲先行的 Book 生成 | course 资源手工/批量导入 | 无 AI 结构化生成 | **P2**:agent-service 增加"大纲→类型化块→课程"生成管线(先大纲审批后生成) |
| kb eval:检索可评估(Recall/nDCG/MRR) | vector-reranker 无检索质量评估 | **核心 GAP** | **P0**:vector-reranker 增加 eval 端点 + 标注集(50-100 条 Q→gold chunk),CI 定期回归 |
| 沙箱分级(sidecar→bubblewrap→subprocess) | ai 代码执行无分级隔离 | 安全 GAP | P1(若引入代码沙箱执行才做) |

### 2.3 LearnGraph(业务域镜像校验)

与 PLPP KT 域高度重合:39 维心理画像 ≈ PLPP 学生画像、概念图+先修边+Bloom ≈ knowledge_points 图、SM-2 遗忘曲线 ≈ ReviewPlan、ZPD Bridge ≈ 难度自适应。**价值是镜像验证而非引入**:确认 PLPP KT 域设计在业界同构项目中的合理性。其 RAG 上下文注入仍在 Phase 7(未完成),而 PLPP vector-reranker 已落地——此点 PLPP 领先。
- **P2**:画像维度对照其 39 维清单做一次查漏(当前 PLPP 画像维度约 20)。

### 2.4 ourclass-lite:不采纳(桌面小工具,无架构参考价值)。

## 3. TOGAF ADM 增量(对齐 v8.1 基线)

- **Phase B(业务)**:新增能力项——"AI 会话韧性""检索质量评估""记忆可视化"挂到 AI 域/学习域。
- **Phase C(信息)**:新增 2 个 GraphQL 查询契约(memeoryGraph、kbEval 概览)与 1 个会话 lease 表设计(agent_db.agent_session: session_id PK, owner, lease_until, revision, payload JSONB)。
- **Phase D(技术)**:LLM fallback 链写入技术标准(多模型冗余,关联 RWT 降级)。
- **Phase E/F**:上表 P0→近期 Sprint,P1→次期,P2→backlog;全部写入 ADR 索引待立项。

## 4. GraphQL 优先交互路径

前端已全量走 `/gateway/graphql`(schema.graphqls 7 域聚合)。残余 REST 直连:
1. ops-web → ops-service(管理面板,独立鉴权链,**保留 REST**,管理面非"前后端交互"主链);
2. web-student 内个别上传/下载 —— 文件二进制 GraphQL 不友好,保留;
3. 登录 /api/v1/auth/login(需要 X-Tenant-Id header + Set-Cookie,保留 REST 网关侧)。
**结论:学生端/教师端业务交互已 100% GraphQL,符合"尽可能 GraphQL"要求;管理面与文件流保留 REST 为行业最佳实践,记录 ADR 说明。**

## 5. 中间件/框架最新最优版本矩阵 + 避坑

| 组件 | PLPP 当前 | 业界最新稳定(2026-10) | 升级建议 | 避坑(本会话/业界实证) |
|---|---|---|---|---|
| Spring Boot | 3.5.6 | 3.5.x 持续(4.x 生态未稳) | 保持 3.5.x 小步升级 | RestClient URI 模板 `{}` 占位符会对变量值再编码——预编码串双重编码(%5B→%255B),传原始串;@RequestParam defaultValue 含 `{}` 同坑 |
| Undertow | A/B 已验证胜出 | — | 转正决策维持待定 | — |
| Node/前端 | Next.js + Vue3 + antd-vue 4.2.3 | Node 22 LTS / Next 16 / React 19 | web-student Vue 栈不建议追 Next/React(重写成本) | antd-vue Menu 布尔/数组 props 必须 `:prop`(v-bind),字符串字面量会进 watcher 崩 forEach |
| PostgreSQL/PG16 | — | (OpenMAIC 用 PG16 印证方向) | PLPP 数据面 CRDB+TiDB 已定,不引入 PG | — |
| TiDB/TiFlash | 已恢复,零改动 | — | **用户铁律:不动** | 30210 直连;enum 值域撞('ACTIVE' 不在 chapters.status)写前先查 schema |
| Redis | infra 单体 7.x | Redis 8.x | 不升(依赖 dify-plus 历史教训:SVD 跨大版本混部脑裂) | 有状态集群禁跨大版本混部 |
| Loki | 3.4.0-x64 单体 | 3.4.x | 保持 | 本机 arm64 镜像必须按 digest 拉 amd64;svc selector 需 pod 带 release label;**System.nanoTime≠epoch**,Loki start/end 用 currentTimeMillis()*1e6 |
| NebulaGraph | v3.8.3(19669 stats) | — | 保持 | 9669=gRPC / 19669=HTTP /stats,配置前查端口语义 |
| Milvus/Pulsar | 已运行 | — | 不动 | — |

## 6. 结论

- 四项目对标后 PLPP **无结构性架构缺陷**;2 个 P0(会话 lease 恢复、kb eval 检索评估)、3 个 P1(LLM fallback、memoryGraph 查询、画像维度查漏)、3 个 P2。
- LearnGraph 与 PLPP KT 域同构互相印证;DeepTutor/OpenMAIC 提供 2 个可落地工程模式;ourclass-lite 不采纳。
- GraphQL 覆盖已达标;中间件"最新最优"以稳定+避坑为准,不盲追版本(逐项理由见 §5)。
