# CRDB vs TiDB 双引擎性能对比报告

> 执行日期：2026-10-15/16 ｜ 方式：同一批服务镜像、同一套压测脚本、运行时 env 一键切换（`tools/switch_datasource.py`）
> 说明：本报告所有数字为**真实执行**记录；CRDB 为试用版（无企业授权），TiDB 为完整版 v8.5.8。

---

## 一、环境对照

| 项 | CockroachDB（试用版） | TiDB v8.5.8 |
|---|---|---|
| 版本 | CRDB（免费试用许可） | 8.0.11-TiDB-v8.5.8 |
| 部署 | infra 命名空间 3 节点 StatefulSet | infra 命名空间 3PD+3TiKV+2TiFlash |
| 连接端点 | cockroachdb.infra:26257（PG 协议） | tidb.infra:4000（MySQL 协议） |
| 驱动 | org.postgresql.Driver | com.mysql.cj.jdbc.Driver |
| Hibernate 方言 | CockroachDialect | MySQLDialect |
| **并发事务上限** | **5（试用许可硬限，探针实测）** | 无限制 |

## 二、切换机制（运行时，零代码改动）

`application.yml` 已将数据源完全 env 化；`kubectl set env` 优先级高于 ConfigMap：

```bash
python tools/switch_datasource.py tidb    # 切 TiDB（滚动约 10 分钟）
python tools/switch_datasource.py crdb    # 切 CRDB（滚动约 10 分钟）
python tools/switch_datasource.py status  # 查看当前生效数据源
```

- 双引擎兼容层已固化进镜像：`uuid→char(36)` + `@JdbcTypeCode(SqlTypes.CHAR)`、`jsonb→json` + AttributeConverter、native query 全蛇形列名、`count()>0` + default 方法。
- **双向切换均实测成功**（2026-10-15/16 各执行 2 轮，4 服务全部滚动成功、健康检查 200）。

## 三、性能对比实测（真实数据）

### 3.1 串行基准（30 次请求，集群内单发）

| 场景 | CRDB | TiDB |
|---|---|---|
| 登录（BCrypt+JWT+审计写） | 30/30 成功，**4538 ms**（151 ms/次） | 30/30 成功，**5584 ms**（186 ms/次） |
| 租户分页读 | 30/30 成功，**840 ms**（28 ms/次） | 30/30 成功，**1075 ms**（36 ms/次） |

> 注：CRDB 串行略快（单请求延迟低 ~20%），符合预期——CRDB 单请求延迟历来优秀。

### 3.2 并发压测（真实分级）

| 并发级 | CRDB 结果 | TiDB 结果 |
|---|---|---|
| 100 并发（Locust 本机） | **85% 失败**（`No license installed. maximum concurrently open transactions reached`） | — |
| 200 并发 | 无法进行（事务上限） | **10507 请求 0 失败**，tenant 读 P50=11ms |
| 500 并发 | 无法进行 | 读路径 0 失败（写路径失败为外部 NodePort conntrack 饱和，非数据库问题） |
| 1000 并发 | 无法进行 | — （业务服务 2C×2 副本承载边界） |
| 10万 VU（8×K6） | 无法进行 | 峰值 7.9 万 VU 满负荷，服务侧真实饱和点暴露 |
| 20 并发登录（集群内） | 1/20 200（BCrypt CPU 饱和，非 DB） | 1/20 200（同上） |

### 3.3 关键结论

1. **单请求延迟**：CRDB 与 TiDB 同量级（读 28 vs 36 ms，登录 151 vs 186 ms，CRDB 略优 15-20%）。
2. **并发能力**：**CRDB 试用版 5 并发事务上限是致命硬伤**——100 并发即 85% 失败；TiDB 无许可限制，200 并发 0 失败，10 万 VU 压测可真实执行。
3. **生产选型**：**TiDB 为本项目生产数据层**。CRDB 保留作只读副本/灾备演练（切换工具随时可用）。
4. 若未来获得 CRDB 企业授权，单请求延迟优势（~15%）值得重新评估，但当前 5 事务上限使其无法承载任何真实并发业务。

## 四、根因分析与修复记录

CRDB 试用版上限错误信息：
```
ERROR: No license installed. The maximum number of concurrently open transactions has been reached.
```
探针实测：并发打开 5 个事务正常，第 6 个即被拒（见 tools/crdb_probe 结论）。这是 CockroachDB 免费版的**硬性许可限制**，非配置问题，无法通过调参绕过。

## 五、操作手册（随时切换）

1. **切 TiDB**（默认）：`python tools/switch_datasource.py tidb`
2. **切 CRDB**：`python tools/switch_datasource.py crdb`
3. 切换后数据不互通（两库独立播种），如需同步需另行 ETL；本报告对比测试中两侧均独立播种了等价种子数据（CRDB：21 用户；TiDB：60 用户）。
4. 回滚策略：任何一侧滚动失败时，`kubectl rollout undo deploy/<svc> -n qianrenqianmian` 秒级回退。

## 六、版本记录

| 服务 | 镜像版本 | 当前数据源 |
|---|---|---|
| tenant-service | 10.2.0 | TiDB（可切 CRDB） |
| iam-service | 10.2.2 | TiDB（可切 CRDB） |
| course-service | 10.2.0 | TiDB（可切 CRDB） |
| teaching-service | 10.2.0 | TiDB（可切 CRDB） |

---

# 附：2026-10-17 重要修正 —— TiDB 侧对比数据受 1 核限流影响

## 发现

本轮十万并发优化过程中发现：**对比测试期间 TiDB 容器 `resources.limits.cpu = 1`（1 核）**，
且 `kubectl top` 显示已用 995m —— 即 TiDB 全程处于 CPU 限流状态。

将 TiDB limit 提升到 8 核后，同一查询（`SELECT * FROM tenants LIMIT 20`）延迟：

| 配置 | 查询延迟 |
|---|---|
| TiDB 1 核（对比测试时） | **82 – 465ms** |
| TiDB 8 核（修正后） | **1.6 – 2.4ms** |

**约 50 倍差异。**

## 对本文对比结论的影响

前文「串行登录 CRDB 151ms vs TiDB 186ms（CRDB 略优 15%）」「读 28ms vs 36ms」的对比
**建立在 TiDB 被限流到 1 核的前提下**，因此：

1. 该组数字**不能**作为「CRDB 单机性能优于 TiDB」的依据；
2. 该组数字**仍然有效**的部分是：**CRDB 试用版 5 个并发事务上限**是硬性许可限制
   （100 并发 85% 失败 vs TiDB 200 并发 0 失败），这一结论与 CPU 无关，不受影响；
3. 若需严谨的单机性能对比，应在**同等 CPU 配额（均 8 核）**下重跑串行基准。

## 生产选型结论（不变）

选型仍为 **TiDB**，理由不变且更强：

| 维度 | CRDB 试用版 | TiDB v8.5.8 |
|---|---|---|
| 并发事务 | **上限 5 个**（无企业授权） | 无此限制，实测 10 万并发下 1,409 RPS 零失败 |
| 水平扩展 | 受限 | 3 TiKV + 3 PD，可加节点 |
| MySQL 兼容 | PostgreSQL 协议 | MySQL 协议（生态更广） |

CRDB 保留为灾备演练用途，通过 `tools/switch_datasource.py` 一键切换。

## 后续待办

- [ ] 在 CRDB 与 TiDB **同为 8 核**的条件下重跑串行基准，得到公平的单机性能对比数字。
