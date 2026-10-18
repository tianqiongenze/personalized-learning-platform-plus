#!/bin/bash
# 一键部署脚本 for Personalized Learning Platform Plus
# Target Server: 10.167.2.176 (Worker), 10.167.2.175 (Master)
# Supports: Docker Compose (单机) / Kubernetes (集群)

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# ==========================================
# 配置区域 - 请根据实际环境修改
# ==========================================

# 部署模式: docker | k8s
DEPLOY_MODE="${DEPLOY_MODE:-k8s}"

# 服务器配置
MASTER_SERVER="10.167.2.175"
WORKER_SERVER="10.167.2.176"
SSH_USER="root"
SSH_PASSWORD="hwyd@0413"

# 项目路径
LOCAL_PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REMOTE_PROJECT_DIR="/opt/personalized-learning-platform-plus"

# 镜像仓库 (如果使用私有仓库请修改)
REGISTRY="${REGISTRY:-}"
IMAGE_TAG="${IMAGE_TAG:-1.0.0}"

# 域名配置
DOMAIN="${DOMAIN:-platform.local}"
METALLB_IP_RANGE="${METALLB_IP_RANGE:-10.167.2.200-10.167.2.220}"

# 密钥配置 (生产环境请修改!)
JWT_SECRET="${JWT_SECRET:-$(openssl rand -hex 32)}"
POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-$(openssl rand -hex 16)}"
REDIS_PASSWORD="${REDIS_PASSWORD:-$(openssl rand -hex 16)}"
GRAFANA_PASSWORD="${GRAFANA_PASSWORD:-admin123}"

# ==========================================
# 工具函数
# ==========================================

log_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

check_command() {
    if ! command -v "$1" &> /dev/null; then
        log_error "命令 '$1' 未找到，请先安装"
        exit 1
    fi
}

run_remote() {
    local server=$1
    local cmd=$2
    sshpass -p "$SSH_PASSWORD" ssh -o StrictHostKeyChecking=no -o ConnectTimeout=30 "$SSH_USER@$server" "$cmd"
}

copy_to_remote() {
    local server=$1
    local src=$2
    local dest=$3
    sshpass -p "$SSH_PASSWORD" scp -o StrictHostKeyChecking=no -r "$src" "$SSH_USER@$server:$dest"
}

wait_for_ssh() {
    local server=$1
    local max_attempts=30
    local attempt=1
    while [ $attempt -le $max_attempts ]; do
        if sshpass -p "$SSH_PASSWORD" ssh -o StrictHostKeyChecking=no -o ConnectTimeout=5 "$SSH_USER@$server" "echo 'SSH ready'" 2>/dev/null; then
            return 0
        fi
        log_info "等待 $server SSH 就绪... ($attempt/$max_attempts)"
        sleep 10
        ((attempt++))
    done
    log_error "无法连接到 $server"
    exit 1
}

# ==========================================
# 环境检查
# ==========================================

check_prerequisites() {
    log_info "检查部署前置条件..."
    
    check_command "sshpass"
    check_command "ssh"
    check_command "scp"
    
    if [ "$DEPLOY_MODE" = "k8s" ]; then
        check_command "kubectl"
        check_command "kustomize"
        check_command "helm"
    else
        check_command "docker"
        check_command "docker-compose"
    fi
    
    # 检查本地项目文件
    if [ ! -f "$LOCAL_PROJECT_DIR/docker-compose-plus.yml" ]; then
        log_error "未找到 docker-compose-plus.yml"
        exit 1
    fi
    
    if [ "$DEPLOY_MODE" = "k8s" ] && [ ! -d "$LOCAL_PROJECT_DIR/k8s" ]; then
        log_error "未找到 k8s 目录"
        exit 1
    fi
    
    log_success "前置条件检查通过"
}

# ==========================================
# Docker Compose 部署 (单机模式)
# ==========================================

