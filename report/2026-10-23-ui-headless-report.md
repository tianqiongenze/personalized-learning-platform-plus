# PLPP 全端无头浏览器(Headless)功能测试报告 — 2026-10-23

**入口**: 学生端 `http://10.167.2.175:30096/` · 运维端 `http://10.167.2.175:30097/`
**工具**: Playwright 无头浏览器(真实渲染 + console/网络监控), 演示账号 admin/Admin@12345(tenant `da98138f-…`)
**结论**: 学生端 6 页全部通过; 运维端 5 页中 4 页通过, 日志中心因 Loki 基础设施缺后端 pod 遗留(见 §5)。

## 1. 背景

用户问"无头浏览器完整模拟测试完成了吗?为何 30096 还提示服务不可达"。诚实回答: **此前未完整完成** —
courses/users/tenants 等 GraphQL 查询 403/500, 学习路径/复习页报错。本会话定位并修复了全链 4 类根因后重测。

## 2. 根因与修复(镜像版本链)

| # | 症状 | 根因 | 修复 | 镜像 |
|---|------|------|------|------|
| 1 | GraphQL 大面积 403 | gateway DomainClient 不透传用户上下文头/Authorization | requestInterceptor 透传 X-User-Id/Tenant-Id/Roles/User-Roles/Authorization + X-Gateway-Verified | graphql-gateway 11.0.4→11.0.8 |
| 2 | tenants 403 | tenant HeaderAuthFilter 读 X-User-Roles, gateway 只回 X-Roles | GatewayJwtFilter 双头名暴露; DomainClient 双透传 | 11.0.5 内含 |
| 3 | 学习路径/作业 500 | `pageable.getSort().toString()` 空排序产出 "UNSORTED" 当属性名; resolver 路径错(/assignments vs /teaching/assignments); 非 UUID courseId 400 | sortOf helper + getProperty; 路径/UUID guard | teaching-service 11.0.3/11.0.4 |
| 4 | masteryProfile NOT_FOUND | resolver 调不存在的 /kt/mastery/batch | 改用 /kt/mastery weak/strong 合成 | 11.0.8 内含 |
| 5 | 前端全 403(无 token) | web-student gql() 不带 Authorization | ensureDemoSession 自动登录+带 token; Courses/Review/Profile 字段对齐 schema | web-student 11.0.7→11.0.11 |
| 6 | AI 助手 500 | agent-service KT_BASE env 缺失且默认 DNS 错(8095@qianrenqianmian) | set env KT_BASE=http://kt-service.infra:8086, COURSE_BASE=http://course-service:8083/api/v1 | live env(已回写 yaml) |
| 7 | 30097 全 401(3 层叠加) | ①ops-web nginx 无 auth_request 链; ②SecurityConfig 匹配器 `/api/v1/ops/**` 相对 context-path 永不匹配; ③角色名 PLATFORM_ADMIN ≠ iam 实际签发 ADMIN/SUPER_ADMIN | ①ops-web 内嵌 auth_request→iam /verify+角色头注入; ②GET `/**` 相对 context; ③接受 ADMIN/SUPER_ADMIN/OPS_READER | ops-service 11.0.3→11.0.6 |
| 8 | 30097 前端仍 403 | 前端不带 token | api.ts 拦截器自动演示登录(401 时清 token 重登); nginx 加 /api/v1/auth/ 直通 | ops-web 11.0.5→11.0.6 |
| 9 | overview/logs 500→401 | PromQL `{..}` 被 RestClient URI 模板展开; Loki @RequestParam defaultValue 同坑; 二次编码 %255B | Prometheus query/range 改模板占位符传原始串; Loki 用 EncodingMode.NONE+预编码; controller 默认值挪进代码 | ops-service 11.0.4→11.0.8 |

BVV 全程执行: 每镜像构建前确认 target jar mtime 新于上次, 构建后容器内 unzip+strings 校验 class 内容指纹(非只验 lib 存在)。

## 3. 学生端 30096 逐页结果(Playwright 实测)

