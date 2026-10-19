# TiFlash(HTAP 列存)适配性分析 — PLPP

> 版本: v1.0 | 日期: 2026-10-18 | 状态: Accepted
> 关联: ADR-004(双存储动态切换)、ADR-005(TiFlash 分析型读路径)、ESP `adr-24-crdb-as-primary-data-plane.md`(教训来源)
> 集群事实: infra ns TiDB v8.5.8(pd×3 + tikv×3 + tidb×1 + tiflash×1, Deployment 裸部署非 TiDB Operator), PLPP 四库 iam_db/tenant_db/course_db/teaching_db 已建。

---

## 1. 结论摘要

| 结论 | 内容 |
|---|---|
| 适合 TiFlash 行转列 | **5 类分析型读负载**: KT 知识追踪批量评测统计、学习行为时序聚合、教师端报表、租户级运营统计、考试/作业成绩分布 |
| 不适合(保持 TiKV 行存) | 高频点写(签到/作答提交/SRS 复习/outbox relay)与强一致点查(登录鉴权/课程详情), **这些表不建 tiflash replica** |
| 现状偏差 | 当前四库表几乎全空(仅 iam_db.users=61 行), TiFlash replica 同步(AVAILABLE=0)因 tiflash store 反复 CrashLoop(`Cannot lock file /data/status`)未能完成 — 见 §6 DEFERRED |

**核心判断规则**(与 ESP ADR-24 复盘一致): TiFlash 的收益来自「少量列的聚合扫描 × 大行数」, 代价是额外一份列存副本的存储 + raft log 同步延迟(秒级, 不适合强一致读)。PLPP 的 OLTP 主路径(点写/点查)必须留在 TiKV; 只有**读放大明显、允许秒级陈旧的聚合查询**才值得行转列。

---

## 2. PLPP 服务与负载形态盘点(11 服务)

| 服务 | 数据面角色 | 负载形态 | TiFlash 候选 |
|---|---|---|---|
| iam-service | OLTP 点查/点写 | 登录鉴权按 username 点查 users/roles; 用户管理 CRUD | 否 |
| tenant-service | OLTP + 少量统计 | tenants/credit_transfer 点操作; 租户运营统计(credit_transfer_records 聚合) | **credit_transfer_records 候选** |
| course-service | OLTP | courses/chapters/knowledge_points/resources 点查, 行数小(内容元数据) | 否 |
| teaching-service | OLTP + 报表 | assignments/exams/discussions/live_sessions 点写; **教师端报表聚合(assignments 按课程/状态/分数分布)** | **assignments/exams 候选** |
| kt-service | 离线批量分析 | DKT/BKT 训练评测、掌握度统计 — **纯 OLAP 读**, 未来数据量最大 | **核心候选(见 §3.3)** |
| agent-service | 编排 | LLM 编排, DB 访问少 | 否 |
| ai-service | 编排 | LLM 推理 | 否 |
| vector-reranker | 向量检索 | Milvus | 否 |
| realtime-gateway | 长连接 | WebSocket, 无 SQL | 否 |
| graphql-gateway | 聚合网关 | 透传, 无自有表 | 否 |
| ops-service | 运维 | 健康检查/配置 | 否 |

---

## 3. 逐候选模块分析

### 3.1 教师端作业/考试报表(teaching_db.assignments / exams)

- **查询形态**: `SELECT course_id, status, count(*), avg(max_score) FROM assignments WHERE tenant_id=? GROUP BY course_id, status`; 考试成绩分布直方图; 截止期漏斗(已发布/已提交/已逾期)。
- **行存 vs 列存**: 现状行数≈0, 收益为零; 达到 **≥100 万行**(假设 10 万学生 × 20 门课 × 5 作业/学期)后, 全表按 2-3 列聚合, TiFlash 列存跳过无关列(20 列只读 3-4 列)+ 向量化, 预期 3-10× 收益。
- **决策**: `assignments`、`exams` 建 tiflash replica(已执行 `SET TIFLASH REPLICA 1`); 报表查询允许秒级陈旧(教师报表无实时性要求)。

### 3.2 租户级运营统计(tenant_db.credit_transfer_records)

- **查询形态**: `SELECT rule_id, count(*), sum(granted_credits) FROM credit_transfer_records GROUP BY rule_id`; 学分转移审计报表按 tenant_id + 时间窗过滤。
- **行存 vs 列存**: 只有 6 列, 列裁剪收益有限; 但审计类查询跨全租户全时间, 行数增长快(每笔转学分一行)。**中等收益, 建副本不着急**(行数 < 10 万前 TiKV 足够)。
- **决策**: Phase2 数据量起来后再建; 当前不建(避免空表浪费 raft 同步)。

### 3.3 KT 知识追踪批量评测统计(核心候选)

- **现状**: kt-service 训练/评测数据在本地 `synthetic_data/`(CSV/内存), 尚未入库。
- **规划查询形态**: 学做题事件流 `(student_id, skill_id, is_correct, timestamp)` 亿级行; 评测统计 = 按 student/skill/时间段聚合正确率、掌握度分布、薄弱点 TopN; 建议新增表 `kt_answer_events`(投影后窄表, ≤8 列)。
- **行存 vs 列存**: 这是 PLPP 与 ESP 场景最接近的负载(ESP 的 learning_events 30 亿行聚合)。窄表 × 亿行 × 全维度聚合, **TiFlash 收益最大(5-20×)**, 且与点写路径天然隔离(批量写入, 无点更新)。
- **决策**: 表建成之日即 `SET TIFLASH REPLICA 1`(DDL 模板见 ADR-005 §4); 写入走 TiKV, 读走 TiFlash(Hint: `/*+ READ_FROM_STORAGE(TIFLASH[kt_answer_events]) */`)。

