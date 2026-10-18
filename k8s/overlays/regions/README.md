# 多区域多活部署（region-a / region-b）

> 面向 10 万并发：单集群（region）承载 ~5 万并发，两区域多活叠加，容量与容灾同时满足。
> 容量推导见 [`docs/CAPACITY_100K.md`](../../../docs/CAPACITY_100K.md)。

## 1. 部署拓扑

```
                        ┌────────────────────────────┐
        用户 ─────────▶ │  GSLB / 智能 DNS / Anycast │
                        └───────────────┬────────────┘
                  就近 + tenant 哈希    │
             ┌──────────────────────────┴──────────────────────────┐
             ▼                                                     ▼
   ┌───────────────────────┐                          ┌───────────────────────┐
   │  region-a 集群(主)     │                          │  region-b 集群(备)     │
   │  ns: qianrenqianmian   │                          │  ns: qianrenqianmian   │
   │      infra/monitoring  │                          │      infra/monitoring  │
   │  replicas 基线 6~8     │                          │  replicas 基线 3~4     │
   └───────────┬───────────┘                          └───────────┬───────────┘
               │        CRDB geo-partition / Pulsar geo-replication│
               └───────────────────────────────────────────────────┘
```

- **单元化（Unitization）**：以 `tenantId` 为分片键，将租户**归属**到某个 region（主单元）。
  同一租户的写请求始终路由到其主单元，避免跨区写冲突；读请求可就近到任意单元。
- **多活（Active-Active）**：两区域均对外提供服务；region-a 为主单元（基线副本高），
  region-b 为备单元，同时承接就近读流量。任一区域整体故障，GSLB 将流量整体切至另一区域。

## 2. tenant → region 映射

| 维度 | 策略 |
|---|---|
| 分片键 | `tenantId`（如为 UUID，取一致性哈希环；如为数值，按区间） |
| 映射表 | 存于接入层/网关（Redis 或配置中心），键：`tenant:region:{tenantId}` |
| 路由 | `region = hash(tenantId) mod N`（N=区域数）；支持手动覆盖（大客户就近/合规驻留） |
| 就近读 | 读请求默认路由到 GSLB 判定的最近健康区域 |
| 写路由 | 写请求路由到租户主单元；跨区写通过 CRDB 分布式事务/或异步同步 |
| 迁移 | 调整映射表 + CRDB `ALTER ... CONFIGURE ZONE` 迁移租户数据，灰度切流 |

## 3. GSLB 就近路由策略

1. **健康检查**：GSLB 对每个 region 的 `ingress-nginx` 做 L7 健康探测（`/healthz`），
   连续 3 次失败摘除该 region 入口。
2. **就近性**：基于用户 DNS 解析源 IP / EDNS Client Subnet 返回最近 region 的 VIP。
3. **权重**：region-a : region-b = 6 : 4（与副本基线权重一致），故障时自动 100% 切至存活区。
4. **会话粘性**：实时长连接（`realtime-gateway` SSE/WS）按连接建立时的 region 粘滞，
   断线重连由 GSLB 重新就近分配。
5. **优雅降级**：单区容量达 80% 时，GSLB 将新增流量溢出到对端区域（防雪崩）。

## 4. 数据层多活

| 组件 | 多活方式 | 关键参数 |
|---|---|---|
| CockroachDB | 跨区多副本 + `--locality` 区域感知 | overlay 注入 `--locality=region=<r>,zone=<z>`；`CONFIGURE ZONE` 约束副本分布 |
| Pulsar | 各区域独立集群 + Geo-Replication | overlay 注入 `PULSAR_PREFIX_clusterName=<r>`；topic 开启跨区复制 |
| Redis Cluster | 区域本地（不做跨区强一致） | 缓存允许区域间独立；失效回源 CRDB |
| Milvus / Nebula | 区域本地副本 + 离线同步 | 向量/图谱以区域本地服务为主，弱一致可接受 |
| MinIO | 区域本地 + 跨区异步复制 | 对象按 region 前缀分桶 |

## 5. 部署与验证

```bash
# 部署（分别切到对应集群 context）
kubectl config use-context <region-a-context>
kubectl apply -k k8s/overlays/regions/region-a

kubectl config use-context <region-b-context>
kubectl apply -k k8s/overlays/regions/region-b

# 预览渲染结果
kubectl kustomize k8s/overlays/regions/region-a | less

# 校验区域标签注入
kubectl get pods -n qianrenqianmian -L topology.kubernetes.io/region
```

## 6. 故障切换（Runbook 摘要）

1. GSLB 探测到 region 失联 → 自动将 DNS/VIP 指向存活区（RTO < 60s）。
2. 存活区 HPA/KEDA 依据流量突增自动扩容（上限 60 副本/服务）。
3. CRDB 跨区副本自动提升为 leaseholder；Pulsar 消费端按 backlog 扩容。
4. 恢复后按 tenant 映射表灰度回切，避免双写与脑裂。

> 注：`k8s/overlays/production` 为单区域生产基线；`regions/*` 在其之上叠加区域参数，
> 两者共用同一份 `k8s/base`。
