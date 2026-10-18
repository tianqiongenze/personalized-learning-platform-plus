# 个性化学习平台 Plus - 部署与运维手册

## 1. 部署架构概览

### 1.1 整体部署拓扑
```
┌─────────────────────────────────────────────────────────────────┐
                        Internet / Campus Network
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
                    Layer 7 Load Balancer (NGINX/ALB)
                    TLS Termination | WAF | Rate Limiting
└─────────────────────────────────────────────────────────────────┘
                              │
              ┌───────────────┼───────────────┐
              ▼               ▼               ▼
        ┌──────────┐    ┌──────────┐    ┌──────────┐
        │  K8s     │    │  K8s     │    │  K8s     │
        │ Cluster  │    │ Cluster  │    │ Cluster  │
        │ (Prod)   │    │ (Staging)│    │ (Dev)    │
        └──────────┘    └──────────┘    └──────────┘
              │               │               │
              └───────────────┼───────────────┘
                              ▼
        ┌─────────────────────────────────────────┐
        │     Shared Data Layer (Multi-AZ)        │
        │  PostgreSQL | Redis | Kafka | MinIO     │
        └─────────────────────────────────────────┘
```

### 1.2 环境规划

| 环境 | 用途 | K8s 集群 | 数据隔离 | 访问控制 |
|------|------|---------|---------|---------|
| **Development** | 开发联调 | 单节点 Kind | 共享开发库 | 开发者全权限 |
| **Testing** | 自动化测试 | 3 节点 | 独立测试库 | CI/CD 只读+部署权限 |
| **Staging** | 预发布验证 | 生产规格 50% | 生产数据脱敏 | 受控部署权限 |
| **Production** | 正式服务 | 多 AZ 高可用 | 物理隔离 | 仅运维团队 |

---

## 2. 基础设施部署

### 2.1 Kubernetes 集群规范

#### 2.1.1 生产集群配置
```yaml
# 集群规格
apiVersion: kubeadm.k8s.io/v1beta3
kind:3
kind: ClusterConfiguration
kubernetesVersion: v1.28.5
controlPlaneEndpoint: "k8s-api.platform.example.com:6443"
networking:
  podSubnet: "10.244.0.0/16"
  serviceSubnet: "10.96.0.0/12"
  dnsDomain: "cluster.local"
---
# 节点规格
# Master: 3 节点 (4C16G, 100G SSD)
# Worker: 15 节点 (8C32G, 500G NVMe)
# GPU Worker: 4 节点 (16C64G, A100 40G, 1TB NVMe)
```

#### 2.1.2 必需插件
| 插件 | 版本 | 用途 |
|------|------|------|
| CNI (Calico) | v3.26 | 网络策略、BGP |
| CSI (Rook-Ceph) | v1.14 | 持久化存储 |
| Ingress NGINX | v1.9 | L7 负载均衡 |
| Cert-Manager | v1.13 | TLS 证书管理 |
| External-DNS | v0.14 | DNS 自动管理 |
| Prometheus Operator | v0.68 | 监控栈 |
| Grafana | v10.2 | 可视化 |
| Jaeger | v1.48 | 分布式追踪 |
| ArgoCD | v2.9 | GitOps 部署 |
| Sealed Secrets | v0.24 | 密钥加密 |
| Kyverno | v1.11 | 策略引擎 |

### 2.2 数据库部署

#### 2.2.1 PostgreSQL (托管服务推荐)
```yaml
# 云厂商托管实例配置
# 阿里云 RDS / AWS RDS / 腾讯云 CDB
规格: 16C64G 主实例 + 2 只读实例
存储: 2TB ESSD PL3 (自动扩容)
高可用: 同城双可用区 (同步复制)
备份: 自动备份 7天 + 逻辑备份 30天
参数优化:
  max_connections: 5000
  shared_buffers: 16GB
  effective_cache_size: 48GB
  work_mem: 256MB
  maintenance_work_mem: 2GB
  random_page_cost: 1.1
```

#### 2.2.2 Redis Cluster
```yaml
# 6 节点集群 (3主3从)
规格: 8C32G / 节点
分片: 3 个分片
持久化: AOF everysec + RDB 每小时
内存策略: allkeys-lru
连接池: 最大 10000 连接
```

#### 2.2.3 Kafka
```yaml
# 3 Broker 集群
规格: 8C32G / Broker
存储: 500G NVMe / Broker
分区: 12 partitions / topic
副本因子: 3
保留: 7天 / 100GB
压缩: ZSTD
```

