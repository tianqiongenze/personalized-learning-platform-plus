# 个性化学习平台 Plus - 人工测试操作手册

## 1. 测试环境准备

### 1.1 环境信息
| 项目 | 信息 |
|------|------|
| **部署服务器** | 10.167.2.176 (Worker 节点) |
| **Kubernetes API** | 10.167.2.175:6443 (Master 节点) |
| **平台访问地址** | https://platform.local (通过 MetalLB: 10.167.2.200) |
| **API 网关** | Nginx Ingress (端口 80/443) |
| **监控面板** | Grafana: https://platform.local/grafana (admin/admin123) |
| **链路追踪** | Jaeger: https://platform.local/jaeger |
| **压测界面** | Locust: https://platform.local/locust |
| **API 文档** | Swagger UI: https://platform.local/swagger-ui.html |

### 1.2 测试账号准备
```bash
# 管理员账号
用户名: admin
密码: admin123

# 测试租户 (需先创建)
租户ID: test-tenant-001
租户域名: test.platform.local

# 测试用户 (批量创建)
用户名格式: testuser{001-100}
密码: testpass123
```

### 1.3 网络准备
- 确保测试机器在 10.167.2.0/24 网段
- 配置 hosts 文件或 DNS:
  ```
  10.167.2.200 platform.local
  10.167.2.200 api.platform.local
  10.167.2.200 grafana.platform.local
  10.167.2.200 locust.platform.local
  ```
- 防火墙开放 80, 443, 30080, 30443 端口

---

## 2. 部署验证测试

### 2.1 基础设施检查
```bash
# 1. 检查 Kubernetes 集群状态
kubectl get nodes -o wide
# 预期: 所有节点 Ready

# 2. 检查所有 Pod 状态
kubectl get pods -n platform-plus -o wide
# 预期: 所有 Pod Running/Completed

# 3. 检查服务状态
kubectl get svc -n platform-plus
# 预期: 所有服务有 ClusterIP

# 4. 检查 Ingress 状态
kubectl get ingress -n platform-plus
# 预期: ADDRESS 显示 MetalLB 分配的 IP (如 10.167.2.200)

# 5. 检查 HPA 状态
kubectl get hpa -n platform-plus
# 预期: 所有 HPA 正常显示当前副本数
```

### 2.2 服务健康检查
```bash
# 1. 网关健康检查
curl -k https://platform.local/health
# 预期: 返回 "healthy"

# 2. 各微服务健康检查
curl -k https://platform.local/api/v1/tenants/actuator/health
curl -k https://platform.local/api/v1/auth/actuator/health
curl -k https://platform.local/api/v1/courses/actuator/health
curl -k https://platform.local/api/v1/paths/actuator/health
# 预期: 返回 {"status":"UP",...}

# 3. 数据库连接检查
kubectl exec -n platform-plus tenant-postgres-0 -- pg_isready -U postgres -d tenant_db
kubectl exec -n platform-plus iam-postgres-0 -- pg_isready -U postgres -d iam_db
kubectl exec -n platform-plus course-postgres-0 -- pg_isready -U postgres -d course_db
kubectl exec -n platform-plus teaching-postgres-0 -- pg_isready -U postgres -d teaching_db
# 预期: accepting connections

# 4. Redis 连接检查
kubectl exec -n platform-plus tenant-redis-0 -- redis-cli ping
kubectl exec -n platform-plus iam-redis-0 -- redis-cli ping
kubectl exec -n platform-plus course-redis-0 -- redis-cli ping
kubectl exec -n platform-plus teaching-redis-0 -- redis-cli ping
# 预期: PONG

# 5. Kafka 检查
kubectl exec -n platform-plus kafka-0 -- kafka-broker-api-versions --bootstrap-server localhost:9092
# 预期: 显示 broker 版本信息
```

---

## 3. 功能测试用例

### 3.1 租户管理测试

#### TC-001: 创建租户
```bash
# 请求
curl -k -X POST https://platform.local/api/v1/tenants \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <admin_token>" \
  -d '{
    "name": "测试租户",
    "domain": "test.platform.local",
    "adminEmail": "admin@test.com",
    "adminName": "测试管理员",
    "plan": "ENTERPRISE"
  }'

# 预期响应
{
  "id": "uuid",
  "name": "测试租户",
  "domain": "test.platform.local",
  "status": "ACTIVE",
  "createdAt": "2024-01-01T00:00:00Z"
}
```