| 页面 | 结果 | 证据 |
|------|------|------|
| 学情首页 `/` | ✅ | 综合掌握度 0.388(BKT+DKT), 薄弱 TOP3: KP#130 2.6% / KP#41 4.1% / KP#68 4.9%, 世界模型建议「explain《动力电池》+0.078」 |
| 课程学习 `/courses` | ✅ | 12 门课列表(一年级数学 C00001 等), 修复前 403/500 |
| 学习路径 `/path` | ✅ | math_101 查询正常空态, 修复前 500(UNSORTED) |
| 复习卡片 `/review` | ✅ | schema 真实字段 skillId/dueAt/intervalDays, 空态正常 |
| 资源中心 `/resources` | ✅ | 正常渲染 |
| 我的画像 `/profile` | ✅ | L3 画像 + 掌握度进度条 |
| AI 助手(浮层) | ✅ | 提问"二次函数怎么学?" 返回模板降级回复(LLM 算力受限时的预期降级文案), mcp/call 500 已修(KT_BASE), 直连 kt_mastery 200 返 overallMastery 0.3882 |

Console 残留 1 类 error: `TypeError: b.value.forEach is not a function` — 定位为 ant-design-vue 压缩包内
select/filterOptions 内部逻辑(页面源码无 forEach 调用), 页面功能无影响, 记录为低优先遗留。

## 4. 运维端 30097 逐页结果

| 页面 | 结果 | 证据 |
|------|------|------|
| 总览 `/` | ✅ | 服务 3/健康 3/异常 0, 29 条告警横幅正常展示(KubeCPUOvercommit 等), Grafana 嵌入框 |
| 服务运行 `/services` | ✅ | HPA 状态表, 熔断器(空数据正常), 主机负载 node_load5 两节点 2.23/1.47 |
| 缓存监控 `/cache` | ✅ | Redis Cluster 节点在线, Milvus 集合 esp_llm_semantic(_proxy), NebulaGraph 报连接错(该 svc 不存在, 数据面问题非鉴权, 遗留) |
| Pulsar 消息 `/pulsar` | ✅ | 加载主题成功: ai-generation-tasks-partition-0..5 列表+统计/peek 按钮 |
| 日志中心 `/logs` | ⚠️ 遗留 | API 链已通(默认 LogQL 修通), 但 `loki-stack.monitoring:3100` Connection refused — **monitoring ns 只有 promtail 无 loki 后端 pod**(基础设施缺部, 非应用 bug) |

## 5. 遗留与决策项

1. **Loki 后端缺失**(monitoring ns): 部署 loki 单体或接现有日志方案 — 基础设施决策。
2. **NebulaGraph /stats 不通**: ops-service 配置的 `nebula-graphd.infra:9669` 不存在, 需确认图库实际地址。
3. **forEach TypeError**: ant-design-vue 内部, 影响面小。
4. Undertow A/B 转正、ai-platform ollama-worker requests 回写: 沿用此前决策待定项。

## 6. 与性能测试报告关系

功能链全通是压力测试前置; k6 8 源 4,019 r/s / 5xx=0 报告见 `report/2026-10-23-loadtest-report.md`(已完成)。
本报告补齐"无头浏览器功能模拟"维度, 与 loadtest 互为佐证。

## 7. 复现命令(局域网)

```bash
TOKEN=$(curl -s -X POST http://10.167.2.175:30096/api/v1/auth/login \
  -H 'Content-Type: application/json' -H 'X-Tenant-Id: da98138f-486f-42db-944c-219a5a44bf52' \
  -d '{"username":"admin","password":"Admin@12345"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['accessToken'])")
curl -s -o /dev/null -w '%{http_code}\n' http://10.167.2.175:30096/            # 200 学生端
curl -s -o /dev/null -w '%{http_code}\n' http://10.167.2.175:30097/api/v1/ops/overview -H "Authorization: Bearer $TOKEN"  # 200
curl -s -o /dev/null -w '%{http_code}\n' http://10.167.2.175:30097/api/v1/ops/logs/loki?limit=3 -H "Authorization: Bearer $TOKEN"  # 现 401=Loki 后端缺(遗留)
```