### 2.3 对象存储
```yaml
# MinIO / S3 兼容
规格: 4 节点分布式
存储: 10TB 原始容量
冗余: EC:4 (可容忍 2 节点故障)
桶策略:
  - course-resources: 私有, 版本控制, 生命周期 365天
  - user-avatars: 公共读, 无版本
  - course-exports: 私有, 7天过期
  - model-checkpoints: 私有, 版本控制, 90天过期
```

---

## 3. 应用部署流程

### 3.1 GitOps 部署流水线

```mermaid
graph LR
    A[代码提交] --> B[CI 构建]
    B --> C[镜像扫描]
    C --> D[单元测试]
    D --> E[集成测试]
    E --> F[镜像签名]
    F --> G[推送镜像仓库]
    G --> H[ArgoCD 同步]
    H --> I[金丝雀发布]
    I --> J[自动化验收]
    J --> K[全量发布]
```

### 3.2 ArgoCD Application 配置

```yaml
# argocd/applications/platform-plus.yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: platform-plus
  namespace: argocd
  finalizers:
    - resources-finalizer.argocd.argoproj.io
spec:
  project: platform-plus
  source:
    repoURL: https://github.com/org/platform-plus.git
    targetRevision: main
    path: helm
    helm:
      valueFiles:
        - values.yaml
        - values-prod.yaml
      parameters:
        - name: global.imageTag
          value: "1.0.0"
  destination:
    server: https://kubernetes.default.svc
    namespace: platform-plus
  syncPolicy:
    automated:
      prune: true
      selfHeal: true
      allowEmpty: false
    syncOptions:
      - CreateNamespace=true
      - PrunePropagationPolicy=foreground
      - PruneLast=true
    retry:
      limit: 5
      backoff:
        duration: 5s
        factor: 2
        maxDuration: 3m
```

### 3.3 部署策略

#### 3.3.1 金丝雀发布
```yaml
# 服务级金丝雀配置
canary:
  enabled: true
  steps:
    - setWeight: 10
      pause: { duration: 5m }
    - setWeight: 30
      pause: { duration: 10m }
    - setWeight: 50
      pause: { duration: 10m }
    - setWeight: 100
  analysis:
    templates:
      - templateName: success-rate
      - templateName: latency-p99
      - templateName: error-rate
    args:
      - name: service-name
        value: "{{.Release.Name}}"
```

#### 3.3.2 蓝绿部署 (数据库迁移)
```yaml
# 数据库迁移蓝绿策略
blueGreen:
  preMigration:
    - 创建新版本 Schema (兼容旧版本)
    - 双写模式验证
  migration:
    - 切换读流量到新版本
    - 迁移历史数据 (分批)
    - 切换写流量
  postMigration:
    - 监控 30 分钟
    - 回滚预案就绪
  rollback:
    - 切回旧版本 Schema
    - 数据回滚脚本
```

---

## 4. 运维操作手册

### 4.1 日常巡检清单

#### 4.1.1 每日巡检 (09:00, 18:00)
| 检查项 | 检查方式 | 正常标准 | 异常处理 |
|--------|---------|---------|---------|
| 集群节点状态 | `kubectl get nodes` | 所有 Ready | 排查 NotReady 节点 |
| Pod 状态 | `kubectl get pods -A` | 无 CrashLoop/Error | 查看日志、重启 |
| 核心服务健康 | `curl /actuator/health` | 全部 UP | 查看依赖服务 |
| 资源使用率 | Grafana 仪表盘 | CPU<70%, Mem<80% | 扩容/排查泄漏 |
| 数据库连接池 | Prometheus 指标 | < 80% | 调整连接池/扩容 |
| Redis 内存/命中率 | Redis INFO / Grafana | < 80% / > 90% | 清理过期/扩容 |
| 磁盘空间 | `df -h` / 监控 | < 80% | 清理日志/扩容 |
| 证书有效期 | `kubectl get cert` | > 30天 | 手动续期/检查 CM |
| 备份状态 | 检查备份作业 | 最近 24h 成功 | 手动触发/排查 |

