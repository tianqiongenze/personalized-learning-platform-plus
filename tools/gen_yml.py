#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""为四个服务生成重构后的 application.yml(环境变量驱动, 对接集群既有中间件)"""
import io

TPL = """server:
  port: {port}
  servlet:
    context-path: /api/v1
  shutdown: graceful

spring:
  application:
    name: {name}
  profiles:
    active: ${{SPRING_PROFILES_ACTIVE:dev}}
  lifecycle:
    timeout-per-shutdown-phase: 30s
  datasource:
    # 生产: 集群既有 CockroachDB (CRDB_JDBC_HOST_PORT 由 ConfigMap 注入)
    # 开发: 本地 PostgreSQL
    url: jdbc:postgresql://${{CRDB_JDBC_HOST_PORT:localhost:5432}}/{db}?sslmode=${{CRDB_SSLMODE:disable}}
    username: ${{DB_USERNAME:postgres}}
    password: ${{DB_PASSWORD:postgres}}
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      idle-timeout: 300000
      connection-timeout: 20000
      leak-detection-threshold: 60000
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    open-in-view: false
    properties:
      hibernate:
        # CRDB 与 PG 双兼容: 通过 profile 切换方言(dev=PostgreSQL / prod=Cockroach)
        dialect: ${{HIBERNATE_DIALECT:org.hibernate.dialect.CockroachDialect}}
        jdbc.batch_size: 30
        order_inserts: true
        order_updates: true
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
    validate-on-migrate: true
  data:
    redis:
      # 多级缓存 L2: 集群既有 Redis Cluster(6 节点); 本地开发回退单机
      cluster:
        nodes: ${{REDIS_CLUSTER_NODES:localhost:6379}}
        max-redirects: 3
      timeout: 3s
      password: ${{REDIS_PASSWORD:}}
      lettuce:
        cluster:
          refresh:
            adaptive: true       # 拓扑自适应: 主从切换/moved 自动感知
            period: 30s
        pool:
          max-active: 20
          max-idle: 10
          min-idle: 5
  pulsar:
    client:
      service-url: ${{PULSAR_SERVICE_URL:pulsar://localhost:6650}}
    producer:
      send-timeout: 5s
    consumer:
      subscription-name: ${{spring.application.name}}-sub

# ---- 多级缓存(platform-common 自动装配): L1 Caffeine + L2 Redis Cluster ----
# 以及事件总线、服务韧性开关、CORS —— 统一挂在 app: 之下
app:
  cache:
    enabled: true
    metrics: true
    l1:
      max-size: 10000
      expire-after-write: 5m
      expire-after-access: 10m
    l2:
      time-to-live: 30m
      cache-null-values: false
      key-prefix: "{short}:"
      invalidation-channel: "plp:cache:invalidate"
    specs:
      # 按业务缓存名精细化配置(示例)
      courseDetail:
        max-size: 20000
        expire-after-write: 10m
        redis-ttl: 60m
  events:
    enabled: true
    default-topic: persistent://public/default/domain-events
  resilience:
    enabled: true
  cors:
    allowed-origins: "${{CORS_ORIGINS:*}}"
    allowed-methods: GET,POST,PUT,PATCH,DELETE,OPTIONS
    allowed-headers: "*"
    allow-credentials: true
    max-age: 3600

# ---- 服务韧性(Resilience4j): 状态由统一运维平台经 Micrometer 读取 ----
resilience4j:
  circuitbreaker:
    instances:
      default:
        sliding-window-size: 20
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
        permitted-number-of-calls-in-half-open-state: 5
        automatic-transition-from-open-to-half-open-enabled: true
        register-health-indicator: true
  ratelimiter:
    instances:
      default:
        limit-for-period: 200
        limit-refresh-period: 1s
        timeout-duration: 500ms
        register-health-indicator: true
  retry:
    instances:
      default:
        max-attempts: 3
        wait-duration: 500ms
        enable-exponential-backoff: true

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus,loggers,caches,circuitbreakers,ratelimiters,archaius
  endpoint:
    health:
      show-details: always
      probes:
        enabled: true
      group:
        readiness:
          include: readinessState,db,redis
  metrics:
    tags:
      application: ${{spring.application.name}}
      namespace: ${{POD_NAMESPACE:platform-plus}}
    distribution:
      percentiles-histogram:
        http.server.requests: true
      slo:
        http.server.requests: 100ms,300ms,500ms,1s

logging:
  level:
    {pkg}: ${{LOG_LEVEL:INFO}}
    org.springframework.web: INFO
  pattern:
    console: "%d{{yyyy-MM-dd HH:mm:ss.SSS}} [%thread] %-5level %logger{{36}} [trace=%X{{traceId:-}}] - %msg%n"
"""

services = [
    ("tenant-service",  8081, "tenant_db",  "tenant",  "com.platform.tenant"),
    ("iam-service",     8082, "iam_db",     "iam",     "com.platform.iam"),
    ("course-service",  8083, "course_db",  "course",  "com.platform.course"),
    ("teaching-service",8084, "teaching_db","teaching","com.platform.teaching"),
]

for name, port, db, short, pkg in services:
    yml = TPL.format(name=name, port=port, db=db, short=short, pkg=pkg)
    io.open(f"{name}/src/main/resources/application.yml", "w", encoding="utf-8", newline="\n").write(yml)
    print(f"{name}/application.yml rewritten")
