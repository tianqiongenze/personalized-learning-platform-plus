# ADR-006: Servlet 容器从 Tomcat 替换为 Undertow(经 A/B 压测验证)

- 状态: Proposed(压测已通过, 待决策切换现网)
- 日期: 2026-10-20
- 关联: report/2026-10-20-tomcat-ab-test.md

## 背景与动机

现有 5 个 Java 服务(iam/tenant/course/teaching/ops)使用 Spring Boot 3.5.6 默认内嵌 Tomcat。用户明确要求不做 Tomcat 线程调优, 而是用更高性能容器引擎**替换** Tomcat, 并以 A/B 测试验证。

## 选型对比

| 候选 | 结论 | 理由 |
|---|---|---|
| **Undertow(选定)** | A 组实施 | Spring Boot 官方 starter 支持; servlet 栈**零业务代码改动**, 仅替换 starter 依赖; XNIO NIO 模型, 小内存占用, 高吞吐低延迟; ESP 先例即走容器替换路线 |
| Netty/WebFlux | 本轮不做 | 需将 servlet 栈整体迁到 reactive(Handler/Router), 5 个服务的 Controller/Filter/Security 链全部重写, 改动面与回归风险不匹配本轮目标, 记入后续演进选项 |
| Jetty | 备选 | Spring Boot 4 已移除 Undertow starter 时可回退 Jetty(ESP 先例 bff-java 即因 Spring Boot 4 用 Jetty); 本项目仍在 Boot 3.5, Undertow 可用 |

## 实现(源码层 A 组, 不动 B 组现网)

1. 5 服务 `pom.xml`: `spring-boot-starter-web` 排除 `spring-boot-starter-tomcat`, 新增 `spring-boot-starter-undertow`。
2. 5 服务 `application.yml`: 增加 `server.undertow.io-threads: 8 / worker-threads: 200 / buffer-size: 16384 / direct-buffers: true`(8 核节点 io=核数, worker=200 与原 Tomcat max-threads 对齐); 原 `server.tomcat.*` 段保留但 Undertow 忽略, 回切时无需恢复配置。
3. 镜像 tag `11.0.1-undertow`(buildx linux/amd64, push 10.167.2.175:30500)。
4. 部署为独立 Deployment(`*-service-undertow`, 1 副本, 克隆原 Deployment 的 env/probe/resources)+ 独立 NodePort Service, **现网 B 组(Tomcat 11.0.0)未做任何变更**。

## A/B 验证结果摘要(100 VU × 3 min × 3 轮取中位数)

- course-service(读密集): Undertow P50 8.61ms vs Tomcat 9.29ms(-7.3%); P95 16.95 vs 19.84(-14.6%); RPS 基本持平(~380/s, 均为客户端并发上限)。
- iam-service(登录写密集, 含 BCrypt 校验): Undertow P50 5.76ms vs Tomcat 6.79ms(-15.2%); P95 10.83 vs 12.51(-13.4%); P99 27.48 vs 38.54(-28.7%); RPS 387 vs 385。
- 12 轮 server_errors 全部 0.00%, 两容器引擎稳定性无差异。
- 节点侧(master 175 / worker 176)CPU 均在 90%+ idle 区间, 负载远未到资源瓶颈, 差异纯来自容器引擎路径开销。

**结论**: Undertow 在读/写两类路径下 P50/P95/P99 全面优于 Tomcat, 其中长尾(P99)改善最明显。建议切换, 方式见下。

## 切换方式(现网)

镜像 tag `11.0.1-undertow` 已在仓库, 切换 = 现网 5 个 Deployment 的 image 改为 `11.0.1-undertow` 并滚动更新; 回切 = 改回 `11.0.0`(配置文件 tomcat 段仍保留, 回切零成本)。应用 Undertow 后可删除 `server.tomcat` 段。

## 风险与缓解

- Undertow 对部分 Tomcat 专有配置(`server.tomcat.*`)不生效——已确认当前 5 服务未使用 Tomcat 专有特性(仅 threads/max-connections/accept-count, 已由 undertow 等价配置覆盖)。
- graceful shutdown: `server.shutdown: graceful` 对 Undertow 同样生效(Spring Boot 通用配置)。
- A 组压测 Deployment 压测后保留(scale 至 1), 供后续复测; 确认切换后可删除。