#### 4.1.2 每周巡检 (周一 10:00)
| 检查项 | 内容 |
|--------|------|
| 安全补丁 | 检查基础镜像/依赖 CVE，计划更新 |
| 容量规划 | 分析资源增长趋势，预测扩容时间 |
| 慢查询分析 | pg_stat_statements Top 10，优化索引 |
| 告警噪音分析 | 统计告警频次，调整阈值/抑制规则 |
| 成本分析 | 云资源成本趋势，识别优化点 |
| 文档更新 | 更新运维手册、架构图、联系人 |

#### 4.1.3 每月巡检 (月初第一个工作日)
| 检查项 | 内容 |
|--------|------|
| 灾备演练 | 执行恢复演练，记录 RTO/RPO |
| 权限审计 | 检查 RBAC/云账号权限，回收过期权限 |
| 证书轮换 | 检查即将过期证书，执行轮换 |
| 日志归档 | 归档冷日志到冷存储，清理热存储 |
| 性能基线对比 | 对比性能基线，识别性能退化 |

### 4.2 常用运维命令

#### 4.2.1 Kubernetes 常用操作
```bash
# 查看所有命名空间 Pod 状态
kubectl get pods -A -o wide

# 查看特定服务详细状态
kubectl describe deployment tenant-service -n platform-plus

# 查看 Pod 日志 (最近 100 行)
kubectl logs -n platform-plus deployment/tenant-service --tail=100 -f

# 进入 Pod 调试
kubectl exec -it -n platform-plus deployment/tenant-service -- bash

# 重启 Deployment
kubectl rollout restart deployment/tenant-service -n platform-plus

# 查看滚动更新历史
kubectl rollout history deployment/tenant-service -n platform-plus

# 回滚到上一版本
kubectl rollout undo deployment/tenant-service -n platform-plus

# 查看资源使用
kubectl top pods -n platform-plus --sort-by=memory

# 强制删除卡住的 Pod
kubectl delete pod <pod-name> -n platform-plus --grace-period=0 --force
```

#### 4.2.2 数据库运维
```bash
# 连接 PostgreSQL
kubectl exec -it -n platform-plus postgresql-0 -- psql -U postgres -d tenant_db

# 查看连接数
SELECT count(*) FROM pg_stat_activity WHERE datname='tenant_db';

# 查看慢查询
SELECT query, mean_time, calls FROM pg_stat_statements ORDER BY mean_time DESC LIMIT 10;

# 查看表大小
SELECT schemaname, tablename, pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) as size 
FROM pg_tables WHERE schemaname='public' ORDER BY size DESC;

# 手动 VACUUM
VACUUM ANALYZE courses;

# 查看复制延迟
SELECT * FROM pg_stat_replication;
```

#### 4.2.3 Redis 运维
```bash
# 连接 Redis
kubectl exec -it -n platform-plus redis-0 -- redis-cli -c

# 查看集群状态
CLUSTER INFO
CLUSTER NODES

# 查看内存使用
INFO memory

# 查看慢日志
SLOWLOG GET 10

# 扫描大 Key
redis-cli --bigkeys

# 手动触发 AOF 重写
BGREWRITEAOF
```

#### 4.2.4 Helm 部署管理
```bash
# 部署/升级
helm upgrade --install tenant-service ./helm/tenant-service -n platform-plus -f values-prod.yaml

# 查看发布历史
helm history tenant-service -n platform-plus

# 回滚
helm rollback tenant-service 3 -n platform-plus

# 卸载
helm uninstall tenant-service -n platform-plus

# 模板渲染预览
helm template tenant-service ./helm/tenant-service -f values-prod.yaml
```

### 4.3 故障处理流程

#### 4.3.1 故障分级
| 级别 | 定义 | 响应时间 | 升级路径 |
|------|------|---------|---------|
| **P0 (Critical)** | 核心业务不可用、数据丢失、安全事件 | 15 分钟 | 直接电话叫醒架构师+运维负责人 |
| **P1 (High)** | 核心功能受影响、性能严重下降 | 30 分钟 | 电话通知值班运维+开发负责人 |
| **P2 (Medium)** | 非核心功能异常、单用户投诉 | 2 小时 | 工单通知相关开发 |
| **P3 (Low)** | 非紧急问题、优化建议 | 1 个工作日 | 纳入迭代计划 |

#### 4.3.2 常见故障处理