#### TC-002: 查询租户列表
```bash
curl -k -X GET "https://platform.local/api/v1/tenants?page=0&size=10" \
  -H "Authorization: Bearer <admin_token>"

# 预期: 返回分页租户列表
```

#### TC-003: 更新租户配置
```bash
curl -k -X PUT https://platform.local/api/v1/tenants/{tenantId} \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <admin_token>" \
  -d '{
    "branding": {
      "logoUrl": "https://example.com/logo.png",
      "primaryColor": "#1890ff",
      "theme": "light"
    },
    "quota": {
      "maxUsers": 10000,
      "maxCourses": 5000,
      "storageGB": 100
    }
  }'
```

### 3.2 认证授权测试

#### TC-004: 用户登录
```bash
curl -k -X POST https://platform.local/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser001",
    "password": "testpass123"
  }'

# 预期响应
{
  "token": "eyJhbGciOiJIUzI1NiIs...",
  "tokenType": "Bearer",
  "expiresIn": 1800,
  "user": {
    "id": "uuid",
    "username": "testuser001",
    "email": "testuser001@test.com",
    "roles": ["STUDENT"]
  }
}
```

#### TC-005: 用户注册
```bash
curl -k -X POST https://platform.local/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "newuser001",
    "email": "newuser001@test.com",
    "password": "testpass123",
    "tenantDomain": "test.platform.local"
  }'
```

#### TC-006: 角色权限验证
```bash
# 学生角色访问课程
curl -k -X GET https://platform.local/api/v1/courses \
  -H "Authorization: Bearer <student_token>"
# 预期: 200 OK

# 学生角色访问管理接口 (应被拒绝)
curl -k -X GET https://platform.local/api/v1/admin/users \
  -H "Authorization: Bearer <student_token>"
# 预期: 403 Forbidden

# 管理员角色访问管理接口
curl -k -X GET https://platform.local/api/v1/admin/users \
  -H "Authorization: Bearer <admin_token>"
# 预期: 200 OK
```

### 3.3 课程资源测试

#### TC-007: 创建课程
```bash
curl -k -X POST https://platform.local/api/v1/courses \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <teacher_token>" \
  -d '{
    "title": "Java 编程入门",
    "description": "从零开始学习 Java 编程",
    "category": "编程语言",
    "difficulty": "BEGINNER",
    "estimatedHours": 20,
    "tags": ["Java", "编程", "入门"]
  }'
```

#### TC-008: 创建知识点
```bash
curl -k -X POST https://platform.local/api/v1/knowledge-points \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <teacher_token>" \
  -d '{
    "courseId": "course-uuid",
    "name": "变量与数据类型",
    "description": "理解 Java 中的变量声明和基本数据类型",
    "difficulty": 1,
    "estimatedMinutes": 30,
    "prerequisites": []
  }'
```

#### TC-009: 上传课程资源
```bash
curl -k -X POST https://platform.local/api/v1/resources \
  -H "Authorization: Bearer <teacher_token>" \
  -F "file=@/path/to/video.mp4" \
  -F "courseId=course-uuid" \
  -F "knowledgePointId=kp-uuid" \
  -F "type=VIDEO" \
  -F "title=Java 变量讲解视频"
```

#### TC-010: 课程搜索与筛选
```bash
curl -k -X GET "https://platform.local/api/v1/courses?category=编程语言&difficulty=BEGINNER&page=0&size=20" \
  -H "Authorization: Bearer <student_token>"
```

### 3.4 学习路径测试

#### TC-011: 创建学习路径
```bash
curl -k -X POST https://platform.local/api/v1/paths \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <teacher_token>" \
  -d '{
    "courseId": "course-uuid",
    "title": "Java 编程完整学习路径",
    "description": "从入门到精通的完整学习路径",
    "steps": [
      {"stepType": "VIDEO", "title": "Java 简介", "contentId": "video-1", "estimatedMinutes": 15},
      {"stepType": "DOCUMENT", "title": "环境搭建指南", "contentId": "doc-1", "estimatedMinutes": 20},
      {"stepType": "QUIZ", "title": "基础语法测验", "contentId": "quiz-1", "estimatedMinutes": 10},
      {"stepType": "ASSIGNMENT", "title": "Hello World 作业", "contentId": "assign-1", "estimatedMinutes": 30},
      {"stepType": "EXAM", "title": "阶段考试", "contentId": "exam-1", "estimatedMinutes": 60}
    ]
  }'
```

