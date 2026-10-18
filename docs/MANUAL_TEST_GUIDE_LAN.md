# 局域网人工测试操作指南（千人千面平台 · 全功能）

> 适用环境：校园局域网（10.167.2.0/24），双节点 K8s（10.167.2.175 / 10.167.2.176，Rancher 管理）
> 测试前完成部署：`./deploy-rancher.sh discover && deploy-rancher.sh images && deploy-rancher.sh deploy && deploy-rancher.sh verify`
> 记录模板：测试人 / 日期 / 用例编号 / 结果(通过/失败/阻塞) / 截图或备注

## 0. 访问入口与准备

| 入口 | 地址（hosts 或 DNS 解析后） | 账号 |
|---|---|---|
| 统一运维监测平台 | http://ops.platform.local/ | 管理员（Grafana 密码见 Secret `GRAFANA_PASSWORD`） |
| 平台 API | http://api.platform.local/api/v1/... | admin / admin123（演示） |
| Grafana | http://ops.platform.local/grafana | admin / Secret 值 |
| Milvus Attu | `kubectl -n infra port-forward svc/attu 3000:3000` → http://localhost:3000 | — |
| Pulsar | `kubectl -n infra run pulsar-cli ...`（见 §4） | — |

准备：测试机加入 hosts 解析（ops/api.platform.local → 入口 IP，MetalLB 10.167.2.200-220）；准备两个浏览器（双租户对照）。

## 1. 认证与多租户（iam-service）

| # | 操作 | 预期 |
|---|---|---|
| 1-1 | POST 登录（curl 或 Postman，带 `X-Tenant-Id: tenant-01`）admin/admin123 | 200，返回 accessToken/refreshToken |
| 1-2 | 错误密码登录 | 401/400，无 token |
| 1-3 | 带错误租户头访问课程列表（tenant-not-exist） | 请求正常隔离（缓存 key 不同），多租户日志中出现该租户 ID |
| 1-4 | 创建用户（含 STUDENT 角色）→ 用户列表 → 详情 → 激活 → 改信息 → 删除 | 全链路 2xx；列表可见新用户；删除后详情仍可查（软删） |
| 1-5 | 角色列表 / 系统角色 | 返回 ADMIN/TEACHER/STUDENT |

```bash
# 1-1 示例
curl -s -X POST http://api.platform.local/api/v1/auth/login \
  -H 'Content-Type: application/json' -H 'X-Tenant-Id: tenant-01' \
  -d '{"username":"admin","password":"admin123"}'
```

## 2. 课程域（course-service，多级缓存验证）

| # | 操作 | 预期 |
|---|---|---|
| 2-1 | 课程列表（两次相同请求） | 第二次明显更快（L1 命中）；L2 Redis 有 key 前缀 `qrr:courseDetail:t:tenant-01:...` |
| 2-2 | 课程详情 → 修改课程信息 → 再查详情 | 修改后缓存被失效（读到新值，验证 evict 广播） |
| 2-3 | 创建章节/知识点/资源 → 发布 | 状态流转 DRAFT→PUBLISHED；domain-events 主题收到事件（§4 验证） |

## 3. 教学域与知识追踪（teaching + kt-service）

| # | 操作 | 预期 |
|---|---|---|
| 3-1 | GET /api/v1/kt/mastery?studentId=test-01 | 200：overallMastery∈[0,1]，weakKps 列表，model=BKT+DKT |
| 3-2 | 同一 studentId 重复查询 | coldStart=false 且画像一致（命中预计算索引） |
| 3-3 | POST /api/v1/kt/predict（kps=[1,2,3], difficulty=3） | probability∈[0,1] + 建议 |
| 3-4 | 学习路径列表 / 作业 / 考试 / 讨论 | 200 分页 |

## 4. 消息总线（Pulsar，infra 命名空间）

| # | 操作 | 预期 |
|---|---|---|
| 4-1 | 触发一次课程创建（§2-3），随后：`kubectl -n infra exec pulsar-broker-0 -- bin/pulsar-admin --admin-url http://pulsar-broker:8080 topics list public/default` | 可见 domain-events / ai-generation-tasks / ai-generation-results / learning-behavior / resource-index-upsert |
| 4-2 | peek 消息内容：`bin/pulsar-client --url pulsar://localhost:6650 read -m 3 -s peek-test public/default/domain-events` | 可读 JSON 信封（eventId/eventType/tenantId） |
| 4-3 | 运维平台「Pulsar 消息」页：加载主题 → 统计 → peek 10 条 | 页面显示 stats（msgRateIn/backlog）与消息 JSON |

## 5. AI 服务（ai + agent + reranker）

