#!/usr/bin/env bash
# =============================================================================
# 双节点(10.167.2.175/176) Rancher 管理的 K8s 集群一键部署脚本(重构版)
# 设计原则:
#   0. 集群中【已存在】的 Redis Cluster / CockroachDB 等组件【直接复用, 不重复部署】
#   1. 密码不进代码库: SSH_PASS/JWT_SECRET 等全部走环境变量
#   2. 幂等: 重复执行安全
# 用法:
#   KUBECONFIG=/path/to/rancher-kubeconfig ./deploy-rancher.sh discover
#   KUBECONFIG=... ./deploy-rancher.sh images      # 构建并导入/推送镜像
#   KUBECONFIG=... ./deploy-rancher.sh deploy      # 部署全部组件与服务
#   KUBECONFIG=... ./deploy-rancher.sh verify      # 部署后验证
# =============================================================================
set -euo pipefail

REGISTRY="${REGISTRY:-}"                  # 可选: 推送镜像到私有仓库
SSH_HOSTS=("${SSH_HOSTS[@]:-10.167.2.175 10.167.2.176}")
SSH_USER="${SSH_USER:-root}"
SSH_PASS="${SSH_PASS:-}"                  # 必须由环境变量提供(不回显不落盘)
NS_PLATFORM=platform-plus
NS_MIDDLE=infra          # 基础应用
NS_PROJECT=qianrenqianmian # 项目服务
NS_MONI=monitoring

kc() { kubectl --kubeconfig "${KUBECONFIG:-$HOME/.kube/config}" "$@"; }

log()  { echo -e "\033[32m[deploy]\033[0m $*"; }
warn() { echo -e "\033[33m[warn]\033[0m $*"; }

# -----------------------------------------------------------------------------
discover() {
  log "探测集群已有组件(已有组件将跳过部署, 直接复用)"
  for sig in redis cockroachdb milvus nebula pulsar loki grafana prometheus llm; do
    found=$(kc get sts,deploy -A -o name 2>/dev/null | grep -i "$sig" | head -3 || true)
    if [ -n "$found" ]; then
      log "  [存在] $sig:"
      echo "$found" | sed 's/^/      /'
    else
      warn "  [缺失] $sig (将按部署物创建)"
    fi
  done
  log "节点信息:"
  kc get nodes -o wide | sed 's/^/  /'
}

# -----------------------------------------------------------------------------
build_images() {
  log "构建全部服务镜像(本机 Docker 或远程构建)"
  local services=(
    "tenant-service" "iam-service" "course-service" "teaching-service"
    "ops-service" "ai-service"
  )
  for s in "${services[@]}"; do
    docker build -t platform/${s}:1.0.0 -f ${s}/Dockerfile ${s} 2>/dev/null \
      || warn "${s} Dockerfile 缺失, 跳过(可用 spring-boot:build-image)"
  done
  docker build -t platform/agent-service:1.0.0     agent-service   2>/dev/null || warn "agent-service 构建失败"
  docker build -t platform/kt-service:1.0.0         kt-service     2>/dev/null || warn "kt-service 需先产出 checkpoints"
  log "KT 模型训练(如 checkpoints 为空): cd kt-service && python train_all.py --data <合成数据目录>"
  docker build -t platform/realtime-gateway:1.0.0  realtime-gateway
  docker build -t platform/vector-reranker:1.0.0   vector-reranker
  docker build -t platform/ops-web:1.0.0           ops-web || warn "ops-web 需先 npm run build"

  if [ -n "$REGISTRY" ]; then
    log "推送镜像到 $REGISTRY"
    for img in $(docker images --format '{{.Repository}}:{{.Tag}}' | grep '^platform/'); do
      docker tag "$img" "$REGISTRY/${img#platform/}" && docker push "$REGISTRY/${img#platform/}"
    done
  else
    log "无 REGISTRY: 通过 SSH 导入镜像到两台服务器(RKE2/containerd)"
    for host in "${SSH_HOSTS[@]}"; do
      docker image save platform/realtime-gateway:1.0.0 platform/vector-reranker:1.0.0 \
        | sshpass -p "$SSH_PASS" ssh -o StrictHostKeyChecking=no "$SSH_USER@$host" \
          'sudo ctr -n k8s.io images import -' && log "  已导入 $host"
    done
    warn "Java/Python 大镜像建议走 REGISTRY 或在各节点本地构建"
  fi
}