| 故障现象 | 可能原因 | 排查步骤 | 恢复措施 |
|---------|---------|---------|---------|
| 服务大面积 503 | 依赖服务挂、配置错误、资源耗尽 | 1. 检查依赖健康 2. 查看事件 3. 检查资源配额 | 重启/扩容/回滚配置 |
| 数据库连接耗尽 | 连接泄漏、并发过高、长事务 | 1. 查看连接数 2. 找长事务 3. 检查连接池 | Kill 长事务/重启应用/扩容连接池 |
| Redis 内存溢出 | 大 Key、过期策略失效、内存碎片 | 1. BIGKEYS 2. 内存分析 3. 检查过期策略 | 删除大 Key/调整策略/重启 |
| 磁盘空间不足 | 日志堆积、临时文件、备份未清理 | 1. df -h 2. du 排查 3. 清理策略 | 清理日志/压缩/扩容磁盘 |
| 证书过期 | Cert-Manager 失败、DNS 验证失败 | 1. 查看 Certificate 状态 2. 检查 Challenge | 手动触发续期/修复 DNS |
| HPA 不扩容 | 指标采集失败、阈值设置不当 | 1. 检查 Metrics Server 2. 查看 HPA 事件 | 修复指标/调整阈值 |

### 4.4 备份与恢复

#### 4.4.1 备份策略
| 数据类型 | 备份频率 | 保留期限 | 存储位置 | 恢复目标 |
|---------|---------|---------|---------|---------|
| PostgreSQL (全量) | 每日 02:00 | 30 天 | 跨区域 OSS | RPO < 24h, RTO < 2h |
| PostgreSQL (增量/WAL) | 连续归档 | 7 天 | 跨区域 OSS | RPO < 5min, RTO < 30min |
| Redis (RDB) | 每 6 小时 | 7 天 | 跨区域 OSS | RPO < 6h, RTO < 30min |
| Redis (AOF) | 实时 | 1 天 | 本地 + 远程 | RPO < 1s, RTO < 15min |
| Kubernetes 资源 | 每 6 小时 | 30 天 | GitOps 仓库 | RTO < 30min |
| MinIO 对象 | 版本控制 + 生命周期 | 永久/按策略 | 同区域 + 异地 | 即时恢复 |

#### 4.4.2 恢复演练记录模板
```markdown
# 恢复演练记录 - YYYY-MM-DD

## 演练信息
- 演练类型: 全量恢复 / 增量恢复 / 单表恢复 / 单库恢复
- 参与人员: 
- 目标 RTO: X 小时
- 目标 RPO: X 分钟

## 执行步骤
1. [ ] 确认备份文件完整性
2. [ ] 创建隔离恢复环境
3. [ ] 执行恢复命令
4. [ ] 数据校验 (行数、校验和、关键业务查询)
5. [ ] 应] 应用层验证 (核心 API 冒烟测试)
6. [ ] 记录耗时

## 结果记录
- 实际 RTO: X 小时 Y 分钟
- 实际 RPO: X 分钟
- 数据完整性: 通过 / 失败
- 问题记录: 

## 改进措施
- 
```

---

## 5. 监控与告警运维

### 5.1 告警处理 SOP

#### 5.1.1 告警接收与分派
```
告警触发 → Alertmanager 分组/抑制 → 发送至钉钉/企业微信/短信/电话
    ↓
值班人员确认 (5分钟内) → 标记处理中
    ↓
初步排查 (15分钟) → 能否自行解决？
    ├─ 是 → 修复 → 验证恢复 → 关闭告警 → 记录复盘
    └─ 否 → 升级至 P0/P1 → 唤醒专家组 → 联合排查
```

#### 5.1.2 告警抑制规则
```yaml
# 维护窗口抑制
- name: maintenance-window
  matchers:
    - alertname =~ ".*"
  startsAt: "2025-01-01T02:00:00Z"
  endsAt: "2025-01-01T04:00:00Z"
  # 每天 02:00-04:00 维护窗口

# 依赖故障抑制
- name: dependency-down
  matchers:
    - alertname = "ServiceDown"
  # 当核心依赖挂了，抑制下游服务告警
```

### 5.2 关键指标监控大盘

