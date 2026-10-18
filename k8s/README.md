# k8s 部署清单（面向 10 万并发 · 多区域多活 · 可观测告警）

## 1. 目录结构

```
k8s/
├── base/                         # 全量基础清单(kustomize base)
│   ├── kustomization.yaml        # ★ 汇总全部 base 资源 + patches
│   ├── namespaces.yaml           # qianrenqianmian / infra / monitoring / load-testing / ingress-nginx
│   ├── configmap-secret.yaml     # 配置 + 明文密钥(生产须替换, 见 §4)
│   ├── external-secrets.yaml     # ★ 外部密钥库同步模板(替代明文)
│   ├── storageclass.yaml         # ★ platform-ssd / platform-standard
│   ├── pvc.yaml                  # ★ CRDB/Milvus/Nebula/Pulsar/MinIO/Prometheus/Grafana/Loki
│   ├── pvc-mounts-patch.yaml     # ★ Prometheus/Grafana emptyDir -> PVC
│   ├── hpa-100k.yaml             # ★ 核心服务 HPA 上限 60 + ai/agent/kt
│   ├── keda.yaml                 # ★ ai/agent 队列驱动扩缩(Pulsar/Prometheus)
│   ├── pdb.yaml                  # ★ 核心服务 minAvailable 50%
│   ├── topology-spread.yaml      # ★ 跨节点/跨可用区打散(补丁)
│   ├── redis-cluster.yaml        # Redis Cluster 6 主 6 从
│   ├── pgbouncer.yaml            # ★ CRDB 连接池
│   ├── prometheus-rules.yaml     # ★ ≥12 条告警(PrometheusRule)
│   ├── alertmanager.yaml         # ★ 告警路由/抑制 + 微信/邮件 receiver
│   └── ... (业务/中间件/监控/ingress/network-policies 等原有清单)
└── overlays/
    ├── production/               # 单区域生产基线(引用 ../../base)
    └── regions/
        ├── README.md             # ★ 单元化 + GSLB 就近路由策略
        ├── region-a/             # 主区域(高副本)
        └── region-b/             # 备区域(低副本)
```

## 2. 部署

```bash
# 预览
kubectl kustomize k8s/base | less
kubectl kustomize k8s/overlays/production | less

# 应用(单区域生产)
kubectl apply -k k8s/overlays/production

# 应用(多区域多活, 分别切 context)
kubectl apply -k k8s/overlays/regions/region-a
kubectl apply -k k8s/overlays/regions/region-b
```

> 命名空间：业务 `qianrenqianmian` / 中间件 `infra` / 监控 `monitoring`。
> overlay **不设置** `namespace:`（base 跨三个命名空间，kustomize namespace 转换器会误改全部资源）。

## 3. 依赖的 Operator / CRD

以下清单依赖对应 CRD，未安装时 `kubectl apply` 会报 “no matches for kind”：

| 清单 | 依赖 |
|---|---|
| `keda.yaml` | KEDA Operator (`keda.sh/v1alpha1`) |
| `prometheus-rules.yaml` | prometheus-operator (`monitoring.coreos.com/v1`) |
| `external-secrets.yaml` | External Secrets Operator (`external-secrets.io/v1beta1`) |

若使用纯 Deployment 版 Prometheus（`prometheus.yaml`），将 `prometheus-rules.yaml` 的 `spec.groups`
原样粘贴进该文件的 ConfigMap `prometheus-rules`（`data.platform-alerts.yml`）即可，格式兼容。

## 4. 密钥治理（替换 `configmap-secret.yaml` 中的明文）

`configmap-secret.yaml` 现含明文口令（`JWT_SECRET` / `POSTGRES_PASSWORD` / `MINIO_SECRET_KEY` /
`REDIS_PASSWORD` / `GRAFANA_PASSWORD` / `PULSAR_TOKEN` 等），**禁止进入生产**。二选一：

### 方案 A：External Secrets Operator（推荐）

1. 部署 ESO Operator，并在 Vault 中建立 `platform-plus/*` 密钥路径。
2. 编辑 `k8s/base/external-secrets.yaml`，替换 `ClusterSecretStore` 的 `vault.server` 与 `role`
   （或改用云厂商 provider：AWS SM / 阿里云 KMS / 腾讯云 SSM）。
3. 删除 `configmap-secret.yaml` 中的 **Secret 段**（保留 ConfigMap），仅由 ExternalSecret 生成同名 Secret。
4. `kubectl apply -f k8s/base/external-secrets.yaml`，验证：
   ```bash
   kubectl get externalsecret -A
   kubectl get secret platform-plus-secrets -n qianrenqianmian -o jsonpath='{.data}' | head -c 200
   ```

### 方案 B：Sealed Secrets

```bash
# 生成加密清单(集群公钥需先安装 sealed-secrets controller)
kubectl create secret generic platform-plus-secrets -n qianrenqianmian \
  --from-literal=JWT_SECRET='...' --dry-run=client -o yaml \
  | kubeseal --format yaml > k8s/base/sealedsecret-platform-plus.yaml
# 提交 sealedsecret-*.yaml, 从 kustomization 移除明文 Secret
```

> 密钥轮换后 ESO 会在 `refreshInterval`（默认 1h）内自动同步；应用需支持热加载或滚动重启。

## 5. 这套清单如何支撑 10 万并发

- **弹性**：`hpa-100k.yaml` 将核心服务上限提到 60 副本（原 12–20）；`keda.yaml` 以 Pulsar 积压/在途任务
  驱动 ai/agent 提前扩容。CPU 目标 65%、scaleUp 无稳定窗、scaleDown 300–600s 防抖。
- **容量**：`pvc.yaml` 为 CRDB 100Gi / Milvus 200Gi / Pulsar 100Gi / Prometheus 100Gi / Grafana 10Gi /
  Loki 100Gi 等预留；`redis-cluster.yaml` 6 主 6 从提供 ~9GB 缓存与读扩展。
- **连接**：`pgbouncer.yaml` 以 transaction 模式复用 CRDB 后端连接（2000 前端 → 40/库后端 ×3）。
- **可用性**：`pdb.yaml`（minAvailable 50%）+ `topology-spread.yaml`（跨节点/可用区）保障滚动升级与
  节点故障不损失可用性；`regions/*` 提供跨区域多活与就近路由。
- **可观测**：`prometheus-rules.yaml`（14 条告警）+ `alertmanager.yaml`（分组/抑制/微信邮件）覆盖
  服务宕机、5xx、P99、租户配额、Pulsar 积压、Redis 命中率、CRDB 连接池、HPA 触顶、磁盘、推理队列、
  AI 超时、证书过期等关键信号。
- **交付**：`.github/workflows/ci.yml`（多模块矩阵 + JaCoCo + Trivy）与 `cd.yml`（ArgoCD staging→prod
  人工审批）。

详细容量推导见 [`docs/CAPACITY_100K.md`](../docs/CAPACITY_100K.md)。