deploy_docker_compose() {
    log_info "=== 开始 Docker Compose 部署 ==="
    
    wait_for_ssh "$WORKER_SERVER"
    
    log_info "1. 准备远程服务器环境..."
    run_remote "$WORKER_SERVER" "
        apt-get update && apt-get install -y docker.io docker-compose-plugin curl jq
        systemctl enable docker
        systemctl start docker
        mkdir -p $REMOTE_PROJECT_DIR
    "
    
    log_info "2. 复制项目文件..."
    copy_to_remote "$WORKER_SERVER" "$LOCAL_PROJECT_DIR" "$REMOTE_PROJECT_DIR/"
    
    log_info "3. 生成环境配置..."
    run_remote "$WORKER_SERVER" "
        cd $REMOTE_PROJECT_DIR
        cat > .env << EOF
JWT_SECRET=$JWT_SECRET
POSTGRES_PASSWORD=$POSTGRES_PASSWORD
REDIS_PASSWORD=$REDIS_PASSWORD
GRAFANA_PASSWORD=$GRAFANA_PASSWORD
DOMAIN=$DOMAIN
EOF
    "
    
    log_info "4. 构建并启动服务..."
    run_remote "$WORKER_SERVER" "
        cd $REMOTE_PROJECT_DIR
        docker-compose -f docker-compose-plus.yml pull
        docker-compose -f docker-compose-plus.yml build --no-cache
        docker-compose -f docker-compose-plus.yml up -d
    "
    
    log_info "5. 等待服务就绪..."
    run_remote "$WORKER_SERVER" "
        cd $REMOTE_PROJECT_DIR
        sleep 60
        docker-compose -f docker-compose-plus.yml ps
    "
    
    log_info "6. 运行健康检查..."
    run_remote "$WORKER_SERVER" "
        # 检查网关
        for i in {1..30}; do
            if curl -kf https://localhost/health > /dev/null 2>&1; then
                echo 'Gateway healthy'
                break
            fi
            sleep 10
        done
        
        # 检查各服务
        for svc in tenant-service iam-service course-service teaching-service; do
            for i in {1..30}; do
                port=\$(docker-compose -f docker-compose-plus.yml port \$svc 8081 2>/dev/null | cut -d: -f2)
                if [ -n \"\$port\" ] && curl -kf http://localhost:\$port/actuator/health > /dev/null 2>&1; then
                    echo \"\$svc healthy\"
                    break
                fi
                sleep 10
            done
        done
    "
    
    log_info "7. 初始化数据库..."
    run_remote "$WORKER_SERVER" "
        cd $REMOTE_PROJECT_DIR
        docker-compose -f docker-compose-plus.yml exec -T tenant-service java -jar /app/app.jar --spring.profiles.active=docker --flyway.migrate 2>/dev/null || true
        docker-compose -f docker-compose-plus.yml exec -T iam-service java -jar /app/app.jar --spring.profiles.active=docker --flyway.migrate 2>/dev/null || true
        docker-compose -f docker-compose-plus.yml exec -T course-service java -jar /app/app.jar --spring.profiles.active=docker --flyway.migrate 2>/dev/null || true
        docker-compose -f docker-compose-plus.yml exec -T teaching-service java -jar /app/app.jar --spring.profiles.active=docker --flyway.migrate 2>/dev/null || true
    "
    
    log_success "Docker Compose 部署完成!"
    show_access_info "docker"
}

# ==========================================
# Kubernetes 部署 (集群模式)
# ==========================================

deploy_kubernetes() {
    log_info "=== 开始 Kubernetes 部署 ==="
    
    # 检查 kubectl 连接
    if ! kubectl cluster-info &>/dev/null; then
        log_error "无法连接到 Kubernetes 集群，请检查 kubeconfig"
        exit 1
    fi
    
    log_info "1. 安装 MetalLB (如果未安装)..."
    if ! kubectl get namespace metallb-system &>/dev/null; then
        kubectl apply -f https://raw.githubusercontent.com/metallb/metallb/v0.13.12/config/manifests/metallb-native.yaml
        log_info "等待 MetalLB 就绪..."
        kubectl wait --for=condition=ready pod -l app=metallb -n metallb-system --timeout=300s
        
        # 配置 IP 地址池
        kubectl apply -f - <<EOF
apiVersion: metallb.io/v1beta1
kind: IPAddressPool
metadata:
  name: lan-pool
  namespace: metallb-system
spec:
  addresses:
  - $METALLB_IP_RANGE
  autoAssign: true
---
apiVersion: metallb.io/v1beta1
kind: L2Advertisement
metadata:
  name: lan-advertisement
  namespace: metallb-system
spec:
  ipAddressPools:
  - lan-pool
EOF
    fi
    
    log_info "2. 安装 NGINX Ingress Controller..."
    if ! kubectl get namespace ingress-nginx &>/dev/null; then
        helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
        helm repo update
        helm install ingress-nginx ingress-nginx/ingress-nginx \
            --namespace ingress-nginx \
            --create-namespace \
            --set controller.service.type=LoadBalancer \
            --set controller.service.annotations."metallb\.universe\.tf/address-pool"=lan-pool \
            --wait --timeout=300s
    fi
    
    log_info "3. 安装 Cert-Manager (可选，用于 TLS)..."
    if ! kubectl get namespace cert-manager &>/dev/null; then
        helm repo add jetstack https://charts.jetstack.io
        helm repo update
        helm install cert-manager jetstack/cert-manager \
            --namespace cert-manager \
            --create-namespace \
            --version v1.13.0 \
            --set installCRDs=true \
            --wait --timeout=300s
    fi
    
    log_info "4. 创建命名空间和基础资源..."
    kubectl apply -f "$LOCAL_PROJECT_DIR/k8s/base/namespaces.yaml"
    kubectl apply -f "$LOCAL_PROJECT_DIR/k8s/base/configmap-secret.yaml"
    
    # 更新 Secret 中的密码
    kubectl create secret generic platform-plus-secrets -n platform-plus \
        --from-literal=JWT_SECRET="$JWT_SECRET" \
        --from-literal=POSTGRES_PASSWORD="$POSTGRES_PASSWORD" \
        --from-literal=REDIS_PASSWORD="$REDIS_PASSWORD" \
        --from-literal=GRAFANA_PASSWORD="$GRAFANA_PASSWORD" \
        --dry-run=client -o yaml | kubectl apply -f -
    
    log_info "5. 部署数据库和中间件..."
    kubectl apply -f "$LOCAL_PROJECT_DIR/k8s/base/tenant-postgres.yaml"
    kubectl apply -f "$LOCAL_PROJECT_DIR/k8s/base/tenant-redis.yaml"
    kubectl apply -f "$LOCAL_PROJECT_DIR/k8s/base/iam-postgres.yaml"
    kubectl apply -f "$LOCAL_PROJECT_DIR/k8s/base/iam-redis.yaml"
    kubectl apply -f "$LOCAL_PROJECT_DIR/k8s/base/course-postgres.yaml"
    kubectl apply -f "$LOCAL_PROJECT_DIR/k8s/base/course-redis.yaml"
    kubectl apply -f "$LOCAL_PROJECT_DIR/k8s/base/teaching-postgres.yaml"
    kubectl apply -f "$LOCAL_PROJECT_DIR/k8s/base/teaching-redis.yaml"
    kubectl apply -f "$LOCAL_PROJECT_DIR/k8s/base/zookeeper.yaml"
    kubectl apply -f "$LOCAL_PROJECT_DIR/k8s/base/kafka.yaml"
    
    log_info "等待数据库就绪..."
    kubectl wait --for=condition=ready pod -l app=tenant-postgres -n platform-plus --timeout=300s
    kubectl wait --for=condition=ready pod -l app=iam-postgres -n platform-plus --timeout=300s
    kubectl wait --for=condition=ready pod -l app=course-postgres -n platform-plus --timeout=300s
    kubectl wait --for=condition=ready pod -l app=teaching-postgres -n platform-plus --timeout=300s
    kubectl wait --for=condition=ready pod -l app=tenant-redis -n platform-plus --timeout=120s
    kubectl wait --for=condition=ready pod -l app=iam-redis -n platform-plus --timeout=120s
    kubectl wait --for=condition=ready pod -l app=course-redis -n platform-plus --timeout=120s
    kubectl wait --for=condition=ready pod -l app=teaching-redis -n platform-plus --timeout=120s
    kubectl wait --for=condition=ready pod -l app=zookeeper -n platform-plus --timeout=120s
    kubectl wait --for=condition=ready pod -l app=kafka -n platform-plus --timeout=180s
    
    log_info "6. 部署微服务..."
    kubectl apply -f "$LOCAL_PROJECT_DIR/k8s/base/tenant-service.yaml"
    kubectl apply -f "$LOCAL_PROJECT_DIR/k8s/base/iam-service.yaml"
    kubectl apply -f "$LOCAL_PROJECT_DIR/k8s/base/course-service.yaml"
    kubectl apply -f "$LOCAL_PROJECT_DIR/k8s/base/teaching-service.yaml"
    
    log_info "等待微服务就绪..."
    kubectl wait --for=condition=ready pod -l app=tenant-service -n platform-plus --timeout=300s
    kubectl wait --for=condition=ready pod -l app=iam-service -n platform-plus --timeout=300s
    kubectl wait --for=condition=ready pod -l app=course-service -n platform-plus --timeout=300s
    kubectl wait --for=condition=ready pod -l app=teaching-service -n platform-plus --timeout=300s
    
    log_info "7. 部署网络策略..."
    kubectl apply -f "$LOCAL_PROJECT_DIR/k8s/base/network-policies.yaml"
    
    log_info "8. 部署 Ingress..."
    kubectl apply -f "$LOCAL_PROJECT_DIR/k8s/base/ingress.yaml"
    
    log_info "9. 部署 HPA..."
    kubectl apply -f "$LOCAL_PROJECT_DIR/k8s/base/hpa.yaml"
    
    log_info "10. 部署监控栈..."
    kubectl apply -f "$LOCAL_PROJECT_DIR/k8s/base/prometheus.yaml"
    kubectl apply -f "$LOCAL_PROJECT_DIR/k8s/base/grafana.yaml"
    kubectl apply -f "$LOCAL_PROJECT_DIR/k8s/base/jaeger.yaml"
    
    log_info "11. 部署 Locust 压测..."
    kubectl apply -f "$LOCAL_PROJECT_DIR/k8s/base/locust.yaml"
    
    log_info "12. 应用生产环境配置..."
    if [ -f "$LOCAL_PROJECT_DIR/k8s/overlays/production/kustomization.yaml" ]; then
        kubectl apply -k "$LOCAL_PROJECT_DIR/k8s/overlays/production/production"
    fi
    
    log_info "13. 等待所有资源就绪..."
    sleep 30
    
    log_info "14. 验证部署..."
    verify_k8s_deployment
    
    log_success "Kubernetes 部署完成!"
    show_access_info "k8s"
}

verify_k8s_deployment() {
    log_info "验证 Kubernetes 部署状态..."
    
    echo "=== 节点状态 ==="
    kubectl get nodes -o wide
    
    echo -e "\n=== Platform Plus Pods ==="
    kubectl get pods -n platform-plus -o wide
    
    echo -e "\n=== Monitoring Pods ==="
    kubectl get pods -n monitoring -o wide
    
    echo -e "\n=== Load Testing Pods ==="
    kubectl get pods -n load-testing -o wide
    
    echo -e "\n=== Services ==="
    kubectl get svc -n platform-plus
    kubectl get svc -n monitoring
    kubectl get svc -n load-testing
    
    echo -e "\n=== Ingress ==="
    kubectl get ingress -n platform-plus
    
    echo -e "\n=== HPA ==="
    kubectl get hpa -n platform-plus
    
    # 获取 Ingress IP
    INGRESS_IP=$(kubectl get ingress platform-plus-ingress -n platform-plus -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "待分配")
    log_info "Ingress IP: $INGRESS_IP"
    
    # 健康检查
    if [ "$INGRESS_IP" != "待分配" ]; then
        log_info "执行健康检查..."
        for i in {1..10}; do
            if curl -kf "https://$INGRESS_IP/health" > /dev/null 2>&1; then
                log_success "网关健康检查通过"
                break
            fi
            sleep 10
        done
    fi
}

# ==========================================
# 卸载旧版本
# ==========================================

uninstall_old() {
    log_info "=== 卸载旧版本 personalized-learning-platform ==="
    
    wait_for_ssh "$WORKER_SERVER"
    
    run_remote "$WORKER_SERVER" "
        # 停止并删除 Docker Compose 服务
        cd /opt/personalized-learning-platform 2>/dev/null || true
        if [ -f docker-compose.yml ]; then
            docker-compose -f docker-compose.yml down -v --remove-orphans 2>/dev/null || true
        fi
        
        # 删除所有相关容器
        docker ps -a --filter 'name=personalized-learning' --format '{{.ID}}' | xargs -r docker stop 2>/dev/null || true
        docker ps -a --filter 'name=personalized-learning' --format '{{.ID}}' | xargs -r docker rm -f 2>/dev/null || true
        
        # 删除卷
        docker volume ls --filter 'name=personalized' --format '{{.Name}}' | xargs -r docker volume rm -f 2>/dev/null || true
        docker volume ls --filter 'name=postgres_data' --format '{{.Name}}' | xargs -r docker volume rm -f 2>/dev/null || true
        docker volume ls --filter 'name=redis_data' --format '{{.Name}}' | xargs -r docker volume rm -f 2>/dev/null || true
        docker volume ls --filter 'name=kafka_data' --format '{{.Name}}' | xargs -r docker volume rm -f 2>/dev/null || true
        docker volume ls --filter 'name=prometheus_data' --format '{{.Name}}' | xargs -r docker volume rm -f 2>/dev/null || true
        docker volume ls --filter 'name=grafana_data' --format '{{.Name}}' | xargs -r docker volume rm -f 2>/dev/null || true
        
        # 删除网络
        docker network ls --filter 'name=learning-network' --format '{{.ID}}' | xargs -r docker network rm 2>/dev/null || true
        
        # 删除镜像
        docker images --filter 'reference=personalized-learning*' --format '{{.ID}}' | xargs -r docker rmi -f 2>/dev/null || true
        
        # 删除项目目录
        rm -rf /opt/personalized-learning-platform* 2>/dev/null || true
        
        # 清理防火墙
        firewall-cmd --permanent --remove-port=80/tcp 2>/dev/null || true
        firewall-cmd --permanent --remove-port=443/tcp 2>/dev/null || true
        firewall-cmd --permanent --remove-port=3000/tcp 2>/dev/null || true
        firewall-cmd --permanent --remove-port=8000/tcp 2>/dev/null || true
        firewall-cmd --permanent --remove-port=8089/tcp 2>/dev/null || true
        firewall-cmd --permanent --remove-port=9090/tcp 2>/dev/null || true
        firewall-cmd --permanent --remove-port=3001/tcp 2>/dev/null || true
        firewall-cmd --reload 2>/dev/null || true
        
        echo '旧版本卸载完成'
    "
    
    log_success "旧版本卸载完成"
}

# ==========================================
# 显示访问信息
# ==========================================

show_access_info() {
    local mode=$1
    local ip=""
    
    if [ "$mode" = "docker" ]; then
        ip="$WORKER_SERVER"
    else
        ip=$(kubectl get ingress platform-plus-ingress -n platform-plus -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "$WORKER_SERVER")
    fi
    
    echo -e "${GREEN}========================================${NC}"
    echo -e "${GREEN}部署成功! 访问信息如下:${NC}"
    echo -e "${GREEN}========================================${NC}"
    echo -e "${BLUE}平台首页:${NC}     https://$ip (或 https://platform.local)"
    echo -e "${BLUE}API 网关:${NC}     https://$ip/api"
    echo -e "${BLUE}API 文档:${NC}     https://$ip/swagger-ui.html"
    echo -e "${BLUE}Grafana:${NC}      https://$ip/grafana (admin/$GRAFANA_PASSWORD)"
    echo -e "${BLUE}Prometheus:${NC}   https://$ip/metrics"
    echo -e "${BLUE}Jaeger:${NC}       https://$ip/jaeger"
    echo -e "${BLUE}Locust:${NC}       https://$ip/locust"
    echo -e "${GREEN}========================================${NC}"
    echo -e "${YELLOW}注意: 首次访问需在 /etc/hosts 添加: $ip platform.local${NC}"
    echo -e "${YELLOW}或配置 DNS 解析 platform.local -> $ip${NC}"
    echo -e "${GREEN}========================================${NC}"
}

# ==========================================
# 运行压测
# ==========================================

run_load_test() {
    log_info "=== 启动 Locust 分布式压测 (5000 并发) ==="
    
    if [ "$DEPLOY_MODE" = "k8s" ]; then
        # 扩容 Locust Worker 到 8 个以支持 5000 并发
        kubectl scale deployment locust-worker -n load-testing --replicas=8
        sleep 10
        
        # 启动压测 (无头模式)
        kubectl exec -n load-testing deployment/locust-master -- \
            locust -f /mnt/locust/locustfile.py \
            --host=https://platform.local \
            --headless -u 5000 -r 50 --run-time 30m \
            --master
    else
        # Docker Compose 模式
        run_remote "$WORKER_SERVER" "
            cd $REMOTE_PROJECT_DIR
            docker-compose -f docker-compose-plus.yml up -d --scale locust-worker=8
            sleep 10
            docker-compose -f docker-compose-plus.yml exec locust-master \
                locust -f /mnt/locust/locustfile.py \
                --host=https://platform.local \
                --headless -u 5000 -r 50 --run-time 30m \
                --master
        "
    fi
    
    log_success "压测已启动，请访问 Locust Web UI 查看实时统计"
}

# ==========================================
# 主菜单
# ==========================================

show_menu() {
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}Personalized Learning Platform Plus - 部署工具${NC}"
    echo -e "${BLUE}========================================${NC}"
    echo "1) 完整部署 (卸载旧版 + 部署新版)"
    echo "2) 仅卸载旧版本"
    echo "3) 仅部署新版本 (Docker Compose)"
    echo "4) 仅部署新版本 (Kubernetes)"
    echo "5) 运行性能压测 (5000 并发)"
    echo "6) 验证部署状态"
    echo "7) 查看访问信息"
    echo "8) 卸载新版本"
    echo "0) 退出"
    echo -e "${BLUE}========================================${NC}"
}