#### TC-012: 学生开始学习路径
```bash
curl -k -X POST https://platform.local/api/v1/paths/{pathId}/start \
  -H "Authorization: Bearer <student_token>"
```

#### TC-013: 完成学习步骤
```bash
curl -k -X POST https://platform.local/api/v1/paths/{pathId}/steps/{stepId}/complete \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <student_token>" \
  -d '{
    "masteryAfter": 0.85
  }'
```

#### TC-014: 查看学习进度
```bash
curl -k -X GET https://platform.local/api/v1/paths/{pathId}/progress \
  -H "Authorization: Bearer <student_token>"

# 预期响应
{
  "pathId": "uuid",
  "totalSteps": 5,
  "completedSteps": 2,
  "currentStep": 3,
  "overallMastery": 0.42,
  "estimatedRemainingMinutes": 120
}
```

### 3.5 作业考试测试

#### TC-015: 创建作业
```bash
curl -k -X POST https://platform.local/api/v1/assignments \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <teacher_token>" \
  -d '{
    "courseId": "course-uuid",
    "learningPathId": "path-uuid",
    "stepId": "step-uuid",
    "title": "Java 变量练习",
    "description": "编写程序演示不同数据类型的变量声明",
    "instructions": "1. 创建 Main.java\n2. 声明 int, double, boolean, String 变量\n3. 打印变量值\n4. 提交代码文件",
    "maxScore": 100,
    "passingScore": 60,
    "maxAttempts": 3,
    "dueAt": "2024-12-31T23:59:59Z"
  }'
```

#### TC-016: 提交作业
```bash
curl -k -X POST https://platform.local/api/v1/assignments/{assignmentId}/submit \
  -H "Authorization: Bearer <student_token>" \
  -F "file=@/path/to/Main.java" \
  -F "content=代码说明文档"
```

#### TC-017: 创建考试
```bash
curl -k -X POST https://platform.local/api/v1/exams \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <teacher_token>" \
  -d '{
    "courseId": "course-uuid",
    "learningPathId": "path-uuid",
    "stepId": "step-uuid",
    "title": "Java 基础阶段考试",
    "description": "测试 Java 基础语法掌握情况",
    "durationMinutes": 60,
    "maxScore": 100,
    "passingScore": 60,
    "maxAttempts": 1,
    "shuffleQuestions": true,
    "questions": [
      {"type": "SINGLE_CHOICE", "content": "Java 中 int 类型占用多少字节？", "options": ["2", "4", "8", "16"], "correctAnswer": "4", "score": 10},
      {"type": "MULTIPLE_CHOICE", "content": "以下哪些是 Java 基本数据类型？", "options": ["int", "Integer", "double", "String"], "correctAnswer": ["int", "double"], "score": 10}
    ]
  }'
```

#### TC-018: 参加考试
```bash
# 开始考试
curl -k -X POST https://platform.local/api/v1/exams/{examId}/start \
  -H "Authorization: Bearer <student_token>"

# 提交答案
curl -k -X POST https://platform.local/api/v1/exams/{examId}/submit \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <student_token>" \
  -d '{
    "answers": [
      {"questionId": "q1", "answer": "4"},
      {"questionId": "q2", "answer": ["int", "double"]}
    ]
  }'
```

### 3.6 讨论互动测试

#### TC-019: 创建讨论
```bash
curl -k -X POST https://platform.local/api/v1/discussions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <student_token>" \
  -d '{
    "courseId": "course-uuid",
    "learningPathId": "path-uuid",
    "stepId": "step-uuid",
    "title": "关于 Java 变量作用域的疑问",
    "content": "请问局部变量和成员变量的作用域有什么区别？"
  }'
```

#### TC-020: 回复讨论
```bash
curl -k -X POST https://platform.local/api/v1/discussions/{discussionId}/replies \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <teacher_token>" \
  -d '{
    "content": "局部变量在方法内定义，作用域仅在方法内；成员变量在类中定义，作用域为整个类。"
  }'
```

### 3.7 直播教学测试

