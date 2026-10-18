# 双节点(10.167.2.175 / 10.167.2.176) K8s + Rancher 部署 Runbook

> 前提：两台服务器上的 K8s 集群已由 Rancher 管理；**Redis Cluster、CockroachDB 等已有组件直接复用，禁止重复部署**。

## 命名空间规划(本轮修订)

| 命名空间 | 内容 |
|---|---|
| `infra` | Pulsar / Milvus(+etcd,minio,attu) / NebulaGraph / vector-reranker / **kt-service(BKT+DKT 推理)** |
| `qianrenqianmian` | tenant / iam / course / teaching / ops-service / ai-service / agent-service / realtime-gateway / ops-web |
| `monitoring` | Prometheus / Grafana / node-exporter / kube-state-metrics / Loki / Promtail |
| `load-testing` | Locust / K6 |

## 0. 环境准备

```bash
export KUBECONFIG=/path/to/rancher-kubeconfig.yml   # Rancher 控制台下载
export SSH_PASS='<运维密码>'                        # 只走环境变量, 不进代码库
export REGISTRY=harbor.campus.local:5000           # 可选
```

## 1. 组件盘点(必做)

```bash
./deploy-rancher.sh discover
```

已有 Redis Cluster / CRDB → 后续 deploy 自动跳过；若 LLM 服务名不是 `llm-service.middleware`，修改
`k8s/base/configmap-secret.yaml` 中 `LLM_BASE_URL`。

## 2. 镜像

```bash
./deploy-rancher.sh images    # 有 REGISTRY 推送; 无则 sshpass+ctr 导入两台节点
```

RKE2/containerd 节点导入：`sudo ctr -n k8s.io images import <tar>`；K3s 用 `k3s ctr images import`。

## 3. 部署顺序(脚本自动)

1. namespaces / secrets / configmap(组件端点全部 env 注入)
2. **Pulsar 3.2**（zk×1 + bookie×2 + broker×2 + init Job 建主题：domain-events、ai-generation-tasks/results、learning-behavior、resource-index-upsert）
3. 监控补齐：node-exporter(DS)、kube-state-metrics、Loki+Promtail、Prometheus(修复版+规则)、Grafana(嵌入+6 看板)
4. **Milvus 2.4 standalone**(etcd+minio+attu)、**NebulaGraph 3.8**(metad/graphd/storaged+ADD HOSTS Job)——已存在则跳过
5. 业务服务 ×4 + ops-service + ai-service + agent-service + realtime-gateway + vector-reranker + ops-web
6. Ingress(ops.api.realtime 三个 host)

## 4. 验证

```bash
./deploy-rancher.sh verify
# Pulsar 消息: kubectl -n middleware exec pulsar-broker-0 -- \
#   bin/pulsar-client --url pulsar://localhost:6650 consume -s test -n 5 persistent://public/default/domain-events
# Redis Cluster(既有): kubectl -n middleware exec redis-cluster-0 -- redis-cli -a $PW cluster info
# Grafana: http://<入口>/grafana  (6 个看板已在 Platform Plus 文件夹)
# 运维平台: http://ops.platform.local/  (总览/服务运行/缓存监控/Pulsar 消息/日志中心 + 每页 AI 助手)
```

## 5. 双节点资源预算(建议)

| 组件 | 副本 | 请求/上限 |
|---|---|---|
| Pulsar(zk/bookie/broker) | 1/2/2 | 1C/1G ~ 2C/3G |
| Milvus standalone(+etcd+minio) | 1+1+1 | 官方最低 2C8G |
| NebulaGraph(m/g/s) | 1×3 | ~2C3G |
| 业务+新服务 | 2~3 | 各 0.5C/1G 级 |
| Redis Cluster(既有)/CRDB(既有) | — | 不占用新预算 |

## 6. 踩坑与对策(部署期)

| 现象 | 原因 | 对策 |
|---|---|---|
| Pulsar broker CrashLoop | ZK 元数据未初始化 | 先跑 `pulsar-init` Job |
| bookie NoBookieException | E/W/A 大于 bookie 数 | 2 bookie → ensemble=2/write=2/ack=2 |
| Redis 集群 `CLUSTERDOWN` | 既有集群 slot 未迁移完整 | `redis-cli --cluster check <addr>` 后 `--cluster fix` |
| Milvus OOM | 低于 2C8G | 调大 limits 或改 Qdrant |
| Nebula SHOW HOSTS offline | storaged 未 ADD HOSTS | nebula-init Job |
| CRDB 40001 事务冲突 | 串行化隔离 | 应用层重试(已配 resilience4j retry) |
| 小程序 AI 助手无响应 | 只开了 SSE | 走 realtime-gateway 的 /ws 通道 |
| Grafana iframe 空白 | allow_embedding 未开 | grafana.ini 已配 allow_embedding=true |


## 7. 知识追踪模型(BKT+DKT) —— 推荐精准率指标的数据前提

```bash
cd kt-service
python train_all.py --data <backend/data 合成数据目录> --n-skills 300 --epochs-bkt 2 --epochs-dkt 2
# 产出 checkpoints/: bkt_params.json + dkt_model.pt + mastery_index.json(8000 学生) + eval_metrics.json
# 本轮实测: AUC=0.7708 / Acc=65.2% (48984 条下一题预测)
docker build -t platform/kt-service:1.0.0 .    # checkpoints 打入镜像或挂载 PVC
```
接入: teaching-service `/api/v1/kt/mastery|predict`(代理 infra/kt-service)；`GET /api/v1/kt/model/info` 可查 AUC。

## 8. 压测与 E2E

```bash
# 功能全链路(pytest)
BASE_URL=http://<入口> pytest tests/e2e -v -m smoke          # 核心
BASE_URL=http://<入口> pytest tests/e2e -v                   # 全量
# 压测(K6 三阶段爬坡至 1000 并发)
BASE_URL=http://<入口> k6 run tests/k6/stress_1000.js
# 压测(Locust 多租户仿真)
locust -f locust/locustfile_1000u.py --headless -u 1000 -r 50 -t 30m --host http://<入口>
# AI 生成质量评测
python evals/run_eval.py --ai http://<入口>/api/v1 --use-judge
```