| # | 操作 | 预期 |
|---|---|---|
| 5-1 | 同步生成：POST /api/v1/generation（KNOWLEDGE_POINT_TALK，欧姆定律） | 201；内容含“欧姆/电压”，产业情境出现“智能网联新能源汽车” |
| 5-2 | 六类型各生成一次，对照评测集结构要求（题组 8 题/讲解五段/任务书含安全+评分…） | 结构要件齐全 |
| 5-3 | 异步提交：POST /api/v1/generation/async | 202 + taskId（毫秒级） |
| 5-4 | 完成推送：浏览器打开学生端/网关 SSE（见 §7），再提交一个异步任务 | 收到 `generation-done` 推送；ai-generation-results 主题可读 |
| 5-5 | 质量评测（自动化）：`python evals/run_eval.py --ai http://api.platform.local/api/v1 --use-judge` | 通过率 ≥80%，报告生成于 evals/report/ |

## 6. 统一运维监测平台（ops-service + Grafana，管理员重点验收）

| # | 页面 | 操作与预期 |
|---|---|---|
| 6-1 | 总览 | 服务卡片全绿（ready=期望）；P95/QPS 曲线有数据；Grafana 总览大屏 iframe 正常显示 |
| 6-2 | 服务运行 | HPA 显示 current/desired 与指标利用率；熔断状态 PromQL 有数据（closed=0 值）；429 指标存在 |
| 6-3 | 缓存监控 | Redis 各节点在线（used_memory/hitRatio/OPS）；Milvus 集合列表；Nebula /stats 指标 |
| 6-4 | Pulsar 消息 | 同 §4-3 |
| 6-5 | 日志中心 | Loki 查询 `{namespace="qianrenqianmian"}` 返回容器日志；选择 Pod 实时日志滚动；动态日志级别切 DEBUG 后目标服务生效 |
| 6-6 | 告警 | 停掉任一副本（kubectl scale --replicas=1），1-2 分钟内总览出现 ServiceDown 告警，恢复后消除 |
| 6-7 | 主机资源 | Grafana「主机资源」看板显示 175/176 两台节点的负载/内存/磁盘/网络 |
| 6-8 | 每页 AI 助手 | 每个页面右下角 AI 悬浮球：提问“当前哪个服务异常？”→ 助手调用工具给出数据支撑的回答（流式） |

## 7. 前端三端（ops-web / edu-app）

| # | 端 | 操作 | 预期 |
|---|---|---|---|
| 7-1 | PC 运维端 | 打开 ops.platform.local 遍历 5 个页面 | 菜单/表格/图表正常，无控制台报错 |
| 7-2 | H5/App | uni-app `npm run dev:h5`，手机浏览器访问 | 四页 Tab 正常；每页 AI 助手（SSE）流式回复 |
| 7-3 | 微信小程序 | `npm run dev:mp-weixin` → 微信开发者工具导入 dist/dev/mp-weixin | 首页渲染；AI 助手走 **WebSocket** 通道（网关 /ws），提问有流式回复 |

## 8. 韧性与限流（1000 并发准备度人工抽查）

| # | 操作 | 预期 |
|---|---|---|
| 8-1 | 删除任一 course-service Pod | readiness 中断短暂后自动恢复；总览短暂黄色后回绿 |
| 8-2 | 用 `for i in $(seq 1 2000); do curl -s -o /dev/null -w "%{http_code}\n" -H 'X-Tenant-Id: flood' URL/api/v1/courses; done` 压单个租户 | 超过 1200×1.5/min 后出现 429 与 Retry-After |
| 8-3 | Locust：`locust -f locust/locustfile_1000u.py --headless -u 1000 -r 50 -t 10m --host http://<入口>` | 读 P95<800ms、错误率<0.5%、429<1% |
| 8-4 | K6：`k6 run tests/k6/stress_1000.js`（BASE_URL=入口） | thresholds 全绿（报告中留存 summary） |

## 9. 常见问题排查

| 现象 | 检查 | 处理 |
|---|---|---|
| 页面 404 | Ingress host 解析、命名空间 | `kubectl -n qianrenqianmian get ing` |
| AI 生成 503/超时 | LLM 端点、Pulsar 就绪 | `kubectl -n infra logs pulsar-broker-0`；configmap 的 LLM_BASE_URL |
| KT 404 | kt-service 未部署/迁移未更新 | `kubectl -n infra get pods -l app=kt-service` |
| Grafana iframe 空白 | 浏览器拒绝嵌入 | 确认 grafana.ini allow_embedding=true（本部署已配置） |
| Redis 429 误伤 | 配额设置过低 | 调整 `app.tenant.default-rate-per-min` |
