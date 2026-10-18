# 既有中间件复用说明（INFRA REUSE MAP）

> 版本 v1.0 | 2026-10-14 | 依据：本轮评审反馈"优先使用服务器上基础设施层和其他命名空间下已有中间件"
> 适用范围：`personalized-learning-platform-plus` 的全部 K8s 部署清单、应用配置与运维操作。

---

## 一、复用原则

| # | 原则 | 说明 |
|---|---|---|
| R1 | **复用优先，绝不重复部署** | 集群中已存在的中间件一律复用；`k8s/base` 不包含任何"再建一套"的资源 |
| R2 | **单一可信源** | 所有外联端点只在 `k8s/base/existing-infra.yaml`（ConfigMap）声明一次，禁止在 `application.yml` 硬编码 |
| R3 | **密钥不落明文** | 口令通过 `external-secrets.yaml` 从既有密钥库/既有 Secret 镜像，或由部署脚本注入 |
| R4 | **探测式部署** | `deploy-rancher.sh` / `deploy-plus.sh` 先探测既有组件（DNS 探活），存在即跳过对应清单的 apply |
| R5 | **自建清单仅作参考** | `redis-cluster.yaml` / `pgbouncer.yaml` 标注"生产勿用"，只在 `overlays/self-contained`（无既有集群的演练环境）选用 |

---

## 二、既有中间件清单（双节点 10.167.2.175 / 176 · K3s v1.28）

| 组件 | 既有位置 | 服务地址（集群内 DNS） | 本平台用途 | 对应清单/配置 |
|---|---|---|---|---|
| **Redis Cluster**（3 主 3 从） | `dify-plus` | `redis8-svc.dify-plus.svc.cluster.local:6379` | 二级缓存 L2、限流计数、会话、租户→区域映射 | `existing-infra.yaml`：`REDIS_HOST/REDIS_PORT` |
| **CockroachDB**（3 节点） | `infra` | `cockroachdb.infra.svc.cluster.local:26257` | 核心业务库（PostgreSQL 线协议兼容） | `existing-infra.yaml`：`CRDB_JDBC_HOST_PORT` |
| **Apache Pulsar**（broker+bookie+zk） | `infra` | `pulsar-broker.infra.svc.cluster.local:6650`（broker）/ `:8080`（HTTP） | 事件总线、AI 生成异步削峰、A2A 异步队列 | `existing-infra.yaml`：`PULSAR_*` |
| **Milvus**（etcd+minio+standalone+attu） | `infra` | `milvus.infra.svc.cluster.local:19530` | RAG 向量检索、资源向量索引 | `existing-infra.yaml`：`MILVUS_*` |
| **NebulaGraph**（metad/graphd/storaged） | `infra` | `nebula-graphd.infra.svc.cluster.local:9669` | "33618"产业知识图谱 `kg33618` | `existing-infra.yaml`：`NEBULA_*` |
| **Prometheus / Grafana / Loki**（kube-prometheus-stack） | `monitoring` | `prometheus.monitoring:9090` / `grafana.monitoring:3000` / `loki-stack.monitoring:3100` | 指标、看板、日志留存（等保 ≥6 月） | `existing-infra.yaml`：`*_URL` |
| **LLM 推理（OpenAI 兼容）** | `ai-platform` | `ollama-master.ai-platform.svc.cluster.local:11434/v1` | 资源生成、教学助手、审核 Agent | `existing-infra.yaml`：`LLM_BASE_URL/LLM_MODEL` |

**已部署清单中与之"应剔除/跳过"的项**（避免与既有组件冲突）：

| 清单文件 | 处置 | 理由 |
|---|---|---|
| `k8s/base/redis-cluster.yaml` | **不纳入 base** | 已有 3 主 3 从 Redis Cluster；再建会争抢 slot 与内存 |
| `k8s/base/pgbouncer.yaml` | **不纳入 base** | 独立连接池按需叠加；默认复用既有 CRDB |
| `k8s/base/prometheus.yaml` / `grafana.yaml` | **不纳入 base** | 复用 `monitoring` 既有栈 |
| `k8s/base/cockroachdb.yaml` / `pulsar.yaml` / `milvus.yaml` / `nebula.yaml` | **探测后跳过** | 部署脚本按 DNS 探活决定是否 apply |