main() {
    # 解析命令行参数
    case "${1:-}" in
        --mode)
            DEPLOY_MODE="$2"
            shift 2
            ;;
        --full)
            check_prerequisites
            uninstall_old
            if [ "$DEPLOY_MODE" = "k8s" ]; then
                deploy_kubernetes
            else
                deploy_docker_compose
            fi
            exit 0
            ;;
        --uninstall-old)
            uninstall_old
            exit 0
            ;;
        --deploy)
            check_prerequisites
            if [ "$DEPLOY_MODE" = "k8s" ]; then
                deploy_kubernetes
            else
                deploy_docker_compose
            fi
            exit 0
            ;;
        --load-test)
            run_load_test
            exit 0
            ;;
        --verify)
            if [ "$DEPLOY_MODE" = "k8s" ]; then
                verify_k8s_deployment
            else
                run_remote "$WORKER_SERVER" "cd $REMOTE_PROJECT_DIR && docker-compose -f docker-compose-plus.yml ps"
            fi
            exit 0
            ;;
        --info)
            show_access_info "$DEPLOY_MODE"
            exit 0
            ;;
        --uninstall-new)
            if [ "$DEPLOY_MODE" = "k8s" ]; then
                kubectl delete namespace platform-plus monitoring load-testing --ignore-not-found=true
            else
                run_remote "$WORKER_SERVER" "cd $REMOTE_PROJECT_DIR && docker-compose -f docker-compose-plus.yml down -v --remove-orphans"
            fi
            exit 0
            ;;
        *)
            # 交互模式
            while true; do
                show_menu
                read -p "请选择操作 [0-8]: " choice
                case $choice in
                    1)
                        check_prerequisites
                        uninstall_old
                        if [ "$DEPLOY_MODE" = "k8s" ]; then
                            deploy_kubernetes
                        else
                            deploy_docker_compose
                        fi
                        ;;
                    2)
                        uninstall_old
                        ;;
                    3)
                        DEPLOY_MODE="docker"
                        check_prerequisites
                        deploy_docker_compose
                        ;;
                    4)
                        DEPLOY_MODE="k8s"
                        check_prerequisites
                        deploy_kubernetes
                        ;;
                    5)
                        run_load_test
                        ;;
                    6)
                        if [ "$DEPLOY_MODE" = "k8s" ]; then
                            verify_k8s_deployment
                        else
                            run_remote "$WORKER_SERVER" "cd $REMOTE_PROJECT_DIR && docker-compose -f docker-compose-plus.yml ps"
                        fi
                        ;;
                    7)
                        show_access_info "$DEPLOY_MODE"
                        ;;
                    8)
                        if [ "$DEPLOY_MODE" = "k8s" ]; then
                            kubectl delete namespace platform-plus monitoring load-testing --ignore-not-found=true
                        else
                            run_remote "$WORKER_SERVER" "cd $REMOTE_PROJECT_DIR && docker-compose -f docker-compose-plus.yml down -v --remove-orphans"
                        fi
                        ;;
                    0)
                        log_info "退出部署工具"
                        exit 0
                        ;;
                    *)
                        log_error "无效选择，请重新输入"
                        ;;
                esac
                echo ""
            done
            ;;
    esac
}

# 执行主函数
main "$@"