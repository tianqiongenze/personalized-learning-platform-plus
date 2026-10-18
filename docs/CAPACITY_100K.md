# 10 万并发容量规划（CAPACITY_100K）

> 目标：支撑 **10 万并发在线用户（CCU）**，峰值 **10 万 QPS**，P99 < 500ms，可用性 ≥ 99.95%。
> 本文所有参数与 `k8s/base/*` 清单一一对应（HPA/KEDA/PgBouncer/Redis/PVC/告警阈值）。

## 1. 并发模型与流量假设

| 参数 | 取值 | 说明 |
|---|---|---|
| 并发在线用户 CCU | 100,000 | 峰值同时在线 |
| 人均请求频率 | 0.5 req/s | 含心跳/轮询/交互，教学场景偏读 |
| 平均 QPS | 50,000 | CCU × 0.5 |
| 峰值系数 | 2.0 | 上课/考试开始时刻脉冲 |
| **峰值 QPS** | **100,000** | 设计目标 |
| 读写比 | 8 : 2 | 课程/知识点/路径以读为主 |
| 长连接 | 100,000 条 | `realtime-gateway` SSE/WS |
| 单区域承载 | 50% | 两区域多活均摊（region-a 主 / region-b 备） |

## 2. 分层 QPS 估算

```
                     100,000 QPS(峰值)
                            │
              ┌─────────────┴─────────────┐
              ▼                           ▼
     CDN / Redis 直出 30%          业务 API 70% = 70,000 QPS
        = 30,000 QPS                     │
                                          ├─ graphql-gateway  40,000 QPS(聚合入口, 含 GraphQL 合并)
                                          ├─ iam-service      20,000 QPS(鉴权, 命中缓存后 ~25% 落库)
                                          ├─ course-service   25,000 QPS(最热)
                                          ├─ teaching-service 15,000 QPS(路径规划)
                                          ├─ tenant-service    5,000 QPS
                                          ├─ kt-service        5,000 QPS(知识追踪推理)
                                          ├─ ai-service        2,000 RPS(异步, 转 Pulsar)
                                          ├─ agent-service     1,000 RPS(多步编排, 转 Pulsar)
                                          ├─ ops-service         200 QPS(管理端)
                                          └─ realtime-gateway 20,000 msg/s + 10 万长连接
```

> 单区域（region）承载约一半：峰值 50,000 QPS。下表按**单区域**计算，双区域叠加满足 10 万。

## 3. 各层单副本能力与所需副本数

单副本能力取压测基线（Spring Boot 4vCPU/8Gi、P99<500ms、CPU 65% 目标下）保守估计：

| 服务 | 单区域峰值 QPS | 单副本能力 | 理论副本 | HPA min/max | 结论 |
|---|---:|---:|---:|---|---|
| graphql-gateway | 20,000 | 800 QPS | 25 | 4 / 60 | ✅ |
| iam-service | 10,000 | 1,200 QPS | 9 | 4 / 60 | ✅ |
| course-service | 12,500 | 500 QPS | 25 | 6 / 60 | ✅（峰值接近 50% 上限，见 §7 瓶颈） |
| teaching-service | 7,500 | 600 QPS | 13 | 4 / 60 | ✅ |
| tenant-service | 2,500 | 1,000 QPS | 3 | 4 / 60 | ✅ |
| kt-service | 2,500 | 200 QPS | 13 | 3 / 60 | ✅ |
| ai-service | 1,000 RPS | 40 RPS | 25 | 3 / 60（KEDA） | ✅ |
| agent-service | 500 RPS | 30 RPS | 17 | 3 / 60（KEDA） | ✅ |
| realtime-gateway | 5 万连接 | 5,000 连接 | 10 | 4 / 60 | ✅ |
| ops-service | 100 | 300 QPS | 1 | 2 / 20 | ✅ |

**结论**：`hpa-100k.yaml` 的 max 60 副本对全部核心服务留有 ≥ 2× 余量（course-service 峰值 25/60）。
`pdb.yaml` 的 `minAvailable: 50%` 保证滚动升级时每服务至少半数在线。

## 4. 节点数量估算

按 **32 vCPU / 128 GiB** 计算节点，目标利用率 70%，按生产 patch 的 requests 汇总（单区域）：

| 组件 | 副本 | requests(vCPU/Gi) | 小计 vCPU | 小计 GiB |
|---|---:|---|---:|---:|
| course-service | 25 | 2 / 4 | 50 | 100 |
| graphql-gateway | 25 | 1 / 2 | 25 | 50 |
| iam-service | 9 | 1 / 2 | 9 | 18 |
| teaching-service | 13 | 1 / 2 | 13 | 26 |
| tenant-service | 3 | 1 / 2 | 3 | 6 |
| kt-service | 13 | 0.2 / 0.5 | 2.6 | 6.5 |
| ai-service | 25 | 0.5 / 1 | 12.5 | 25 |
| agent-service | 17 | 0.25 / 0.75 | 4.25 | 12.75 |
| realtime-gateway | 10 | 0.05 / 0.03 | 0.5 | 0.3 |
| **业务合计** | | | **≈ 120 vCPU** | **≈ 245 GiB** |