---

## 三、应用侧如何复用（配置接线）

```yaml
# 业务服务 Deployment: envFrom 引用复用声明, 不写死中间件地址
envFrom:
  - configMapRef:
      name: platform-existing-infra        # 既有中间件端点目录(qianrenqianmian)
  - secretRef:
      name: platform-plus-secrets          # 由 external-secrets.yaml 同步(非明文)
```

对应 `application.yml` 仅保留占位符（真实值来自 ConfigMap）：

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST}
      port: ${REDIS_PORT}
      password: ${REDIS_PASSWORD:}
  datasource:
    url: jdbc:postgresql://${CRDB_JDBC_HOST_PORT}/course_db?sslmode=${CRDB_SSLMODE}
    hikari:
      maximum-pool-size: 15               # 连接预算见 docs/CAPACITY_100K.md
```

> 注：Redis 以 standalone 协议经 K8s Service VIP 接入既有 Cluster（Lettuce cluster topology refresh 在 NodePort 场景下解析失败，见 `DEEP_ANALYSIS_FLYWAY_CACHE_JPA.md` §2.5）；多 key 操作必须带 hash tag `{tenantId}`。

---

## 四、探测与验证命令（运维）

```bash
# 1. 探测既有中间件是否就绪
for d in redis8-svc.dify-plus.svc.cluster.local:6379 \
         cockroachdb.infra.svc.cluster.local:26257 \
         pulsar-broker.infra.svc.cluster.local:6650 \
         milvus.infra.svc.cluster.local:19530 \
         nebula-graphd.infra.svc.cluster.local:9669; do
  h=${d%%:*}; p=${d##*:};
  kubectl -n qianrenqianmian run probe-$RANDOM --rm -it --restart=Never \
    --image=busybox:1.36 -- nc -z "$h" "$p" && echo "OK $d" || echo "MISS $d";
done

# 2. 确认没有重复部署 Redis/Prometheus
kubectl get statefulset -A | grep -Ei 'redis|prometheus|grafana'

# 3. 确认业务服务连的是既有集群
kubectl -n qianrenqianmian exec deploy/tenant-service -- env | grep -E 'REDIS_HOST|CRDB_JDBC|PULSAR_SERVICE'
```

---

## 五、变更纪律（TOGAF Phase G 治理）

1. 新增中间件**先查本清单**；仅当既有组件在容量/版本/隔离性上确实不满足时才申请新增，并在此登记。
2. 地址变更只改 `k8s/base/existing-infra.yaml`，改后滚动重启业务服务（`kubectl rollout restart deploy -n qianrenqianmian --all`）。
3. 既有集群的容量水位（Redis 内存/命中率、CRDB CPU/连接数、Pulsar backlog）纳入 `monitoring/rules` 告警，纳入 10 万并发容量规划的基线。
4. 任何"自建参考清单"被用于生产前，必须在本文件登记原因与回收计划。

---

*本文件是"复用优先"原则的执行依据；与 `docs/CAPACITY_100K.md`（容量规划）配套使用。*

---

## 六、数据规模准备与 warmup 的复用接线

数据层/缓存层规模数据工具 `tools/datagen_100m.py` 按 §三 配置复用既有中间件：

- **CRDB**（infra）：`--mode crdb --dsn postgresql://root@cockroachdb.infra:26257/<db>`，COPY 批量直插 1 亿行。
- **Redis**（dify-plus）：`--warmup-redis redis://...@redis8-svc.dify-plus...`，pipeline 预热 20 万热键；键前缀 `t:{tenant}:` 与 TwoLevelCache 约定一致，hash tag 保证租户键落同分片。
- **Pulsar**（infra）：warmup 后压测产生的 AI 异步生成事件经既有 `pulsar-broker.infra` 削峰，backlog 由 `monitoring/rules` 告警。
- 预热与压测共用 `data/scale100m/hotkeys.txt`（seed=42 确定性），保证压测流量命中预热的真实热键 → 缓存命中率指标可信。

详见 `docs/FULLCHAIN_TEST_REPORT.md`（实测基准与 1 亿行 runbook）。
