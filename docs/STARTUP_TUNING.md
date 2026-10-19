# 启动性能调优记录 (STARTUP_TUNING)

> 记录 PLPP 各 Java 服务集群实测启动基线与按轮次的调优改动。原则: 不猜根因、不破坏功能、默认行为不变(开关化)。

## 实测基线(集群冷启动, 集群时间点见 PROGRESS.md)

| 服务 | 实测启动耗时 | 备注 |
|------|------------|------|
| iam-service | 49.5s | 最慢; 疑似与 Spring Security/BCrypt 链路相关, **未验证, 不下根因结论** |
| tenant-service | 29.9s | |
| course-service | 14.6s | |
| teaching-service | 13.6s | |

> 依据 UIM 铁律(未验证推断误诊): 上表仅记录现象。iam-service 49.5s 的真实瓶颈需后续用
> `--debug` 启动日志 / Spring Bean 启动耗时(`spring.startup` metrics / ApplicationStartup)
> 实测定位, 本轮不做任何针对 BCrypt/安全链路的猜测性改动。

## 第 1 轮改动(2026-10-18)

### 1. Tomcat 线程调优补齐(对齐 ai-service)

ai-service 已有 `server.tomcat`(threads.max=200 / min-spare=20 / max-connections=8192 / accept-count=200)。
同段复制到以下服务(保留各自 port / context-path / `shutdown: graceful`):

- iam-service / tenant-service / course-service / teaching-service / ops-service
- graphql-gateway(spring-boot-starter-web = servlet 栈, 同样适用)

### 2. Spring 层

- 各服务 `spring.main.lazy-initialization: ${SPRING_MAIN_LAZY:false}` —— **默认 false, 行为不变**, 仅留开关。
- `spring.jpa.open-in-view: false` —— 5 个有 JPA 的服务(iam/tenant/course/teaching/ai)已存在, 无需新增。
- `spring.lifecycle.timeout-per-shutdown-phase` —— 各服务已有, 保持。

### 3. JVM 层(Dockerfile)

标准生产参数(确认各 Dockerfile 已有): `-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC`。
graphql-gateway 原为 `-XX:MaxRAMPercentage=70 -XX:+UseZGC`, 补 `-XX:+UseContainerSupport`。

开发期启动提速参数**不进默认值**, 仅经环境变量透传:

```
ENV JAVA_OPTS_DEV=""
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS $JAVA_OPTS_DEV -jar app.jar"]
```

用法(本地/开发镜像 build 后注入, 生产不设即零影响):

```
docker run -e JAVA_OPTS_DEV="-XX:TieredStopAtLevel=1 -noverify" ...
```

明确**不加** `-XX:+UseSerialGC`(会退化生产吞吐, 不符合"仅 env 可选"约束)。
k8s yaml 未改动。

### 4. 网关路由补齐(nginx-gateway/gateway.conf)

- 新增 `upstream ops_service`(server ops-service:8096, least_conn + keepalive 32, 风格对齐既有 upstream)。
- 新增 `location /api/v1/credit-transfer/` → `http://tenant_service`(proxy 头与 limit_req 样式照抄 tenants location)。
- 新增 `location /api/v1/ops/` → `http://ops_service`(api_limit burst=100)。

校验: 本地无全量 nginx 环境(缺 mime.types/ssl 证书路径), 无法 `nginx -t -c`; 以 grep 校验
upstream 与 location 成对出现, 输出见该轮回复。

## 后续待办

- [ ] iam-service 49.5s: 用 ApplicationStartup/启动日志分段计时实测定位后再优化(不猜测)。
- [ ] 重启一轮采集启动耗时, 与本文件基线对比; `SPRING_MAIN_LAZY=true` 开关验证开启后启动收益与首请求延迟。