#### TC-021: 创建直播
```bash
curl -k -X POST https://platform.local/api/v1/live-sessions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <teacher_token>" \
  -d '{
    "courseId": "course-uuid",
    "learningPathId": "path-uuid",
    "stepId": "step-uuid",
    "title": "Java 面向对象编程直播课",
    "description": "深入讲解类、对象、继承、多态",
    "instructorId": "teacher-uuid",
    "instructorName": "张老师",
    "scheduledStartAt": "2024-01-15T19:00:00Z",
    "scheduledEndAt": "2024-01-15T21:00:00Z",
    "maxParticipants": 100,
    "recordingEnabled": true,
    "chatEnabled": true
  }'
```

#### TC-022: 学生加入直播
```bash
curl -k -X POST https://platform.local/api/v1/live-sessions/{sessionId}/join \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <student_token>" \
  -d '{"participantId": "student-uuid"}'
```

#### TC-023: 直播状态流转
```bash
# 教师准备直播
curl -k -X POST https://platform.local/api/v1/live-sessions/{sessionId}/prepare \
  -H "Authorization: Bearer <teacher_token>"

# 教师开始直播
curl -k -X POST https://platform.local/api/v1/live-sessions/{sessionId}/start \
  -H "Authorization: Bearer <teacher_token>"

# 教师结束直播
curl -k -X POST https://platform.local/api/v1/live-sessions/{sessionId}/end \
  -H "Authorization: Bearer <teacher_token>"
```

### 3.8 知识追踪与推荐测试

#### TC-024: 获取知识状态
```bash
curl -k -X GET https://platform.local/api/v1/knowledge/state \
  -H "Authorization: Bearer <student_token>"

# 预期响应
{
  "userId": "uuid",
  "knowledgeStates": [
    {"knowledgePointId": "kp-1", "mastery": 0.85, "lastPracticedAt": "2024-01-01T10:00:00Z"},
    {"knowledgePointId": "kp-2", "mastery": 0.62, "lastPracticedAt": "2024-01-01T09:30:00Z"}
  ]
}
```

#### TC-025: 获取个性化推荐
```bash
curl -k -X GET "https://platform.local/api/v1/recommendations?limit=10" \
  -H "Authorization: Bearer <student_token>"

# 预期响应
{
  "recommendations": [
    {"type": "KNOWLEDGE_POINT", "itemId": "kp-3", "reason": "前置知识点已掌握", "priority": 0.9},
    {"type": "COURSE", "itemId": "course-2", "reason": "相似学习者也在学", "priority": 0.8}
  ]
}
```

#### TC-026: 接受推荐
```bash
curl -k -X POST https://platform.local/api/v1/recommendations/{recId}/accept \
  -H "Authorization: Bearer <student_token>"
```

---

## 4. 性能与压力测试

### 4.1 Locust 压测操作

#### 4.1.1 启动分布式压测
```bash
# 1. 进入 Locust 目录
cd /opt/personalized-learning-platform-plus/locust

# 2. 启动 Master (在单独终端)
docker run -d --name locust-master \
  -p 8089:8089 \
  -e LOCUST_MODE=master \
  -e TARGET_HOST=https://platform.local \
  -v $(pwd):/mnt/locust \
  locustio/locust:2.17.0

# 3. 启动 4 个 Worker (在单独终端)
for i in {1..4}; do
  docker run -d --name locust-worker-$i \
    -e LOCUST_MODE=worker \
    -e LOCUST_MASTER_HOST=host.docker.internal \
    -v $(pwd):/mnt/locust \
    locustio/locust:2.17.0
done

# 4. 访问 Web UI
# 打开浏览器: http://10.167.2.176:8089
```

#### 4.1.2 压测场景配置
在 Web UI 中配置:
- **Number of users**: 5000
- **Spawn rate**: 50 users/second
- **Host**: https://platform.local
- 点击 "Start swarming"

#### 4.1.3 压测阶段
| 阶段 | 用户数 | 持续时间 | 目的 |
|------|--------|----------|------|
| 预热 | 100 | 2分钟 | 系统预热、连接池建立 |
| 爬坡 | 1000 | 5分钟 | 观察自动扩容 |
| 压力 | 3000 | 10分钟 | 稳定性验证 |
| 峰值 | 5000 | 15分钟 | 极限性能测试 |
| 维持 | 5000 | 20分钟 | 长时间稳定性 |
| 冷却 | 0 | 5分钟 | 观察缩容 |