| 大盘名称 | 关注指标 | 刷新频率 | 适用场景 |
|---------|---------|---------|---------|
| 系统概览 | 整体 RPS、错误率、P50/P95/P99、吞吐量 | 10s | 日常巡检、故障首屏 |
| 服务拓扑 | 服务间调用关系、延迟热力图、错误率 | 30s | 故障定位、依赖分析 |
| KT 服务专题 | 推理延迟分布、GPU 利用率、队列长度、模型版本 | 10s | AI 服务专项监控 |
| 推荐服务专题 | 缓存命中率、推荐延迟、召回率、候选集大小 | 30s | 推荐质量监控 |
| 数据库专题 | 连接池、QPS、慢查询、复制延迟、表膨胀 | 10s | DBA 日常运维 |
| 缓存专题 | 命中率、内存使用、逐出率、大 Key 分布 | 30s | 缓存优化 |
| Kubernetes 专题 | Pod 状态、资源请求/限制、HPA 状态、节点资源 | 30s | 容器平台运维 |
| 业务指标 | 活跃用户、课程创建、作业提交、推荐点击率 | 1min | 产品/运营关注 |

---

## 6. 安全运维

### 6.1 证书管理
```bash
# 查看所有证书状态
kubectl get certificates -A

# 查看证书详情
kubectl describe certificate tenant-service-tls -n platform-plus

# 手动触发续期
kubectl annotate certificate tenant-service-tls cert-manager.io/force-renewal="$(date +%s)" -n platform-plus

# 查看挑战状态
kubectl describe challenge -n platform-plus
```

### 6.2 密钥轮换
```bash
# JWT 密钥轮换 (每 90 天)
# 1. 生成新密钥对
# 2. 更新 SealedSecret
# 2. 滚动重启相关服务
# 4. 验证新旧 Token 兼容性 (过渡期 24h)
# 5. 撤销旧密钥

# 数据库密码轮换 (每 90 天)
# 1. 生成新密码
# 2. 更新 Secret
# 4. 滚动重启相关服务
# 5. 验证连接正常
```

### 6.3 漏洞扫描与修复
```bash
# 镜像漏洞扫描 (CI 集成)
trivy image --severity HIGH,CRITICAL registry.example.com/tenant-service:1.0.0

# 运行时漏洞扫描 (每周)
kubectl run trivy-scanner --image=aquasec/trivy:latest -- \
  k8s --namespace platform-plus --report summary

# 依赖漏洞扫描 (每次构建)
mvn org.owasp:dependency-check-maven:check
```

---

## 7. 容量规划与扩容

### 7.1 容量基线
| 资源 | 当前使用 | 峰值使用 | 扩容阈值 | 扩容步长 | 最大规模 |
|------|---------|---------|---------|---------|---------|
| K8s Worker CPU | 45% | 65% | 70% | +3 节点 | 50 节点 |
| K8s Worker Mem | 55% | 75% | 80% | +3 节点 | 50 节点 |
| PostgreSQL CPU | 25% | 40% | 60% | 纵向扩容 | 32C128G |
| PostgreSQL 存储 | 40% | 60% | 75% | 自动扩容 10% | 10TB |
| Redis 内存 | 50% | 65% | 75% | +2 分片 | 12 分片 |
| Kafka 存储 | 30% | 50% | 70% | +1 Broker | 9 Broker |
| GPU 显存 | 55% | 75% | 80% | +1 节点 | 16 节点 |

### 7.2 扩容操作手册
```bash
# K8s 节点扩容 (自动伸缩组)
# 云厂商控制台或 CLI 调整 ASG DesiredCapacity

# PostgreSQL 纵向扩容
# 云厂商控制台修改实例规格，需停机 5-10 分钟

# Redis 分片扩容
# 1. 创建新分片节点
# 2. CLUSTER MEET 新节点
# 3. 重新分片: redis-cli --cluster reshard
# 4. 验证数据均衡

# Kafka Broker 扩容
# 1. 新增 Broker 配置 broker.id
# 2. 启动新 Broker
# 3. 重新分配分区: kafka-reassign-partitions.sh
```

---

## 8. 灾难恢复演练

### 8.1 演练计划
| 演练类型 | 频次 | 参与范围 | 目标 RTO | 目标 RPO |
|---------|------|---------|---------|---------|
| 单表恢复 | 月度 | DBA + 开发 | 30 分钟 | 5 分钟 |
| 单库恢复 | 季度 | DBA + 运维 | 2 小时 | 30 分钟 |
| 全量恢复 | 半年 | 全团队 | 4 小时 | 1 小时 |
| 多 AZ 故障切换 | 年度 | 全团队 | 30 分钟 | 0 (同步复制) |
| 勒索软件恢复 | 年度 | 安全+运维+法务 | 8 小时 | 24 小时 |

