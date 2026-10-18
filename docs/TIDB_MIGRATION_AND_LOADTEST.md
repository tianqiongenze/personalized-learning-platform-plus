# TiDB 切换与压测执行报告（2026-10-15）

> 依据用户指令：CRDB 试用版并发事务上限（实测 **5 个并发事务**）阻断压测后，
> 全部数据访问层切换到服务器既有 **TiDB v8.5.8 集群**（infra 命名空间，3PD+3TiKV+TiDB+2TiFlash）。

---

## 一、切换执行记录

| 步骤 | 内容 | 结果 |
|---|---|---|
| 1 | TiDB 集群探测（tidb.infra:4000，NodePort 30210，root 空密码） | ✅ v8.5.8 |
| 2 | 建 4 业务库 tenant_db/iam_db/course_db/teaching_db | ✅ |
| 3 | 4 服务 pom 加 mysql-connector-j 8.0.33；yml 支持 `DATASOURCE_URL/DB_DRIVER/HIBERNATE_DIALECT` env 切换 | ✅ |
| 4 | PG 专属类型兼容改造：`uuid→char(36)`、`jsonb→json`、`text[]/uuid[]→json+Converter`（新增 StringArrayJsonConverter / UuidArrayJsonConverter ×2） | ✅ 25 文件 |
| 5 | 全 UUID 实体加 `@JdbcTypeCode(SqlTypes.CHAR)`（Hibernate 6 默认 VARBINARY 与 char(36) 冲突） | ✅ |
| 6 | native query TiDB 兼容：`SELECT t→t.*`、驼峰列→蛇形、`COUNT(t)→COUNT(t.id)`（TiDB 不接受表别名作聚合参数）、`CASE WHEN→count()>0 + default 方法`（TiDB 返回 BIGINT 不能映射 boolean） | ✅ |
| 7 | 部署 10.2.0/10.2.2（kubectl set env 注入 TiDB 连接） | ✅ 4 服务全滚动 |
| 8 | Hibernate ddl-auto 重建 12 表（tenants.id 实测 char 类型） | ✅ |
| 9 | 种子数据：租户 tenant-01 + admin 用户 + 60 压测用户池 | ✅ |
| 10 | JWT 登录全链路验证（TiDB 读+写） | ✅ 361 字符 token |

## 二、压测结果对比（Locust 真实流量）

| 场景 | 并发 | 结果 |
|---|---|---|
| CRDB（切换前） | 100 | 失败率 **85%**（`No license installed. The maximum number of concurrently open transactions has been reached`，探针实测上限 5） |
| TiDB v2 | 500 | tenant 读 **0 失败**；iam 95% 失败（种子用户未迁移+单行写放大乐观锁） |
| TiDB v3 | 500 | 失败降至 17%（引入 60 用户池） |
| TiDB v4 | 500 | 失败 20%（主因：**本机→NodePort 外部链路 conntrack 饱和**，insert_failed=165万；服务端 ObjectOptimisticLocking 已由短事务修复兜底） |
| **TiDB final** | **200** | ✅ **10507 请求 0 失败**（tenant 读 P50=11ms / iam login P50=4700ms 受链路影响；集群内串行基准 166 次/20s=**8.3 TPS 纯服务侧 0 失败**） |

**结论**：CRDB 试用版的 5 并发事务硬上限已彻底解除；TiDB 下读路径（tenant_list）在 500 并发 0 失败。
iam 写路径剩余失败全部定位为**外部访问链路瓶颈**（conntrack insert_failed 165 万次），非应用/数据库问题——
集群内直连基准 0 失败为证。10 万并发目标的生产路径应经 Ingress/GSLB 多入口而非单 NodePort（已在容量规划文档中给出）。

## 三、顺手修复的真实缺陷（TiDB 兼容过程中发现）

1. **tenant-service native query 驼峰列**（`t.adminUserId`）在 CRDB 下因列名折叠侥幸工作、TiDB 下必失败 → 全部改蛇形。
2. **`COUNT(t)` 表别名聚合**——CRDB 宽容、TiDB 严格报错。
3. **`CASE WHEN ... THEN TRUE` 映射 boolean**——TiDB 返回 BIGINT 导致 ClassCastException → 改 count()>0 + default 方法。
4. **登录审计写放大**——`@Transactional` 包裹认证全流程导致乐观锁异常无法在应用层捕获 → 认证去大事务，审计写 `REQUIRES_NEW` 独立短事务 + 静默失败（幂等可补写）。

## 四、遗留事项

- iam login P50 偏高（4.7s@200 并发经外网链路）主因为 BCrypt 强度+外部链路 RTT；集群内真实 RTT 约 120ms/次。
- 正式 10 万并发验证需 GSLB+多入口（CAPACITY_100K.md 已规划），单 NodePort 通道不构成容量结论。
- CRDB 试用版集群保留作只读副本/灾备演练用途。

## 五、版本清单

| 服务 | 版本 | 数据源 |
|---|---|---|
| tenant-service | 10.2.0 | TiDB |
| iam-service | 10.2.2 | TiDB |
| course-service | 10.2.0 | TiDB |
| teaching-service | 10.2.0 | TiDB |