- 业务计算节点：`max(120/32, 245/128) / 0.7 ≈ max(3.75, 1.9)/0.7 ≈ **6 节点**`
- 中间件节点（每区域，独立池）：
  - CRDB 3 副本（2vCPU/4Gi）→ 2 节点
  - Pulsar（zk1 + bookie2 + broker2）→ 2 节点
  - Milvus（etcd/minio/standalone）→ 2 节点
  - Nebula（metad/graphd/storaged）→ 2 节点
  - Redis Cluster 12 节点（0.25vCPU/0.5Gi）→ 2 节点
  - 监控栈（Prometheus/Grafana/Loki/Alertmanager/KSM/node-exporter）→ 2 节点
  - **小计 ≈ 10 节点**
- **单区域合计 ≈ 16 节点；双区域多活 ≈ 32 节点**（含控制面/ingress 冗余）。

> 弹性峰值：HPA 打满 60 副本时业务 vCPU 需求约 2.5×，由 **Cluster Autoscaler** 自动扩容节点
> （建议每区域预留 4 节点弹性池，对应 `HPA 触顶告警 HpaAtMaxReplicas`）。

## 5. 连接池预算公式

### 5.1 数据库（CockroachDB + PgBouncer）

```
应用侧连接需求 = 副本数 × 单副本连接池
              = 60(峰值) × 20 = 1,200 条  → 直连 CRDB 不可行(每连接内存 + 上下文切换)

经 PgBouncer(transaction 模式) 后端连接 = PgBouncer 副本数 × default_pool_size
              = 3 × 40 = 120 条  → 仅为前端的 1/10
```

| 参数 | 取值 | 位置 |
|---|---:|---|
| 前端 `max_client_conn` | 2,000 / 副本 | `pgbouncer.yaml` |
| 后端 `default_pool_size` | 40 / 库 | `pgbouncer.yaml` |
| 后端连接总量 | 3 × 40 = **120** | 远低于 CRDB 上限 |
| CRDB 连接内存占用 | 120 × ~10 MiB ≈ **1.2 GiB** | 可接受 |
| 触发扩容 | 连接池使用率 > 85% | `CrdbConnectionPoolHigh` 告警 |

> 应用连接串改为 `jdbc:postgresql://pgbouncer.infra:6432/<db>`；若依赖会话级特性改 `pool_mode=session`
> 并同步下调 `default_pool_size`。

### 5.2 Redis 缓存预算

```
总缓存容量 = master 分片数 × 单分片 maxmemory
           = 6 × 1500 MiB ≈ 9 GiB
目标命中率 ≥ 90%  →  热数据(课程目录/知识点/租户配置/鉴权)需 ≤ 8 GiB
```

- 分片：`redis-cluster.yaml` 6 主 6 从，`--cluster-replicas 1`，16384 slot 均分 6 片。
- 多 key 操作须带 hash tag `{tenantId}` 避免跨 slot。
- 命中率 < 70% 触发 `RedisLowHitRatio` 告警。

### 5.3 消息积压与消费者

```
Pulsar 消费能力 = 消费者副本 × 单副本消费速率
ai-service:   60 × 40 = 2,400 RPS  > 峰值 2,000 RPS ✅
agent-service: 60 × 30 = 1,800 RPS > 峰值 1,000 RPS ✅
扩容阈值: ai backlog > 200 / agent backlog > 100  → KEDA 扩容(keda.yaml)
```

## 6. 存储容量（引用 `pvc.yaml`）

| 组件 | 容量 | StorageClass | 依据 |
|---|---:|---|---|
| CRDB | 100 Gi | platform-ssd | 业务库 + 索引，按 100k 用户 × 5 年估算 |
| Milvus | 200 Gi | platform-ssd | 向量索引（知识库/资源向量，含副本） |
| Pulsar bookie | 100 Gi | platform-ssd | 消息保留 7 天 × 峰值吞吐 |
| Nebula storaged | 50 Gi | platform-ssd | 知识图谱（33618 产业图） |
| MinIO | 50 Gi | platform-standard | 对象存储（Milvus 后端 + 资源文件） |
| Prometheus | 100 Gi | platform-standard | 30 天 retention × 高基数指标 |
| Loki | 100 Gi | platform-standard | 30 天日志 |
| Grafana | 10 Gi | platform-standard | 看板/用户 |

> StorageClass 均开启 `allowVolumeExpansion`，磁盘使用率 > 85% 触发 `DiskUsageHigh` 告警。

