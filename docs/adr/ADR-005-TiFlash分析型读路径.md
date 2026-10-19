# ADR-005 TiFlash 分析型读路径(HTAP 列存)

| 项 | 值 |
|---|---|
| 编号 | ADR-005 |
| 标题 | TiFlash 分析型读路径(HTAP 行转列) |
| 状态 | Accepted(读路径设计 + 副本同步已修复, 见 §6) |
| 日期 | 2026-10-18 |
| 关联 | ADR-004(双存储切换)、docs/ANALYSIS_TIFLASH.md(逐模块适配性分析)、k8s/base/tidb.yaml |
| Supersedes | 无 |

---

## 背景

ESP 转全量 CRDB 时最大的架构代价是"HTAP 学情分析路径丢失且无替代方案"(ADR-24 技术债第 1 条)。PLPP 的 infra ns 中 TiDB v8.5.8 集群已带 tiflash 组件, 分析型读路径可以库内列存方式补齐, 不需引入外部数仓(ClickHouse 等)。

## 决策

**分析型读统一走 TiDB 行存(TiKV)上的 TiFlash 列存副本; OLTP 读写在 TiKV, 报表/聚合在 TiFlash。**

### 1. 候选表与副本策略(与 ANALYSIS_TIFLASH.md §3/§5 一致)

| 表 | 建副本 | 理由 |
|---|---|---|
| teaching_db.assignments / exams | ✅ 已执行 `SET TIFLASH REPLICA 1` | 教师端报表聚合, 100 万行量级收益 3-5× |
| teaching_db.discussions / live_sessions / learning_path_steps | ✅ 已执行(历史批量) | 表空无代价; 无报表需求则 Phase2 回收 |
| tenant_db.credit_transfer_records | ⏳ Phase2 | 行数 < 10 万收益≈0 |
| kt_answer_events(规划表) | ✅ 建表即建 | KT 批量评测统计, 亿行窄表收益 5-20×, PLPP 的"ESP learning_events 对应物" |
| iam_db.users / roles、event_outbox、course_db 全部 | ❌ 显式不建 | 点查强一致 / 高频点写 / 维表, 列存负收益 |

### 2. 查询路由规则

1. `tidb_allow_mpp=ON`(已确认生效)、`tidb_enforce_mpp=OFF` — 优化器按代价自动选择, 小表退回 TiKV 是正确行为。
2. 报表 SQL 模板统一带 Hint `/*+ READ_FROM_STORAGE(TIFLASH[表名]) */` 固定列存路径, 不依赖统计信息成熟度。
3. 验证标准: `EXPLAIN` 出现 `cop[tiflash]`; **当前实测仍为 `cop[tikv]`**(replica AVAILABLE=0, 诚实记录), AVAILABLE=1 后复查。

### 3. kt_answer_events DDL 模板(Phase2)

```sql
CREATE TABLE kt_answer_events (
  id           CHAR(36) NOT NULL,
  student_id   CHAR(36) NOT NULL,
  course_id    CHAR(36) NOT NULL,
  skill_id     BIGINT   NOT NULL,
  is_correct   TINYINT(1) NOT NULL,
  answer_ms    INT      NOT NULL,
  answered_at  DATETIME(6) NOT NULL,
  tenant_id    CHAR(36) NOT NULL,
  PRIMARY KEY (id) NONCLUSTERED,
  KEY idx_kt_student_time (student_id, answered_at),
  KEY idx_kt_course_skill (course_id, skill_id)
);
ALTER TABLE kt_answer_events SET TIFLASH REPLICA 1;
```

写入: 批量(bulk insert, 非点更新); 读: Hint 走 TiFlash。

## 迁移动因

1. ESP ADR-24 明确把"HTAP 丢失"列为技术债并要求补补偿方案 — 本 ADR 即补偿落地。
2. TiFlash 集群组件已在 infra ns 运行且版本三态一致(TiDB/TiFlash 均 v8.5.8, SVD 铁律达标), 增量成本仅为副本存储与同步。
3. PLPP 分析型负载(KT 评测/报表)与点写路径天然表隔离, 无 HTAP 资源争抢问题。

## 代价 / 技术债

- 副本存储翻倍(仅限建副本的表); raft log 同步秒级延迟, 分析读有陈旧窗口。
- tiflash 单副本单 pod, 无高可用(单节点环境 DEFERRED, 多节点后 replicas≥2)。

## 回切条件

