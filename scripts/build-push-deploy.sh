#!/usr/bin/env bash
# build-push-deploy.sh — amd64 镜像构建 + 推送 + 滚动部署到 10.167.2.175/176 K8S
# 用法:
#   ./scripts/build-push-deploy.sh --svc course-service,tenant-service [--skip-deploy]
#   ./scripts/build-push-deploy.sh --all [--skip-deploy]
set -euo pipefail

REG="10.167.2.175:30500"
TAG="11.0.0"
NAMESPACE="qianrenqianmian"
SSH_HOST="10.167.2.175"
SSH_OPTS="-o BatchMode=yes"
SKIP_DEPLOY=0
SVCS=()
# 依赖序: tenant iam course teaching ops graphql-gateway ai agent kt realtime-gateway vector-reranker nginx-gateway web-student ops-web
ALL_SVCS=(tenant iam course teaching ops graphql-gateway ai agent kt realtime-gateway vector-reranker nginx-gateway web-student ops-web)
# Java 服务(走 jar 产物校验); 其余为前端/其他
JAVA_SVCS=(tenant-service iam-service course-service teaching-service ops-service graphql-gateway ai-service agent-service kt-service realtime-gateway)

log() { echo "[build-push-deploy] $*"; }

usage() { grep '^#' "$0" | sed -n '3,6p'; exit 1; }

parse_args() {
  [[ $# -eq 0 ]] && usage
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --svc) IFS=',' read -r -a SVCS <<< "$2"; shift 2 ;;
      --all) SVCS=("${ALL_SVCS[@]}"); shift ;;
      --skip-deploy) SKIP_DEPLOY=1; shift ;;
      -h|--help) usage ;;
      *) echo "未知参数: $1"; usage ;;
    esac
  done
}

# 探测 buildx
buildx_available() {
  command -v docker >/dev/null && docker buildx version >/dev/null 2>&1
}

build_one() {
  local svc="$1"
  local img="${REG}/platform/${svc}:${TAG}"
  log "build ${svc} -> ${img}"
  if buildx_available; then
    docker buildx build --platform linux/amd64 -t "${img}" --load "./${svc}/"
  else
    log "buildx 不可用, 回退 DOCKER_BUILDKIT=1 docker build"
    DOCKER_BUILDKIT=1 docker build --platform linux/amd64 -t "${img}" "./${svc}/"
  fi
  verify_artifact "$svc" "$img"
}

# BVV 铁律: 产物内容校验
verify_artifact() {
  local svc="$1" img="$2"
  log "verify artifact: ${img}"
  if [[ " ${JAVA_SVCS[*]} " == *" ${svc} "* ]]; then
    docker run --rm --platform linux/amd64 --entrypoint sh "${img}" \
      -c 'ls /app/*.jar 2>/dev/null || ls /app' | grep -q "\.jar" \
      || { log "ERROR: ${svc} 镜像内未找到 jar 产物"; return 1; }
    docker run --rm --platform linux/amd64 --entrypoint sh "${img}" -c 'uname -m' | grep -q x86_64 \
      || { log "ERROR: ${svc} 镜像架构非 amd64"; return 1; }
    log "OK: jar 存在且架构 x86_64"
  else
    # 前端镜像: 确认 nginx/html 就位
    docker run --rm --platform linux/amd64 --entrypoint sh "${img}" \
      -c 'ls /usr/share/nginx/html/index.html 2>/dev/null || ls /usr/share/nginx/html || ls /etc/nginx' \
      || { log "ERROR: ${svc} 前端产物缺失"; return 1; }
    log "OK: 前端产物就位"
  fi
}

push_one() {
  local svc="$1"
  local img="${REG}/platform/${svc}:${TAG}"
  log "push ${img}"
  docker push "${img}"
}

deploy_one() {
  local svc="$1"
  local img="${REG}/platform/${svc}:${TAG}"
  # 容器名从集群实查, 回退按服务名
  local cname
  cname=$(ssh ${SSH_OPTS} "${SSH_HOST}" \
    "kubectl -n ${NAMESPACE} get deploy ${svc} -o jsonpath='{.spec.template.spec.containers[0].name}'" 2>/dev/null || true)
  cname="${cname//\'/}"; cname="${cname//$'\r'/}"
  [[ -z "${cname}" ]] && cname="${svc}"
  log "deploy ${svc}: set image ${cname}=${img}"
  ssh ${SSH_OPTS} "${SSH_HOST}" \
    "kubectl -n ${NAMESPACE} set image deployment/${svc} ${cname}=${img} && \
     kubectl -n ${NAMESPACE} rollout status deployment/${svc} --timeout=300s"
  log "deploy ${svc} 完成"
}

main() {
  parse_args "$@"
  for svc in "${SVCS[@]}"; do
    [[ -f "./${svc}/Dockerfile" ]] || { log "SKIP ${svc}: 无 Dockerfile"; continue; }
    build_one "$svc"
    push_one "$svc"
    if [[ "${SKIP_DEPLOY}" -eq 0 ]]; then
      deploy_one "$svc"
    fi
    log "== ${svc} 完成 =="
  done
  log "全部完成: ${SVCS[*]}"
}

main "$@"
