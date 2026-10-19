# PLPP TOGAF 对齐文档(TOGAF ADM 六阶段)

> 版本: v1.0(2026-10-18)
> 范围: PLPP(personalized-learning-platform-plus,个性化学习平台 Plus)
> 方法论来源: ESP SAD —— `/Users/joelgong/claude-Projects/English-Study-Platform/docs/design/02-系统架构设计文档-SAD.md` 的"分层架构 + 事件驱动 + 读写分离"章节。本文档全部模式声明为**"参照 ESP 模式落地"**。

---

## 目录

1. [Phase A: 架构愿景](#phase-a-架构愿景)
2. [Phase B: 业务架构](#phase-b-业务架构)
3. [Phase C: 信息系统架构](#phase-c-信息系统架构)
4. [Phase D: 技术架构](#phase-d-技术架构)
5. [Phase E: 机会与解决方案](#phase-e-机会与解决方案)
6. [Phase F: 迁移规划](#phase-f-迁移规划)
7. [ADR 索引](#adr-索引)

---

## Phase A: 架构愿景

PLPP 目标:支撑多租户个性化学习平台(学生/教师/管理多端),按工业级标准构建(可水平扩展、可移植多节点),参照 ESP 的验证拓扑——单/双节点 Rancher Desktop 验证,保留多节点迁移路径。

本轮对齐 ESP 的三项核心架构决策:

| 决策 | 状态 | 详见 |
|---|---|---|
| 多级缓存(L1 Caffeine + L2 Redis) | 已接线(3 服务查询路径) | ADR-002 |
| 事件驱动(Pulsar + Outbox) | 已全量接线(`app.events.outbox=true`) | ADR-003 |
| 读写分离(RoutingDataSource + @ReadOnly) | 本轮新增,默认关闭 | ADR-001 |

---

## Phase B: 业务架构

业务域划分(11 服务):

- **身份与租户域**: iam-service(认证/授权/用户)、tenant-service(多租户管理)
- **课程与教学域**: course-service(课程管理)、teaching-service(教学过程)、kt-service(课堂互动/知识树)、edu-app(教育应用层)
- **学习域**: web-student(学生端)、realtime-gateway(实时推送/协同)
- **运营域**: ops-service(运营/监控)、ops-web(运营端)
- **AI 域**: agent-service(智能体)、ai-service(AI 能力)、vector-reranker(向量检索重排)
- **接入域**: graphql-gateway(GraphQL 统一网关)、nginx-gateway(边缘网关)

业务架构遵循 ESP SAD 的分层模式:边缘网关 → BFF/聚合层 → 领域服务 → 数据层,水平方向叠加横切面(认证、租户、缓存、事件、可观测)。

---

## Phase C: 信息系统架构

### 数据架构

- **主存储**: CockroachDB 3 节点(10.167.2.175 / 10.167.2.176,双节点物理机部署,`k8s/base/cockroachdb.yaml`)
  - 主 svc:`cockroachdb`(NodePort 26257,写路径)
  - 读 svc:`cockroachdb-read`(NodePort 26267,follower/replica 读路径)——ADR-001 读写分离的读端点
- **缓存**: Redis Cluster(L2)+ 进程内 Caffeine(L1),platform-common `TwoLevelCache`,跨实例失效广播(pub/sub `plp:cache:invalidate`)
- **向量存储**: Milvus(知识点/内容语义检索,vector-reranker 消费)
- **图存储**: Nebula(知识图谱/学习路径关系)
- **对象/文件**: k8s 集群本地卷(hostPath 模式,多节点迁移时替换为分布式存储,DEFERRED)

### 应用架构

- **领域事件**: Pulsar;可靠性基线为 **Transactional Outbox**(EER 模式)——业务事务内业务写 + outbox 行同库原子提交,独立 relay 扫表重投,消费幂等。`platform-common` 提供 `OutboxEventRelay` / `PulsarDomainEventPublisher` / `DomainEventEnvelope`,`app.events.outbox=true` 已全量接线。
- **读写分离**: `platform-common` `com.platform.common.rw` 组件(参照 esp-rw-java 落地),`@ReadOnly` 注解 + AOP 路由,写事务内强制 WRITE 保证连接一致性。
- **多级缓存**: `TwoLevelCache` L1 Caffeine(进程内,微秒)+ L2 Redis(跨实例,毫秒),命中率监控接入 Micrometer。

---

## Phase D: 技术架构

| 层 | 技术选型 | 备注 |
|---|---|---|
| 运行时 | Java 17 / Spring Boot 3.5 | platform-common 公共共享包(≥2 服务共用必下沉,禁复制) |
| 消息 | Apache Pulsar 3.x | 领域事件总线,outbox relay 投递 |
| 缓存 | Redis Cluster + Caffeine | L1/L2 两级 |
| 数据库 | CockroachDB 3 节点 | 可水平扩展,支持副本读 |
| 服务网格/编排 | Kubernetes 双节点(Rancher Desktop) | 验证拓扑,可移植多节点 |
| AI | Milvus + Nebula + LLM 网关 | 语义检索/知识图谱/智能体 |
| 可观测 | Prometheus + Grafana + Alertmanager | `monitoring/` |

与 ESP 的映射:ESP 用 TiDB(主/副本)+ TiFlash HTAP;PLPP 用 CRDB 3 节点替代(见 `docs/CRDB_VS_TIDB_COMPARISON.md`),读写分离路由模式(lookup key + ThreadLocal)保持一致,读端点从 TiDB follower svc 换成 CRDB read svc(26267)。

---

## Phase E: 机会与解决方案

1. **多级缓存**(ADR-002,已落地): 3 服务查询路径已接线 `TwoLevelCache`;扩展方向:其余读多写少服务按 `app.cache.specs.<cacheName>` 逐个接入。
2. **事件驱动**(ADR-003,已落地): outbox 全量接线;扩展方向:DLQ 监控告警与重放工具化。
3. **读写分离**(ADR-001,本轮新增): 组件已入库且测试全绿;启用是**各服务逐个开启**的运维动作——`app.rw.enabled=true` + `app.rw.write-url`/`app.rw.read-url`,先在只读查询占比高的服务(course/ops 查询路径)试点,验证 CRDB follower 复制延迟可接受后再全量。
4. 本轮新增组件清单(`platform-common/src/main/java/com/platform/common/rw/`): `DataSourceType` / `RoutingHolder` / `ReadWriteRoutingDataSource` / `ReadOnly` / `ReadOnlyAspect` / `ReadWriteDataSourceProperties` / `ReadWriteDataSourceAutoConfiguration`(已注册 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`)。

---

## Phase F: 迁移规划

读写分离启用路线(风险递增排列):

1. **阶段 0(当前,默认关闭)**: `app.rw.enabled` 未配置 = false,所有服务走现有单主库路径,零行为变化。
2. **阶段 1(试点)**: 选 1 个读占比高的服务,配置 `app.rw.enabled=true` + read-url 指向 `cockroachdb-read`(26267),观察 follower 复制延迟(目标 < 500ms)与读命中率。
3. **阶段 2(全量)**: 试点通过后各服务开启;写后需强一致读的查询路径用 `@ReadOnly(forceWrite=true)` 走主库。
4. **阶段 3(多节点迁移,DEFERRED)**: 双节点 → 多节点时,CRDB 副本随集群原生扩展,read-url 可改为多副本负载均衡端点;组件无需改动,仅运维配置变化。

风险与回滚: 任一阶段将 `app.rw.enabled` 改回 false 即回滚到单池;未配 read-url 时组件自动降级单池(READ 也走主库)。

---

## ADR 索引

| 编号 | 标题 | 状态 |
|---|---|---|
| [ADR-001](adr/ADR-001-读写分离共享包.md) | 读写分离共享包(参照 esp-rw-java) | Accepted(默认关闭,按服务启用) |
| [ADR-002](adr/ADR-002-多级缓存.md) | 多级缓存 L1 Caffeine + L2 Redis | Accepted(已接线) |
| [ADR-003](adr/ADR-003-outbox事件驱动.md) | Outbox 事件驱动(Pulsar) | Accepted(已全量接线) |