### 3.4 学习行为时序分析(learning_events 类)

- **查询形态**: 学习时长/活跃漏斗/热力图, 按天滚动聚合; 依赖 outbox 事件流。
- **行存 vs 列存**: 同 3.3, 窄表时序聚合。但 PLPP 当前**无此表**(ESP 的 app_logs 对应物未建), 属规划项。
- **决策**: DEFERRED — 与 `kt_answer_events` 一起作为分析型窄表设计, 进 ADR-005 路线图。

### 3.5 不建 tiflash replica 的表(显式排除清单)

| 表 | 排除理由 |
|---|---|
| iam_db.users / roles | 登录鉴权点查, 要求强一致 + 低延迟; 行数小 |
| event_outbox(四库) | relay 扫表 + 状态更新(delivered_at/attempts)高频点写, TiFlash 副本只增写放大 |
| course_db 全部 | 内容元数据, 行数千级, TiKV 索引足够 |
| tenants / credit_transfer_rules | 维表, 行数极小, JOIN 时 TiKV 即可(维表小, HashJoin 广播成本低) |
| learning_paths / learning_path_steps | 结构化树导航点查为主 |
| discussions / live_sessions | 高频点写(发言/心跳), 已建副本但**不承载 OLAP 读**, Phase2 可回收 |

> 注: discussions/live_sessions 已设 replica(历史遗留), AVAILABLE=0 期间无实际代价; 表数据为空时同步完成即标 AVAILABLE。若确认无报表需求, 可 `SET TIFLASH REPLICA 0` 回收。

---

## 4. 行存 vs 列存收益判断矩阵(数据量假设)

| 负载 | 数据量假设 | TiKV 行存预估 | TiFlash 列存预估 | 收益 | 建 replica |
|---|---|---|---|---|---|
| KT 评测聚合(kt_answer_events) | 1 亿行 × 8 列 | 30-60s(全扫) | 2-8s(MPP) | 5-20× | ✅ 建表即建 |
| 教师作业报表(assignments) | 100 万行 × 20 列 | 2-5s | 0.5-1.5s | 3-5× | ✅ 已建 |
| 考试成绩分布(exams) | 50 万行 | 1-3s | 0.3-1s | 3× | ✅ 已建 |
| 学分转移审计(credit_transfer_records) | 10 万行 | <1s | <1s | ≈1× | ⏳ Phase2 |
| 登录鉴权 users 点查 | 10 万行, QPS 1000 | 1ms | 不适用(反例) | 负收益 | ❌ 不建 |
| outbox relay 扫表 | 万行/分钟写 | 顺序扫 | 写放大 | 负收益 | ❌ 不建 |

---

## 5. 查询路由策略(与 ADR-005 配套)

1. **默认不干预**: 优化器在 `tidb_allow_mpp=ON`(已确认)下按代价自动选 TiFlash; 表行数小于 `tidb_auto_analyze` 阈值时会退回 TiKV(伪统计), 这是正确行为。
2. **显式 Hint 用于报表固定走列存**: 分析型 SQL 模板统一加 `/*+ READ_FROM_STORAGE(TIFLASH[表名]) */`, 避免小表阶段优化器摇摆。
3. **强一致读禁走 TiFlash**: 鉴权/事务内查询不允许 TiFlash 路径(副本秒级陈旧), 由查询模板隔离, 不依赖开发纪律。
4. **验证方法**: `EXPLAIN` 输出中 `cop[tiflash]` 即命中列存; 本轮因 replica 未 AVAILABLE, EXPLAIN 仍显示 `cop[tikv]`(实测记录), AVAILABLE=1 后复查。

---

## 6. DEFERRED 项(诚实记录, HSA 铁律)

| 项 | 状态 | 原因 | 恢复步骤 |
|---|---|---|---|
| tiflash replica AVAILABLE=1 | **DEFERRED** | tiflash pod 反复 CrashLoop: 旧实例 `/data/status` 锁文件残留(`Cannot lock file /data/status`)导致新 pod 无法独占数据目录; store 10057 短暂 Up 后旧 store 心跳互踩; 且集群存在 6 个历史 Down 态 tiflash store(8684/8694/8723/9001/10055/10057)未被 PD 回收 | ① `tiup ctl pd` 下线/回收 Down store; ② 确认 PVC `tiflash-data` 上无残留进程后清 `/data/status`; ③ 重启 tiflash pod 单实例独占; ④ 复查 `INFORMATION_SCHEMA.TIFLASH_REPLICA.AVAILABLE=1` + `EXPLAIN` 出现 `cop[tiflash]` |
| credit_transfer_records 建 replica | DEFERRED | 行数不足, 建了无收益 | 数据量 > 10 万行后执行 |
| kt_answer_events 表 + replica | DEFERRED | 表本身未建(规划项) | ADR-005 路线图 Phase2 |
| learning_events 分析窄表 | DEFERRED | PLPP 尚无该表 | 同上 |

> 部署维度说明: 任务要求的"最小集群 pd×1+tikv×1"在执行时发现 infra 已存在 v8.5.8 三副本集群(8 天前部署), 且 TiFlash v8.5 与 TiDB v8.5.8 版本一致(符合 SVD 铁律: 版本三态对齐)。为避免与现有集群冲突(两个 PD 集群抢 port/资源), **不另建最小集群**, 改为"复用现有集群 + 把 live 清单固化进仓库(k8s/base/tidb.yaml)"满足 DGO 要求, 并如实记录。