#### 4.1.4 关键指标监控
```bash
# 1. 实时查看 Locust 统计
# Web UI 显示: RPS, 响应时间, 失败率

# 2. 监控 Kubernetes 资源
kubectl top pods -n platform-plus --sort-by=memory
kubectl top nodes

# 3. 监控 HPA 扩容
kubectl get hpa -n platform-plus -w

# 4. 检查数据库连接池
kubectl exec -n platform-plus tenant-postgres-0 -- psql -U postgres -d tenant_db -c "SELECT count(*) FROM pg_stat_activity;"

# 5. 检查 Redis 内存
kubectl exec -n platform-plus tenant-redis-0 -- redis-cli INFO memory
```

### 4.2 性能验收标准
| 指标 | 目标值 | 验收标准 |
|------|--------|----------|
| **平均响应时间 (P50)** | < 200ms | ✅ 达标 |
| **P99 响应时间** | < 500ms | ✅ 达标 |
| **错误率** | < 0.1% | ✅ 达标 |
| **吞吐量 (RPS)** | > 1000 | ✅ 达标 |
| **CPU 使用率** | < 70% | ✅ 达标 |
| **内存使用率** | < 80% | ✅ 达标 |
| **自动扩容触发** | < 3分钟 | ✅ 达标 |
| **无内存泄漏** | 4小时稳定 | ✅ 达标 |

---

## 5. 监控与告警验证

### 5.1 Grafana 仪表盘检查
访问: https://platform.local/grafana (admin/admin123)

必查仪表盘:
1. **系统概览** - 整体 RPS、错误率、延迟分布
2. **服务拓扑** - 服务间调用关系、依赖延迟
3. **KT 服务** - 模型推理延迟、GPU 利用率
4. **推荐服务** - 缓存命中率、推荐延迟
5. **数据库** - 连接池、慢查询、复制延迟
6. **缓存** - 命中率、内存使用、逐出率
7. **Kubernetes** - Pod 状态、HPA 状态、节点资源

### 5.2 告警规则验证
```bash
# 触发高延迟告警
# 在压测期间观察 Alertmanager 是否触发 HighLatencyP99 告警

# 触发高错误率告警
# 手动制造错误 (如访问不存在的接口) 观察 HighErrorRate 告警

# 触发资源告警
# 观察 CPU/内存告警是否在阈值触发
```

### 5.3 链路追踪验证
访问: https://platform.local/jaeger

检查:
1. 服务调用链路完整性
2. 跨服务调用延迟分布
3. 错误调用的根因分析
4. 采样率配置 (建议 10%)

---

## 6. 故障注入与恢复测试

### 6.1 Pod 故障恢复
```bash
# 1. 删除单个服务 Pod
kubectl delete pod -n platform-plus -l app=tenant-service --grace-period=0 --force

# 2. 观察自动重建
kubectl get pods -n platform-plus -l app=tenant-service -w

# 3. 验证服务可用性
curl -k https://platform.local/api/v1/tenants/actuator/health
```

### 6.2 数据库故障恢复
```bash
# 1. 模拟主库故障 (如果有从库)
kubectl exec -n platform-plus tenant-postgres-0 -- pg_ctl stop -m fast

# 2. 观察连接切换
# 应用应自动重连到从库或新主库

# 3. 恢复主库
kubectl exec -n platform-plus tenant-postgres-0 -- pg_ctl start
```

### 6.3 网络分区测试
```bash
# 使用网络策略模拟网络分区
kubectl apply -f - <<EOF
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: deny-all-ingress
  namespace: platform-plus
spec:
  podSelector:
    matchLabels:
      app: tenant-service
  policyTypes:
  - Ingress
  ingress: []
EOF

# 验证服务降级处理
# 观察熔断器、重试机制、降级响应

# 恢复网络
kubectl delete networkpolicy deny-all-ingress -n platform-plus
```

---

## 7. 安全测试

### 7.1 认证绕过测试
```bash
# 1. 无 Token 访问受保护接口
curl -k https://platform.local/api/v1/tenants
# 预期: 401 Unauthorized

# 2. 过期 Token
curl -k -H "Authorization: Bearer expired_token" https://platform.local/api/v1/tenants
# 预期: 401 Unauthorized

# 3. 篡改 Token
curl -k -H "Authorization: Bearer tampered_token" https://platform.local/api/v1/tenants
# 预期: 401 Unauthorized
```

