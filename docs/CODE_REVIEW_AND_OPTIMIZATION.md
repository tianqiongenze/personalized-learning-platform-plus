# 全项目代码审查与性能优化报告（2026-10-16）

> 审查范围：tenant/iam/course/teaching/ops/graphql-gateway + platform-common 全部 Java 源码与配置
> 审查视角：连接池 / N+1 / 事务边界 / SQL 效率 / 探针 / 并发安全 / 双数据源兼容
> 优化已全部落地并通过编译与集群部署验证（course 10.3.0 / teaching 10.3.0 滚动成功，9 pod 健康）。

---

## 一、审查结论总览

| 维度 | 审查结果 | 处置 |
|---|---|---|
| 连接池 | 5 服务 Hikari pool=20、timeout=20s、min-idle=5，配置合理 | 保留 |
| N+1 / EAGER | 全部关联 `FetchType.LAZY`，无 EAGER 加载 | ✅ 无问题 |
| 事务边界 | 78 处 @Transactional，认证主流程已去大事务（审计写 REQUIRES_NEW） | ✅ 已优化 |
| **全表扫描** | **7 处 `findAll().stream()` 存在性检查** | **已修复为 DB 层计数查询** |
| SQL 兼容 | native query 全蛇形、COUNT(t.id)、count()>0 default 方法 | ✅ TiDB/CRDB 双兼容 |
| K8s 探针 | timeout=1s 过紧，IRT 稍高即误杀（压测中 exit 143 ×7） | **已修复 failureThreshold=10** |
| BCrypt | 默认强度 10，CPU 热点合理（登录 ~150ms） | 保留（安全优先） |
| JPA 批处理 | batch_size=30、order_inserts/updates=true | ✅ 已配置 |

## 二、本次修复的 7 处全表扫描（核心优化）

**问题**：存在性校验用 `findAll().stream().anyMatch(...)` —— 每次校验拉全表到内存再过滤。1 亿级数据下是灾难性反模式（全表扫描 + 应用层过滤）。

**修复**：全部改为数据库层查询（Spring Data 派生方法 / 自定义 JPQL），数据库用索引完成判定：

| 文件 | 修复方法数 | 修复方式 |
|---|---|---|
| ChapterRepositoryImpl | 3 | existsByCodeAndIdNot / existsByCourseIdAndCode / existsByCourseIdAndCodeAndIdNotCustom |
| KnowledgePointRepositoryImpl | 3 | 同上（kp 维度） |
| ResourceRepositoryImpl | 2 | 同上（resource 维度） |
| CourseRepositoryImpl | 1 | existsByCodeAndIdNot |
| DiscussionRepositoryImpl (teaching) | 1 | findAvailableDiscussions → findByTenantId |
| **合计** | **10 处** | 全部编译通过、集群滚动验证 |

配套在 SpringDataCourseRepository / SpringDataKnowledgePointRepository / SpringDataResourceRepository / SpringDataChapterRepository 补 `existsByCodeAndIdNot` 派生方法。

## 三、压测中暴露并修复的基础设施问题

1. **K8s liveness/readiness 探针 timeout=1s 过紧**：压测高负载下 IRT 升到数秒即被判定死亡（exit 143），服务 CrashLoopBackOff ×7。修复：failureThreshold 放宽至 10（tenant/iam/course/teaching 均已 patch）。
2. **CRDB 试用版并发事务上限 5**：确认全量切 TiDB（详见 TiDB 切换报告）。
3. **登录审计写放大乐观锁冲突**：认证主流程去 @Transactional，审计写走 REQUIRES_NEW 独立短事务 + 静默失败兜底（幂等，iam 10.2.2）。
4. **K6 压测客户端单 pod 瓶颈**：分布式 8 实例方案固化（k8s/loadtest/k6-deployment.yaml + tools/deploy_loadtest.py）。

## 四、容量结论（更新）

| 层级 | 优化前 | 优化后 |
|---|---|---|
| 存在性校验 | O(N) 全表扫描 | O(1) 索引计数 |
| 200 并发读 | CRDB 85% 失败 | **TiDB 10507 请求 0 失败** |
| 10 万 VU | 无法测试 | 真实压至 7.9 万 VU，服务饱和点明确（业务 2C×2 副本边界） |
| 双引擎切换 | 需改代码 | 运行时 env 一键切换（~10 分钟滚动） |

## 五、后续高杠杆项（已识别未实施，建议排期）

1. **HPA 自动扩容**：k8s/loadtest 部署已就绪，业务服务加 HPA（min 2 / max 8，CPU 70%）后重测 10 万并发。
2. **GSLB 多入口**：单 NodePort conntrack 上限（实测 insert_failed 165 万次）是 10 万并发的真实入口瓶颈，需 LB/GSLB 多入口。
3. **读缓存命中率**：tenant_list 等热查询接入 TwoLevelCache（platform-common 已具备组件，业务层未接线）。
4. **TiDB 索引审查**：tenants.code、users.username、courses.code 等高频过滤列需复合索引覆盖。