# -----------------------------------------------------------------------------
deploy() {
  log "生成随机密码并更新密钥(覆盖默认值)"
  REDIS_PW=$(openssl rand -hex 16); GRAFANA_PW=$(openssl rand -hex 12)
  kc -n $NS_MIDDLE get secret platform-secrets >/dev/null 2>&1 \
    && kc -n $NS_MIDDLE get secret platform-secrets -o jsonpath='{.data.redis-password}' | base64 -d | grep -q CHANGE_ME \
    && kc -n $NS_MIDDLE patch secret platform-secrets -p "{\"stringData\":{\"redis-password\":\"$REDIS_PW\"}}"

  log "部署命名空间/密钥/配置"
  kc apply -f k8s/base/namespaces.yaml
  kc apply -f k8s/base/configmap-secret.yaml
  kc apply -f k8s/base/grafana-dashboards.yaml

  log "部署 Pulsar(替换 Kafka) —— 集群已有则跳过"
  if ! kc -n $NS_MIDDLE get sts pulsar-broker >/dev/null 2>&1; then
    kc apply -f k8s/base/pulsar.yaml
  else warn "Pulsar 已存在, 跳过"; fi

  log "部署监控补齐(node-exporter/kube-state-metrics/Loki/Promtail)"
  kc apply -f k8s/base/monitoring-stack.yaml
  kc apply -f k8s/base/prometheus.yaml
  kc apply -f k8s/base/grafana.yaml

  log "部署新增中间件(Milvus/Nebula —— 已存在则跳过)"
  kc -n $NS_MIDDLE get deploy milvus-standalone >/dev/null 2>&1 || kc apply -f k8s/base/milvus.yaml
  kc -n $NS_MIDDLE get deploy nebula-graphd   >/dev/null 2>&1 || kc apply -f k8s/base/nebula.yaml
  # Redis Cluster / CockroachDB: 集群既有, 仅本地演练用 --with-reference 才应用:
  warn "Redis Cluster/CRDB 使用集群既有实例(redis-cluster.yaml/cockroachdb.yaml 为参考部署物, 默认不应用)"

  log "部署平台服务(4 业务 + ops + ai)"
  for f in k8s/base/tenant-service.yaml k8s/base/iam-service.yaml k8s/base/course-service.yaml \
           k8s/base/teaching-service.yaml; do kc apply -f "$f"; done
  kc apply -f k8s/base/new-services.yaml

  log "部署 Ingress"
  kc apply -f k8s/base/ingress-v2.yaml
  kc apply -f k8s/base/hpa-new-services.yaml

  log "AI 生成质量评测(可选, 需要 LLM): python evals/run_eval.py --ai <ai-url> --use-judge"

  log "生产 overlay"
  kc apply -k k8s/overlays/production 2>/dev/null || warn "production overlay 需先修正 patch 目标(见 docs)"

  log "等待核心 Pod 就绪…"
  sleep 60 && kc -n $NS_PROJECT get pods | sed 's/^/  /' && kc -n $NS_MIDDLE get pods | sed 's/^/  /'
}

# -----------------------------------------------------------------------------
verify() {
  log "端到端验证"
  echo "-- 服务健康 --"
  for s in tenant iam course teaching ops ai; do
    kc -n $NS_PLATFORM get deploy "$s-service" -o jsonpath="{.status.readyReplicas}/{.spec.replicas} $s-service{$'\n'}" 2>/dev/null || true
  done
  echo "-- Pulsar 主题(硬校验: 6 主题缺一即 FAIL, 不再 || true 吞错) --"
  VERIFY_OUT=$(kc -n $NS_MIDDLE run pulsar-verify --rm -i --restart=Never --image=apachepulsar/pulsar:3.2.2 -- \
    bin/pulsar-admin --admin-url http://pulsar-broker:8080 topics list public/default 2>&1) || warn "pulsar-verify pod 运行失败"
  echo "$VERIFY_OUT" | grep -oE "persistent://public/default/[a-zA-Z-]+" | sed 's|.*/||' | sed 's/-partition.*//' | sort -u > /tmp/plp-topics.txt
  MISSING=""
  for T in domain-events ai-generation-tasks ai-generation-results learning-behavior resource-index-upsert domain-events-DLQ; do
    grep -qx "$T" /tmp/plp-topics.txt || MISSING="$MISSING $T"
  done
  if [ -n "$MISSING" ]; then warn "Pulsar 缺主题:$MISSING (数据中台事件链断裂, 见 docs/AUDIT_DOC_VS_CODE_2026-10-24.md)"; else echo "  Pulsar 6/6 主题 OK"; fi
  echo "$VERIFY_OUT" | grep -qE "ai-generation-tasks" || warn "无法列 Pulsar 主题 — broker 可能不可达(多轮功能测试漏检的根因之一, 本次起硬校验)"
  echo "-- Redis Cluster --"
  kc -n $NS_MIDDLE get pods -l app=redis-cluster 2>/dev/null || warn "redis-cluster(既有)探测失败, 请人工核对地址"
  echo "-- Ingress --"
  kc get ingress -A | sed 's/^/  /'
  log "验证完成。运维平台入口: http://<NodeIP>/ (ops.platform.local) Grafana: /grafana"
}

case "${1:-}" in
  discover) discover ;;
  images)   build_images ;;
  deploy)   deploy ;;
  verify)   verify ;;
  *) echo "用法: $0 {discover|images|deploy|verify}"; exit 1 ;;
esac
