# ADR-005 TiFlash 分析型读路径(HTAP 列存)

| 项 | 值 |
|---|---|
| 编号 | ADR-005 |
| 标题 | TiFlash 分析型读路径(HTAP 行转列) |
| 状态 | Accepted(读路径设计)/ Partial(副本同步 DEFERRED, 见 §6) |
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

## 6. 实测状态(2026-10-18, 诚实记录 / HSA 铁律)

| 验证项 | 结果 |
|---|---|
| `SET TIFLASH REPLICA 1`(teaching_db 5 表) | ✅ 执行成功, `INFORMATION_SCHEMA.TIFLASH_REPLICA` 有记录(REPLICA_COUNT=1) |
| replica AVAILABLE | ❌ **=0** — tiflash store(10057)Up 但 `Cannot lock file /data/status` 残留锁致历史实例反复 CrashLoop, PD 中积累 6 个 Down 态 tiflash store(8684/8694/8723/9001/10055/10057)未回收 |
| `EXPLAIN ... GROUP BY` | `cop[tikv]`(预期, replica 不可用时优化器正确回退行存, 降级路径闭环) |
| MPP 变量 | `tidb_allow_mpp=ON`, `tidb_enforce_mpp=OFF`, `tiflash_replica_read=all_replicas` ✅ |

**恢复步骤(DEFERRED, 操作时遵守 BRR 铁律: 先 backup / 单 pod 试点 / 每步可回滚)**:

1. `tiup ctl:v8.5.8 pd -u http://pd-0.infra:2379 store delete <id>` 回收 6 个 Down store(或等 PD max-store-down-time 自动置 Offline)。
2. 核实 `tiflash-data` PVC 上无存活进程后, 清理 `/data/status` 锁文件(或直接重建 PVC — 表为空无数据损失)。
3. 重启 tiflash pod, 观察 store 注册为 Up 且仅 1 个存活 store。
4. `SELECT AVAILABLE, PROGRESS FROM INFORMATION_SCHEMA.TIFLASH_REPLICA` → AVAILABLE=1; `EXPLAIN` 复查 `cop[tiflash]`。