### 8.2 灾难恢复演练检查单
```markdown
# 灾难恢复演练检查单 - YYYY-MM-DD

## 演练前准备
- [ ] 确认演练环境隔离 (独立命名空间/集群)
- [ ] 确认备份文件可用性 (校验和、完整性)
- [ ] 确认恢复脚本/工具可用
- [ ] 通知相关方 (避免误判为真实故障)
- [ ] 准备演练记录模板

## 演练执行
- [ ] 记录开始时间
- [ ] 执行恢复命令
- [ ] 监控恢复进度
- [ ] 记录关键时间点 (开始、数据导入完成、索引重建完成、校验完成)
- [ ] 执行数据校验 (行数、Checksum、关键查询)
- [ ] 执行应用层验证 (核心 API 冒烟测试)
- [ ] 记录结束时间

## 结果评估
- 实际 RTO: ___ 小时 ___ 分钟 (目标: ___)
- 实际 RPO: ___ 分钟 (目标: ___)
- 数据完整性: 通过 / 失败
- 业务功能: 正常 / 异常
- 发现问题: 

## 改进计划
- [ ] 问题 1: 改进措施、责任人、完成时间
- [ ] 问题 2: 改进措施、责任人、完成时间

## 签署
- 演练组织者: ________ 日期: ________
- 技术负责人: ________ 日期: ________
```

---

## 9. 变更管理

### 9.1 变更分类
| 变更类型 | 定义 | 审批流程 | 实施窗口 | 回滚要求 |
|---------|------|---------|---------|---------|
| **标准变更** | 低风险、频繁、标准化 (如配置调整、小版本升级) | 技术负责人审批 | 任意时间 (避开高峰) | 必须可回滚 |
| **正常变更** | 中风险、需评估 (如新功能上线、架构调整) | CAB 评审 + 技术总监批准 | 周二/四 22:00-02:00 | 必须有回滚方案并演练 |
| **紧急变更** | 高风险、必须立即实施 (如安全补丁、生产故障修复) | 技术总监/架构师口头批准，事后补单 | 任意时间 | 必须有回滚方案 |

### 9.2 变更单模板
```markdown
# 变更单 CR-YYYYMMDD-XXX

## 基本信息
- 变更标题: 
- 变更类型: 标准 / 正常 / 紧急
- 申请人: 
- 申请时间: 
- 计划实施时间: 
- 预计耗时: 

## 变更内容
- 变更描述: 
- 影响范围: (服务/组件/用户群体)
- 风险评估: 低/中/高
- 影响业务: 无/部分功能不可用/核心业务中断

## 实施方案
- 详细步骤: 
  1. 
  2. 
  3. 
- 回滚方案: 
  1. 
  2. 
- 验证方案: 
  1. 冒烟测试用例
  2. 关键指标观察

## 审批记录
- 技术评审: ________ 通过/驳回 时间: ________
- CAB 评审: ________ 通过/驳回 时间: ________
- 最终批准: ________ 通过/驳回 时间: ________

## 实施记录
- 实际开始时间: 
- 实际结束时间: 
- 实施人员: 
- 执行情况: 顺利 / 遇到问题 (详述)
- 回滚执行: 是 / 否 (如是，记录回滚过程)

## 事后复盘
- 是否达成预期目标: 是 / 否
- 遇到的问题: 
- 改进建议: 
- 文档更新: 是 / 否
```

---

## 10. 联系人与升级路径

### 10.1 核心团队联系表
| 角色 | 姓名 | 电话 | 邮箱 | 钉钉/微信 | 职责 |
|------|------|------|------|----------|------|
| 项目负责人 | | | | | 整体把控、资源协调 |
| 架构师 | | | | | 技术决策、方案评审 |
| 开发负责人 | | | | | 开发团队管理、代码质量 |
| 运维负责人 | | | | | 基础设施、监控、部署 |
| DBA | | | | | 数据库设计、优化、备份恢复 |
| 安全负责人 | | | | | 安全合规、漏洞管理、应急响应 |
| 产品经理 | | | | | 需求管理、验收标准 |
| 值班运维 (轮岗) | 见排班表 | | | | 一线告警处理、日常巡检 |

### 10.2 升级路径
```
L1: 值班运维 (5分钟响应)
    ↓ 无法解决 / P0 故障
L2: 运维负责人 + 相关开发负责人 (15分钟响应)
    ↓ 无法解决 / 架构级问题
L3: 架构师 + 技术总监 (30分钟响应)
    ↓ 涉及数据安全 / 重大决策
L4: 项目负责人 + CTO (1小时响应)
    ↓ 媒体关注 / 法律合规 / 重大损失
L5: CEO + 法务 + PR (按危机公关流程)
```