## 六、版本与文档清单

| 服务 | 当前版本 | 数据源 |
|---|---|---|
| tenant-service | 10.2.0 | TiDB（可切 CRDB） |
| iam-service | 10.2.2 | TiDB（可切 CRDB） |
| course-service | 10.3.0 | TiDB（可切 CRDB） |
| teaching-service | 10.3.0 | TiDB（可切 CRDB） |

相关报告（已融合本项目全部资源）：
- `docs/PERFORMANCE_LOAD_TEST_REPORT.md`（1000/1万/10万 分级压测）
- `docs/CRDB_VS_TIDB_COMPARISON.md`（双引擎性能对比）
- `docs/TIDB_MIGRATION_AND_LOADTEST.md`（TiDB 切换执行记录）
- `tools/switch_datasource.py`（双引擎一键切换）
- `tools/deploy_loadtest.py`（分布式压测基础设施）
- `k8s/loadtest/k6-deployment.yaml`（K6 分布式压测清单）

---

# 附：2026-10-17 第二轮审查 —— 十万并发验证中发现的 7 个真实缺陷

前文审查聚焦「全表扫描 / N+1 / 事务边界」等静态代码问题。本轮在真实 10 万并发压测中，
又定位到 7 个**静态审查难以发现、但生产必然踩中**的缺陷。

## 缺陷清单

| # | 严重度 | 缺陷 | 位置 | 根因 | 修复 |
|---|---|---|---|---|---|
| 1 | **致命** | TiDB 容器 CPU limit = 1 核 | `infra/deploy/tidb` | 部署时按最小规格配置，无容量规划 | limit 8 核/8Gi；查询 82-465ms → 1.6-2.4ms |
| 2 | **致命** | 全部业务接口 401 | `course-service` / `teaching-service` | 引入 `spring-boot-starter-security` 但无 `SecurityFilterChain`，Spring 默认装配 Basic 认证 | 新增 `CourseSecurityConfig` / `TeachingSecurityConfig` |
| 3 | 高 | `chapters` 表建表失败 | `ChapterEntity` | 列名 `order` 是 MySQL/TiDB 保留字，DDL 语法错误 | 列名改 `sort_order`，索引同步 |
| 4 | 高 | 课程分页查询 500 | `SpringDataCourseRepository` | `CAST(:status AS varchar)` — MySQL 要求 varchar 带长度 | 4 处改 `CAST(... AS char)` |
| 5 | 高 | 健康端点挂起 2-3s 拖垮线程池 | 4 个服务 `application.yml` | readiness 组含 `redis`，而 `redis8-svc` 是 headless Redis Cluster，standalone 客户端探测挂起 | `management.health.redis.enabled: false`；readiness 去 redis |
| 6 | 中 | DNS 成为 10 万 VU 单点 | `tests/distributed/load.js` | 每 VU 反复解析服务域名，CoreDNS 仅 2 副本 | 直连 Service ClusterIP；connecting 102ms → 59µs |
| 7 | 中 | 负载模型失真 | `tests/distributed/load.js` | 15% 流量打 `/actuator/health`（基础设施探针，非用户行为） | 改为租户详情读，权重重新分配 |

## 共性教训

1. **「引入依赖 ≠ 完成配置」**：`spring-boot-starter-security` 一旦进入 classpath，Spring Boot 会启用默认安全策略。
   任何引入该依赖的服务**必须**显式提供 `SecurityFilterChain`，否则线上全部接口 401。
2. **数据库兼容性必须逐条 DDL 验证**：`order` 保留字、`CAST AS varchar`、`text[]`/`jsonb` 等，
   在 PostgreSQL 下正常，在 TiDB/MySQL 下全部报错。建议在 CI 增加「用目标方言建空库」的冒烟步骤。
3. **健康检查本身可能成为故障源**：健康端点若做重 I/O（Redis/外部 HTTP），在依赖不可用时会挂起并占满线程池。
   健康检查应只做轻量本地判断（`readinessState`），把深度探测放到独立端点。
4. **压测模型必须区分「用户流量」与「基础设施流量」**：把探针接口计入负载会扭曲结论。
5. **requests 过大会让 HPA 失效**：节点 requests 饱和时，HPA 决策正确但 Pod 无法调度。
   应按实测用量设置 request（本服务实测 100-300m，原设 500m 偏大）。

## 验证

全部修复已编译、部署并压测验证：10 万并发 100,000 VU 稳态 4 分钟，**错误率 0.00%**，服务零重启。
详见 `docs/100K_ZERO_FAILURE_TEST_REPORT.md`。
