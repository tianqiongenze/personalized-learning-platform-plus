# 租户管理服务

个性化学习平台 Plus 的租户管理核心服务，提供多租户全生命周期管理能力。

## 功能特性

- **租户全生命周期管理**：创建、查询、更新、状态流转（草稿→激活→暂停/过期/锁定/删除→恢复）
- **多租户隔离**：基于行级安全策略 (RLS) 的数据隔离
- **资源配额管理**：用户数、课程数、存储空间、API调用频率、并发会话等多维度配额控制
- **品牌定制化**：Logo、主题色、自定义域名、邮件/登录页模板、CSS变量
- **功能开关与集成配置**：特性标志、SSO/LTI/xAPI集成、限制策略
- **管理员控制**：租户管理员指定、权限边界
- **审计日志**：全操作审计追踪
- **领域事件驱动**：租户创建、更新、状态变更、配额变更、品牌变更、删除、恢复等事件发布

## 技术栈

- **语言**：Java 21
- **框架**：Spring Boot 3.2+
- **数据库**：PostgreSQL 16 + pgvector
- **缓存**：Redis 7
- **ORM**：Spring Data JPA / Hibernate
- **映射**：MapStruct
- **文档**：SpringDoc OpenAPI 3 (Swagger UI)
- **测试**：JUnit 5, Mockito, AssertJ, Testcontainers
- **代码质量**：Checkstyle, SpotBugs
- **容器化**：Docker, Docker Compose, Helm

## 快速开始

### 前置要求

- Java 21+
- Maven 3.9+
- PostgreSQL 16+
- Redis 7+

### 本地开发

```bash
# 克隆项目
cd tenant-service

# 启动依赖服务
docker-compose up -d postgres redis

# 运行数据库迁移
mvn flyway:migrate

# 启动应用
mvn spring-boot:run
```

### Docker 部署

```bash
# 构建镜像
docker build -t tenant-service:1.0.0 .

# 启动完整栈
docker-compose up -d
```

### 访问服务

- **API 文档**：http://localhost:8081/swagger-ui.html
- **健康检查**：http://localhost:8081/actuator/health
- **指标监控**：http://localhost:8081/actuator/prometheus

## API 接口

### 租户管理

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | /api/v1/tenants | 创建租户 | SUPER_ADMIN |
| GET | /api/v1/tenants/{id} | 获取租户详情 | SUPER_ADMIN, TENANT_ADMIN |
| GET | /api/v1/tenants/code/{code} | 根据编码获取租户 | SUPER_ADMIN, TENANT_ADMIN |
| GET | /api/v1/tenants | 分页查询租户列表 | SUPER_ADMIN |
| GET | /api/v1/tenants/active | 获取所有激活态租户 | SUPER_ADMIN, TENANT_ADMIN |
| GET | /api/v1/tenants/statistics | 获取租户统计信息 | SUPER_ADMIN |
| PUT | /api/v1/tenants/{id}/info | 更新租户基本信息 | SUPER_ADMIN, TENANT_ADMIN |
| PUT | /api/v1/tenants/{id}/code | 更新租户编码(仅草稿态) | SUPER_ADMIN |
| PUT | /api/v1/tenants/{id}/quota | 更新租户配额 | SUPER_ADMIN |
| PUT | /api/v1/tenants/{id}/branding | 更新品牌定制 | SUPER_ADMIN, TENANT_ADMIN |
| PUT | /api/v1/tenants/{id}/config | 更新租户配置 | SUPER_ADMIN |
| PUT | /api/v1/tenants/{id}/admin | 更新租户管理员 | SUPER_ADMIN |

### 状态流转

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | /api/v1/tenants/{id}/activate | 激活租户(草稿→激活) | SUPER_ADMIN |
| POST | /api/v1/tenants/{id}/suspend | 暂停租户(激活→暂停) | SUPER_ADMIN |
| POST | /api/v1/tenants/{id}/restore | 恢复租户(暂停/过期/锁定/删除→激活) | SUPER_ADMIN |
| POST | /api/v1/tenants/{id}/expire | 过期租户(激活→过期) | SUPER_ADMIN |
| POST | /api/v1/tenants/{id}/lock | 锁定租户(激活→锁定) | SUPER_ADMIN |
| DELETE | /api/v1/tenants/{id} | 删除租户(软删除) | SUPER_ADMIN |

## 状态机

```
DRAFT → ACTIVE ↔ SUSPENDED
  ↓         ↓           ↓
  └────────→ EXPIRED ←─┘
  ↓         ↓           ↓
  └────────→ LOCKED ←───┘
  ↓         ↓           ↓
  └────────→ DELETED ───┘
       ↓
       └────────→ ACTIVE (恢复)
```

## 配置说明

### application.yml 关键配置

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/tenant_db
    username: postgres
    password: postgres
  redis:
    host: localhost
    port: 6379
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true

app:
  jwt:
    secret: ${JWT_SECRET}
    expiration: 86400000
  tenant:
    default-quota: starter
```

### 环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| POSTGRES_PASSWORD | PostgreSQL 密码 | postgres |
| REDIS_PASSWORD | Redis 密码 | (无) |
| JWT_SECRET | JWT 签名密钥 | 必须设置 |
| SPRING_PROFILES_ACTIVE | 激活配置文件 | dev |

## 测试

```bash
# 单元测试
mvn test

# 集成测试 (需要 Docker)
mvn verify -Pintegration-test

# 代码质量检查
mvn checkstyle:check spotbugs:check
```

## 监控指标

- `tenant_created_total` - 租户创建总数
- `tenant_status_changed_total` - 状态变更总数
- `tenant_quota_usage_percent` - 配额使用率
- `tenant_active_count` - 激活态租户数
- `tenant_repository_query_duration_seconds` - 仓储查询延迟

## 部署

### Kubernetes (Helm)

```bash
helm install tenant-service ./helm \
  --set image.tag=1.0.0 \
  --set config.spring.datasource.url=jdbc:postgresql://postgresql:5432/tenant_db \
  --set config.jwt.secret=${JWT_SECRET}
```

### 环境要求

- Kubernetes 1.28+
- PostgreSQL 16 (建议托管服务)
- Redis 7 (建议集群模式)
- Ingress Controller (nginx)
- cert-manager (TLS 证书)
- Prometheus + Grafana (监控)

## 目录结构

```
tenant-service/
├── src/
│   ├── main/
│   │   ├── java/com/platform/tenant/
│   │   │   ├── domain/           # 领域层
│   │   │   │   ├── model/        # 聚合根、值对象
│   │   │   │   ├── event/        # 领域事件
│   │   │   │   ├── repository/   # 仓储接口
│   │   │   │   └── service/      # 领域服务
│   │   │   ├── application/      # 应用层
│   │   │   │   ├── command/      # 命令
│   │   │   │   ├── query/        # 查询
│   │   │   │   └── service/      # 应用服务
│   │   │   ├── infrastructure/   # 基础设施层
│   │   │   │   ├── persistence/  # 持久化
│   │   │   │   ├── security/     # 安全
│   │   │   │   └── event/        # 事件发布
│   │   │   └── interfaces/       # 接口层
│   │   │       ├── rest/         # REST API
│   │   │       └── config/       # 配置
│   │   └── resources/
│   │       ├── application.yml
│   │       └── db/migration/     # Flyway 迁移脚本
│   └── test/                     # 测试代码
├── helm/                         # Helm Chart
├── docker-compose.yml
├── Dockerfile
├── pom.xml
└── README.md
```

## 许可证

Apache License 2.0