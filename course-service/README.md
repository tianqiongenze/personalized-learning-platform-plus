# 课程资源服务

个性化学习平台 Plus 的课程资源管理核心服务，提供课程、章节、知识点、资源的全生命周期管理能力。

## 功能特性

- **课程全生命周期管理**：创建、查询、更新、发布、归档、恢复、删除
- **章节管理**：层级结构、排序、难度、预估学时、知识点关联
- **知识点管理**：编码、难度、前置依赖、关联知识点、元数据
- **资源管理**：多类型支持(视频/文档/练习/考试/SCORM/H5P/链接/直播)、版本控制、难度、标签
- **多租户隔离**：基于租户ID的数据隔离
- **状态机驱动**：严格的状态流转规则
- **领域事件驱动**：创建、更新、状态变更、删除等事件发布
- **版本控制**：资源版本管理

## 技术栈

- **语言**：Java 21
- **框架**：Spring Boot 3.2+
- **数据库**：PostgreSQL 16
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
cd course-service

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
docker build -t course-service:1.0.0 .

# 启动完整栈
docker-compose up -d
```

### 访问服务

- **API 文档**：http://localhost:8083/swagger-ui.html
- **健康检查**：http://localhost:8083/actuator/health
- **指标监控**：http://localhost:8083/actuator/prometheus

## API 接口

### 课程管理

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | /api/v1/courses | 创建课程 | TEACHER, ADMIN |
| GET | /api/v1/courses/{id} | 获取课程详情 | TEACHER, STUDENT, ADMIN |
| GET | /api/v1/courses/code/{code} | 根据编码获取课程 | TEACHER, STUDENT, ADMIN |
| GET | /api/v1/courses | 分页查询课程列表 | TEACHER, ADMIN |
| PUT | /api/v1/courses/{id}/info | 更新课程基本信息 | TEACHER, ADMIN |
| PUT | /api/v1/courses/{id}/code | 更新课程编码(仅草稿态) | TEACHER, ADMIN |
| PUT | /api/v1/courses/{id}/settings | 更新课程设置 | TEACHER, ADMIN |
| POST | /api/v1/courses/{id}/publish | 发布课程(草稿→发布) | TEACHER, ADMIN |
| POST | /api/v1/courses/{id}/archive | 归档课程(发布→归档) | TEACHER, ADMIN |
| POST | /api/v1/courses/{id}/restore | 恢复发布(归档→发布) | TEACHER, ADMIN |
| DELETE | /api/v1/courses/{id} | 删除课程 | TEACHER, ADMIN |

### 章节管理

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | /api/v1/courses/{courseId}/chapters | 创建章节 | TEACHER, ADMIN |
| GET | /api/v1/chapters/{id} | 获取章节详情 | TEACHER, STUDENT, ADMIN |
| GET | /api/v1/courses/{courseId}/chapters | 分页查询章节列表 | TEACHER, STUDENT, ADMIN |
| PUT | /api/v1/chapters/{id}/info | 更新章节信息 | TEACHER, ADMIN |
| PUT | /api/v1/chapters/{id}/code | 更新章节编码(仅草稿态) | TEACHER, ADMIN |
| PUT | /api/v1/chapters/{id}/order | 更新章节排序 | TEACHER, ADMIN |
| POST | /api/v1/chapters/{id}/knowledge-points | 添加知识点 | TEACHER, ADMIN |
| DELETE | /api/v1/chapters/{id}/knowledge-points/{kpId} | 移除知识点 | TEACHER, ADMIN |
| POST | /api/v1/chapters/{id}/publish | 发布章节 | TEACHER, ADMIN |
| DELETE | /api/v1/chapters/{id} | 删除章节 | TEACHER, ADMIN |

### 知识点管理

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | /api/v1/courses/{courseId}/knowledge-points | 创建知识点 | TEACHER, ADMIN |
| GET | /api/v1/knowledge-points/{id} | 获取知识点详情 | TEACHER, STUDENT, ADMIN |
| GET | /api/v1/courses/{courseId}/knowledge-points | 分页查询知识点列表 | TEACHER, STUDENT, ADMIN |
| PUT | /api/v1/knowledge-points/{id}/info | 更新知识点信息 | TEACHER, ADMIN |
| PUT | /api/v1/knowledge-points/{id}/code | 更新知识点编码 | TEACHER, ADMIN |
| POST | /api/v1/knowledge-points/{id}/prerequisites | 添加前置知识点 | TEACHER, ADMIN |
| DELETE | /api/v1/knowledge-points/{id}/prerequisites/{prereqId} | 移除前置知识点 | TEACHER, ADMIN |
| POST | /api/v1/knowledge-points/{id}/related-points | 添加关联知识点 | TEACHER, ADMIN |
| DELETE | /api/v1/knowledge-points/{id}/related-points/{relatedId} | 移除关联知识点 | TEACHER, ADMIN |
| PUT | /api/v1/knowledge-points/{id}/metadata | 更新元数据 | TEACHER, ADMIN |
| DELETE | /api/v1/knowledge-points/{id} | 删除知识点 | TEACHER, ADMIN |

### 资源管理

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | /api/v1/courses/{courseId}/resources | 创建资源 | TEACHER, ADMIN |
| GET | /api/v1/resources/{id} | 获取资源详情 | TEACHER, STUDENT, ADMIN |
| GET | /api/v1/courses/{courseId}/resources | 分页查询资源列表 | TEACHER, STUDENT, ADMIN |
| PUT | /api/v1/resources/{id}/info | 更新资源信息 | TEACHER, ADMIN |
| PUT | /api/v1/resources/{id}/metadata | 更新资源元数据 | TEACHER, ADMIN |
| POST | /api/v1/resources/{id}/knowledge-points | 添加关联知识点 | TEACHER, ADMIN |
| DELETE | /api/v1/resources/{id}/knowledge-points/{kpId} | 移除关联知识点 | TEACHER, ADMIN |
| POST | /api/v1/resources/{id}/publish | 发布资源 | TEACHER, ADMIN |
| POST | /api/v1/resources/{id}/versions | 创建新版本 | TEACHER, ADMIN |
| DELETE | /api/v1/resources/{id} | 删除资源 | TEACHER, ADMIN |

## 状态机

### 课程状态
```
DRAFT → PUBLISHED ↔ ARCHIVED
  ↓         ↓           ↓
  └────────→ DELETED ───┘
```

### 章节状态
```
DRAFT → PUBLISHED
  ↓
  └────────→ DELETED
```

### 资源状态
```
DRAFT → PUBLISHED
  ↓
  └────────→ DELETED
```

## 配置说明

### application.yml 关键配置

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/course_db
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
  cors:
    allowed-origins: "*"
    allowed-methods: GET,POST,PUT,PATCH,DELETE,OPTIONS
    allowed-headers: "*"
    allow-credentials: true
    max-age: 3600
```

### 环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| POSTGRES_PASSWORD | PostgreSQL 密码 | postgres |
| REDIS_PASSWORD | Redis 密码 | (无) |
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

- `course_created_total` - 课程创建总数
- `course_status_changed_total` - 课程状态变更总数
- `chapter_created_total` - 章节创建总数
- `knowledge_point_created_total` - 知识点创建总数
- `resource_created_total` - 资源创建总数
- `course_repository_query_duration_seconds` - 仓储查询延迟

## 部署

### Kubernetes (Helm)

```bash
helm install course-service ./helm \
  --set image.tag=1.0.0 \
  --set config.spring.datasource.url=jdbc:postgresql://postgresql:5432/course_db
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
course-service/
├── src/
│   ├── main/
│   │   ├── java/com/platform/course/
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