### 7.2 权限越界测试
```bash
# 1. 学生访问教师接口
curl -k -H "Authorization: Bearer <student_token>" \
  -X POST https://platform.local/api/v1/courses
# 预期: 403 Forbidden

# 2. 租户 A 用户访问租户 B 数据
curl -k -H "Authorization: Bearer <tenantA_user_token>" \
  https://platform.local/api/v1/tenants/tenantB-id
# 预期: 403 Forbidden 或 404 Not Found
```

### 7.3 输入验证测试
```bash
# 1. SQL 注入测试
curl -k -X POST https://platform.local/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "admin'\'' OR 1=1--", "password": "anything"}'
# 预期: 400 Bad Request 或正常登录失败

# 2. XSS 测试
curl -k -X POST https://platform.local/api/v1/discussions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{"title": "<script>alert(1)</script>", "content": "test"}'
# 预期: 输入被转义或拒绝

# 3. 超长输入测试
curl -k -X POST https://platform.local/api/v1/courses \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{"title": "'$(python3 -c "print('A'*10000)")'", "description": "test"}'
# 预期: 400 Bad Request (长度超限)
```

---

## 8. 兼容性测试

### 8.1 浏览器兼容性
| 浏览器 | 版本 | 测试结果 |
|--------|------|----------|
| Chrome | 120+ | ✅ |
| Firefox | 121+ | ✅ |
| Safari | 17+ | ✅ |
| Edge | 120+ | ✅ |

### 8.2 移动端适配
- 响应式布局在 375px, 768px, 1024px, 1440px 断点正常
- 触摸交互正常 (点击、滑动、长按)
- 直播播放在移动端正常

### 8.3 API 版本兼容
```bash
# 测试 v1 API 向后兼容
curl -k -H "Accept: application/vnd.platform.v1+json" \
  https://platform.local/api/v1/tenants
```

---

## 9. 测试报告模板

### 9.1 测试执行记录
| 测试轮次 | 日期 | 测试类型 | 执行人 | 通过用例 | 失败用例 | 阻塞用例 | 备注 |
|---------|------|---------|--------|---------|---------|---------|------|
| 1 | 2024-01-15 | 冒烟测试 | 张三 | 26 | 0 | 0 | 基础功能验证 |
| 2 | 2024-01-16 | 功能测试 | 李四 | 24 | 2 | 0 | 详见缺陷列表 |
| 3 | 2024-01-17 | 压力测试 | 王五 | - | - | - | 5000并发通过 |
| 4 | 2024-01-18 | 安全测试 | 赵六 | 15 | 0 | 0 | 无高危漏洞 |

### 9.2 缺陷追踪
| 缺陷ID | 发现轮次 | 模块 | 严重度 | 标题 | 状态 | 修复版本 |
|--------|---------|------|--------|------|------|---------|
| DEF-001 | 2 | 课程管理 | 中 | 课程标题超长未校验 | 已修复 | v1.0.1 |
| DEF-002 | 2 | 直播功能 | 高 | 直播结束后回放地址未生成 | 修复中 | v1.0.2 |

### 9.3 性能测试结果
| 指标 | 目标值 | 实际值 | 状态 |
|------|--------|--------|------|
| 平均响应时间 (P50) | < 200ms | 156ms | ✅ |
| P99 响应时间 | < 500ms | 423ms | ✅ |
| 错误率 | < 0.1% | 0.02% | ✅ |
| 最大吞吐量 | > 1000 RPS | 1200 RPS | ✅ |
| 5000并发稳定运行 | 30分钟 | 30分钟 | ✅ |

---

## 10. 签收确认

| 角色 | 姓名 | 签名 | 日期 | 结论 |
|------|------|------|------|------|
| 测试负责人 | | | | 通过/不通过 |
| 开发负责人 | | | | 通过/不通过 |
| 运维负责人 | | | | 通过/不通过 |
| 产品经理 | | | | 通过/不通过 |
| 项目经理 | | | | 通过/不通过 |

---

**文档版本**: v1.0  
**编制日期**: 2024-01-15  
**下次评审**: 2024-04-15  
**文档归属**: 测试团队