## 7. 瓶颈识别与扩容策略

| # | 瓶颈 | 现象 | 缓解 / 扩容策略 |
|---|---|---|---|
| 1 | **course-service CPU** | 峰值 25 副本（HPA max 60 的 42%） | 优先加缓存（Redis 直出 30%→45%）、读写分离；再扩 `maxReplicas` 并加节点 |
| 2 | **CRDB 写吞吐** | 写 P99 升高、40001 重试增多 | PgBouncer 削峰；按 `tenantId` 分区/`CONFIGURE ZONE` 就近写；多区域分片 |
| 3 | **LLM 推理（ai-service）** | `ModelInferenceQueueBacklog` 告警 | KEDA 按 backlog 扩容；请求合并/批处理；结果缓存；限流降级 |
| 4 | **Redis 热 key / 单分片** | 某分片 CPU 饱和、命中率下降 | hash tag 打散、读写分离（replica 就近读）、必要时扩分片 |
| 5 | **Ingress / 网关** | 5xx 上升、连接排队 | nginx 横向扩容；`limit-rps` 限流；GSLB 就近分流 |
| 6 | **Pulsar bookie 磁盘 IO** | 写延迟抖动 | bookie 独立 SSD、增 bookie 数、调 `ackQuorum` |
| 7 | **HPA 触顶** | `HpaAtMaxReplicas` 告警 | Cluster Autoscaler 加节点 + 上调 `maxReplicas`（>60） |
| 8 | **长连接内存** | realtime-gateway OOM | 提升单副本连接上限或增副本；`topologySpreadConstraints` 打散 |

### 扩容链路（自动）

```
流量↑ → 业务 HPA(CPU 65%)/KEDA(队列) 扩容 Pod
      → 节点资源不足 → Cluster Autoscaler 加节点
      → HPA 触顶(HpaAtMaxReplicas 告警) → 人工上调 maxReplicas / 优化
      → 磁盘/连接池接近阈值(DiskUsageHigh/CrdbConnectionPoolHigh) → 扩 PVC / 调池
```

### 降级策略

- AI 生成超时率 > 5%（`AiGenerationTimeout`）→ 熔断非核心生成、返回缓存结果。
- 单区域故障 → GSLB 切流至对端区域（RTO < 60s），存活区 HPA 自动补足。
- 租户配额 > 90%（`TenantQuotaExceeded`）→ 对该租户限流，保全局。

## 8. 关键指标 SLO

| 指标 | SLO | 告警 |
|---|---|---|
| 可用性 | ≥ 99.95% | `ServiceDown` / `NodeNotReady` |
| P99 延迟 | < 500ms | `HighP99Latency`（阈值 2s 兜底） |
| 错误率 | < 1% | `High5xxRate`（阈值 5%） |
| Redis 命中率 | ≥ 90% | `RedisLowHitRatio`（阈值 70%） |
| Pulsar 积压 | < 10,000 | `PulsarBacklogHigh`（阈值 50,000） |
| 连接池使用率 | < 85% | `CrdbConnectionPoolHigh` |
| 磁盘使用率 | < 85% | `DiskUsageHigh` |

## 9. 压测与验证

```bash
# 分层压测(k8s/base/locust.yaml)
kubectl apply -f k8s/base/locust.yaml
# 目标: 单区域 50k QPS / 5 万长连接, 观察 HPA 副本曲线与 P99
kubectl get hpa -n qianrenqianmian -w
```

验证要点：① HPA 在 15s 内响应突发；② KEDA 按 backlog 扩容；③ 任一节点排空时
`pdb.yaml` 生效、服务不中断；④ 告警在阈值内触发并送达。

---

## 附：1 亿级数据准备与 warmup（配套 FULLCHAIN_TEST_REPORT.md）

数据规模准备已工具化并实测校准（详见 `docs/FULLCHAIN_TEST_REPORT.md`）：

```bash
# 数据层: 1 亿行学习交互(CRDB COPY 直插, 预期 < 10 分钟, 3 并发连接)
python tools/datagen_100m.py --rows 100000000 --mode crdb \
  --dsn postgresql://root@cockroachdb.infra:26257/course_db?sslmode=disable

# 缓存层: 20 万热键预热(复用 dify-plus Redis Cluster, hash tag 租户分片)
python tools/datagen_100m.py --warmup-only \
  --warmup-redis redis://:***@redis8-svc.dify-plus.svc.cluster.local:6379/0
```

- 实测基准（本地 seed=42）：CSV 生成 37,379 rows/s；fakeredis warmup 10,195 keys/s、命中率 100%。
- 热键清单 `data/scale100m/hotkeys.txt` 供 k6/locust 直接加载 —— **压测打的就是 warmup 预热的真实热键**。
- 建议将 warmup 纳入 deploy-plus.sh verify 阶段后置钩子 + 每日 06:00 定时预热。