① TiFlash 稳定性不可修复(见 §6)→ Hint 摘除, 分析读退回 TiKV 行存(功能等价仅变慢); ② 数据量始终低于收益阈值 → 回收全部 replica, 本 ADR 标记 Deferred-by-capacity。回退零数据损失(列存是副本非主存)。

---

## 6. 实测状态(2026-10-19 终态, 诚实记录 / HSA 铁律)

| 验证项 | 结果 |
|---|---|
| `SET TIFLASH REPLICA 1`(teaching_db 5 表 + esp.users, 共 6 表) | ✅ 执行成功, `INFORMATION_SCHEMA.TIFLASH_REPLICA` REPLICA_COUNT=1 × 6 |
| replica AVAILABLE | ✅ **=1 × 6** — store 10162 Up, `peer_address=<POD_IP>:20170`, `tiflash_proxy_tikv_raftstore_region_count{type="region"}=6` |
| MPP 列存读 | ✅ `SELECT /*+ READ_FROM_STORAGE(TIFLASH[users]) */ COUNT(*) FROM esp.users` = 1000(经 TiFlash 副本返回) |
| `EXPLAIN ... GROUP BY` | replica 不可用时优化器自动回退 `cop[tikv]`(降级路径闭环, 修复期间实证) |
| MPP 变量 | `tidb_allow_mpp=ON`, `tidb_enforce_mpp=OFF` ✅ |

### 6.1 根因链复盘(AVAILABLE=0 → 1, 完整证据)

1. **陈旧空文件挂载**: 早期 Deployment 在容器内以 subPath 挂载 `/data/proxy.toml`(指向 ConfigMap), 该挂载移除后 **PVC 上残留 0 字节 `/data/proxy.toml`**。
2. **配置路径误导**: `[proxy].config` 指向 `/data/proxy.toml` → proxy 读到空文件 → 所有 addr 回退默认值。
3. **127.0.0.1 注册**: proxy 以 `addr=127.0.0.1:20170` 向 PD 注册 `peer_address` → 其他节点 tikv leader 连 `127.0.0.1:20170` 全部 Connection refused → learner 收不到任何 raft 消息/快照 → `region_count=0` → AVAILABLE=0。
4. **store-ID 漂移**: 每次重启因 "duplicated store address" FATAL 换新 store ID(10055→10057→…→10155), PD 积累 10+ 个 Offline/Down store。
5. **死锁**: PD 拒绝 tombstone Offline store(region 上仍挂 learner peer), 而 learner 无法搬迁到新 store(tiflash 单副本且 pod 起不来)— **单副本 tiflash 的 placements-rule 死锁**。
6. **gen8 修复关键**: 按 TiDB v8.5 官方文档, proxy 的 raft addr 必须写在**主 config.toml 的 `[flash.proxy]` 段**(键: `addr`/`advertise-addr`/`status-addr`), **不是** `[proxy]` 段、也**不消费**外部 proxy.toml 的这些键; `[proxy].config` 仅用于 proxy 其余参数。
7. **placement 手术**: 临时 DELETE 全部 tiflash placement rules → PD 立即清掉 6 region 上的死 learner peer → 两个 Offline store 成功 tombstone → 恢复 rules → 新 store 10162 获得 6 个 learner → 快照回放 → AVAILABLE=1。
8. **`/data/status` 锁**: CrashLoop 时残留 PID 锁, 需在 PVC 上 `rm /data/status` 后重启(已纳入 Deployment 收尾流程考量)。

### 6.2 运维红线(写入 k8s/base/tidb.yaml 注释与本文)

- 改 proxy raft addr 只改主 config.toml `[flash.proxy]` 段(gen-config initContainer 已固化该逻辑)。
- tiflash pod 异常重启循环时: 先检查 `/data/status` 残留锁 → 再查 PD `duplicated store address` → 必要时 placement-rule 临时清空(操作顺序见 6.1 第 7 步, 遵守 BRR: 规则先 backup 再动)。
- 多节点扩容时 tiflash replicas≥2 以消除单副本 placement 死锁模式。

### 6.3 历史修复记录(过程证据, 备份于 10.167.2.175:/root/plpp-backup-20261019/)

gen2→gen8 共 7 次 init 脚本迭代; 每次均先 `kubectl get deploy tiflash -o yaml` 落盘备份再 patch; 最终态已 DGO 回写 `k8s/base/tidb.yaml`(gen-config initContainer 生成 `[flash.proxy]` 段 + 移除 `/data/proxy.toml` subPath 挂载 + 移除失效 tiflash-proxy-config ConfigMap)。