### 10.3 外部依赖联系人
| 依赖服务 | 提供方 | 联系人 | 电话 | 邮箱 | SLA |
|---------|-------|--------|------|------|-----|
| 云服务器/数据库/Redis | 阿里云/腾讯云/AWS | 企业支持经理 | 400-xxx-xxxx | support@xxx.com | 99.95% |
| 短信/邮件服务 | 阿里云/腾讯云/个推 | 技术支持 | 400-xxx-xxxx | support@xxx.com | 99.9% |
| 对象存储 | MinIO/阿里云 OSS | 技术支持 | 400-xxx-xxxx | support@xxx.com | 99.9% |
| 监控告警 | Grafana Cloud/Prometheus | 社区/商业支持 | - | - | - |
| 证书签发 | Let's Encrypt / CFSSL | 自动化 | - | - | - |

---

## 11. 附录

### 11.1 环境变量参考
```bash
# 通用环境变量
SPRING_PROFILES_ACTIVE=prod
SPRING_DATASOURCE_URL=jdbc:postgresql://postgresql:5432/xxx_db
SPRING_REDIS_HOST=redis
SPRING_REDIS_PORT=6379
JWT_SECRET=${JWT_SECRET}
LOG_LEVEL=INFO
APP_CORS_ALLOWED_ORIGINS=https://platform.example.com
```

### 11.2 端口分配表
| 服务 | 端口 | 协议 | 说明 |
|------|------|------|------|
| API Gateway | 80/443 | HTTP/HTTPS | 入口网关 |
| Tenant Service | 8081 | HTTP | 租户管理 |
| IAM Service | 8082 | HTTP | 认证授权 |
| Course Service | 8083 | HTTP | 课程资源 |
| Teaching Service | 8084 | HTTP | 教学互动 |
| Analytics Service | 8085 | HTTP | 学情分析 |
| Recommend Service | 8086 | HTTP | 推荐引擎 |
| KT Service | 9001 | HTTP/gRPC | 知识追踪 |
| Multimodal Service | 9002 | HTTP/gRPC | 多模态分析 |
| LLM Agent Service | 9003 | HTTP/gRPC | 智能体 |
| ModelOps Service | 9004 | HTTP/gRPC | MLOps |
| Prometheus | 9090 | HTTP | 指标采集 |
| Grafana | 3001 | HTTP | 可视化 |
| Jaeger | 16686 | HTTP | 追踪 UI |
| PostgreSQL | 5432 | TCP | 主库 |
| Redis | 6379 | TCP | 缓存 |
| Kafka | 9092 | TCP | 消息队列 |
| MinIO | 9000/9001 | HTTP | 对象存储 |

### 11.3 关键文件路径
| 文件类型 | 路径 |
|---------|------|
| 应用日志 | `/var/log/platform-plus/{service}/` |
| Nginx 访问日志 | `/var/log/nginx/access.log` |
| Nginx 错误日志 | `/var/log/nginx/error.log` |
| Kubernetes 审计日志 | `/var/log/kubernetes/audit.log` |
| 数据库慢查询日志 | `/var/lib/postgresql/log/postgresql-slow.log` |
| Redis 慢日志 | `redis-cli SLOWLOG GET` |
| 应用配置文件 | `/app/config/application.yml` |
| Kubernetes 配置 | `/etc/kubernetes/` |
| Helm Values | `helm/{service}/values-prod.yaml` |

---

## 12. 版本历史

| 版本 | 日期 | 编制人 | 审核人 | 批准人 | 变更摘要 |
|------|------|--------|--------|--------|---------|
| v1.0 | 2025-01-20 | 运维团队 | 架构师 | 技术总监 | 初版发布 |
| v1.1 | 2025-04-20 | 运维团队 | 架构师 | 技术总监 | 更新 K8s 版本、增加 GPU 扩容 |
| v1.2 | 2025-07-20 | 运维团队 | 架构师 | 技术总监 | 增加灾备演练记录模板 |

---

**文档分类**: 机密 - 仅限内部运维团队查阅  
**文档版本**: v1.0  
**编制日期**: 2025-01-20  
**下次评审**: 2025-04-20  
**文档归属**: 运维团队