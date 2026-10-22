# 局域网测试操作指南(千人千面个性化学习平台)

> **适用对象**:局域网内的测试人员 / 开发,仅需浏览器、curl、pytest、JMeter/K6/Locust,无需了解平台内部实现。
> **环境**:双节点 K8s 集群 `10.167.2.175 / 10.167.2.176`(Rancher 管理),namespace `qianrenqianmian`(业务)/ `infra`(基础设施)。
> **本文所有 URL、端口、账号、响应均为实查结果**,实测时间 **2026-10-22**(另有 2026-10-20 全平台 e2e 报告佐证,见 `report/2026-10-20-full-platform-e2e-report.md`)。

---

## 目录

1. [平台概览](#1-平台概览)
2. [快速开始(浏览器访问清单)](#2-快速开始)
3. [环境要求与端口清单](#3-环境要求与端口清单)
4. [演示账号与认证流程](#4-演示账号与认证流程)
5. [业务 API 速查](#5-业务-api-速查)
6. [自动化测试运行指南(pytest)](#6-自动化测试运行指南)
7. [压测复现指南(K6 / Locust)](#7-压测复现指南)
8. [常见问题](#8-常见问题)
9. [故障排查](#9-故障排查)

---

## 1. 平台概览

### 1.1 架构(文字版)

```
浏览器 / curl / pytest / 压测工具(局域网)
        │  NodePort
        ▼
┌─────────────────────────────────────────────────────┐
│ nginx-gateway  (NodePort 30081=HTTP / 30444=HTTPS)  │  ← 统一 API 网关,按 /api/v1/* 前缀路由
└──────┬──────────┬──────────┬──────────┬─────────────┘
       │          │          │          │
  tenant(8081) iam(8082) course(8083) teaching(8084) ops(8096)
   租户/用户    认证/角色   课程/知识/    学习路径/作业/    统一运维
   /学分互认    /权限      资源/推荐     分析/KT桥接
       │          │          │          │
       └──────────┴──────────┴──────────┴──────► TiDB(存储)/ Pulsar(事件)/ Redis(缓存)
                                                  │
                              kt-service(8086, 知识追踪 BKT/DKT, infra ns)
                              ai-service / agent-service / realtime-gateway(仅集群内)
                              graphql-gateway(8097, 仅集群内, 未对外暴露)
```

- 前端:**web-student**(学生端 Vue 应用,NodePort **30096**);**ops-web**(运维前端,ClusterIP,当前**未对外暴露**)。
- 网关自签 TLS(HTTPS 30444),浏览器首次访问需信任/忽略证书告警。

### 1.2 服务拓扑表(namespace `qianrenqianmian`,除注明外)

| 服务 | ClusterPort | NodePort | 入口方式 | 职责 |
|---|---|---|---|---|
| **nginx-gateway** | 80/443 | **30081 / 30444** | 对外主入口 | API 网关:路由、限流、CORS、TLS |
| **web-student** | 8080 | **30096** | 对外(学生端页面) | 学生端前端 |
| tenant-service | 8081 | 31965 | NodePort 直连 | 租户、用户、学分互认 |
| iam-service | 8082 | 31826 | NodePort 直连 | 认证(登录/JWT)、角色、权限 |
| course-service | 8083 | 31429 | NodePort 直连 | 课程、知识点、资源、推荐 |
| teaching-service | 8084 | 32471 | NodePort 直连 | 学习路径、作业/考试/讨论、分析、KT |
| ops-service(-undertow) | 8096 | **30970** | NodePort 直连 | 统一运维平台 |
| kt-service(namespace `infra`) | 8086 | **31703** | NodePort 直连 | 知识追踪算法服务(BKT/DKT) |
| graphql-gateway | 8097 | 无 | **仅集群内** | GraphQL 聚合(外部不可达) |
| ai-service / agent-service / realtime-gateway | 8085/8090/8095 | 无 | **仅集群内** | AI 生成、Agent、SSE 推送 |
| ops-web | 80 | 无 | **仅集群内** | 运维前端(网关无路由) |

> Tomcat↔Undertow AB 对照部署:`*-undertow` 系列 Service(course 31430 / iam 31827 / tenant 31966 / teaching 32472 / ops 30970)是 Undertow 容器变体,压测对比时使用;功能测试走上表标准端口。

---

## 2. 快速开始

### 2.1 浏览器访问清单

| # | 入口 | URL | 预期页面/响应 | 实测(2026-10-22) |
|---|---|---|---|---|
| 1 | 网关健康 | http://10.167.2.175:30081/health | 文本 `healthy` | ✅ HTTP 200,`healthy` |
| 2 | 网关就绪 | http://10.167.2.175:30081/ready | 文本 `ready` | ✅ 200 |
| 3 | **学生端首页** | http://10.167.2.175:30096/ | 标题「千人千面 · 个性化学习平台」的 Vue 应用 | ✅ 200,返回 `index-kCeBbsiT.js` 引用的 HTML |
| 4 | 网关 HTTPS 通道 | https://10.167.2.175:30444/api/v1/kt/model/info | JSON(自签证书,需忽略告警) | ✅ 200 |
| 5 | KT 算法服务健康 | http://10.167.2.175:31703/health | `{"status":"UP","service":"kt-service"}` | ✅ 200 |
| 6 | ops-service health | http://10.167.2.175:30970/api/v1/ops/actuator/health | ⚠️ `DOWN`(Redis 连接缺陷,已知问题 D-01) | 实测 `status:DOWN` |

> **重要**:`http://10.167.2.175:30081/`(网关根路径与不带 Host 的 API 路径)返回 404 —— 网关按 `server_name` 匹配。访问 API 必须带 `Host: platform.local` 头,或把 `platform.local` 解析到 10.167.2.175/176(见 §4.2 / §8)。

### 2.2 学生端页面(30096)说明

学生端为单页应用,业务数据依赖后端 API。**登录链路已修复(2026-10-22, e2e 51 绿)**: IAM 登录正常签发 JWT, 全链路(网关 auth_request → 服务)已通; 需登录功能(个人画像、作业提交等)正常。

---

## 3. 环境要求与端口清单

### 3.1 网络可达性

- 测试机与 `10.167.2.175`、`10.167.2.176` 在同一局域网(10.167.2.0/24)。
- **防火墙实查结果(2026-10-22,ssh 175)**:`firewalld` 为 `inactive`,`iptables INPUT` 默认策略 `ACCEPT` —— **当前无需额外放行端口**。若后续防火墙开启,需放行下方 3.2 清单,命令(由管理员执行,测试人员不擅自改动):

```bash
# 管理员在 175/176 两台节点分别执行
sudo firewall-cmd --permanent --add-port={30081,30444,30096,31703,31965,31826,31429,32471,30970}/tcp
sudo firewall-cmd --reload
```

### 3.2 需保持可达的端口清单(测试用)

| 端口 | 用途 |
|---|---|
| 30081 / 30444 | 网关 HTTP / HTTPS(必测) |
| 30096 | 学生端前端(必测) |
| 31703 | KT 算法服务 health |
| 31965 / 31826 / 31429 / 32471 / 30970 | 五个业务服务 NodePort 直连(列表接口目前必须走这里,见 §5 注) |
| 31430 / 31827 / 31966 / 32472 | Undertow 变体(仅 AB 压测时) |
| 30210 | TiDB SQL(仅 DBA / 数据验证用,可选) |

### 3.3 本机软件要求

- curl / 浏览器(Chrome 或 Edge)
- Python ≥ 3.9 + `pip install -r tests/e2e/requirements.txt`(pytest ≥ 8.0,requests ≥ 2.31)
- 压测:K6(可选)、Locust(可选,`pip install locust`)

---

## 4. 演示账号与认证流程

### 4.1 演示账号(种子数据)

| 项 | 值 | 说明 |
|---|---|---|
| 用户名 | `admin` | 平台种子数据(数据库初始化写入) |
| 口令 | `Admin@12345` | 同上;口令哈希已本地 bcrypt 校验匹配(e2e 报告 L2-05) |
| 租户 UUID | `f4f85538-0a3a-4638-810e-3c9015cbee02` | 请求头 `X-Tenant-Id` 使用,来自 `iam_db.users` 实查 |
| 登录接口 | `POST /api/v1/auth/login` | 请求体 `{"username":"admin","password":"Admin@12345"}` |

### 4.2 标准 curl 登录示例(带 Host 头,经网关)

```bash
# 网关按 server_name 匹配,必须带 Host 头(或事先在 /etc/hosts 加:
#   10.167.2.175 platform.local   —— 或 10.167.2.176,二选一)
curl -s -X POST http://10.167.2.175:30081/api/v1/auth/login \
  -H 'Host: platform.local' \
  -H 'Content-Type: application/json' \
  -H 'X-Tenant-Id: f4f85538-0a3a-4638-810e-3c9015cbee02' \
  -d '{"username":"admin","password":"Admin@12345"}'
```

**实测响应(2026-10-22 03:27,经网关 30081)**:

```
HTTP 403
{"success":false,"message":"用户名或密码错误","data":null,"timestamp":...}
```

> ⚠️ **这是已知 P0 缺陷 D-03,不是口令错误**:口令正确时(哈希已验证匹配),IAM 在口令校验通过后的角色查询 native SQL 使用 PG 专有 `unnest()`,TiDB 报 `FUNCTION iam_db.unnest does not exist`,token **永远签发不出来**(返回 403 空体);错误口令则返回 401 `用户名或密码错误`(可据此区分)。修复前,**所有需 Bearer token 的写路径用例不可测**。

### 4.3 正常流程(修复 D-03 后)

```bash
TOKEN=$(curl -s -X POST http://10.167.2.175:30081/api/v1/auth/login \
  -H 'Host: platform.local' -H 'Content-Type: application/json' \
  -H 'X-Tenant-Id: f4f85538-0a3a-4638-810e-3c9015cbee02' \
  -d '{"username":"admin","password":"Admin@12345"}' \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["data"]["accessToken"])')

# 后续所有业务请求头:
#   Authorization: Bearer $TOKEN
#   X-Tenant-Id: f4f85538-0a3a-4638-810e-3c9015cbee02
```

### 4.4 鉴权行为契约(已验证,2026-10-20 e2e)

| 场景 | 预期 | 实测 |
|---|---|---|
| 匿名访问 tenant 列表/注册 | 403(接口要求 SUPER_ADMIN,角色只能来自 JWT) | ✅ 403 |
| 伪造 `X-User-Roles` 头 | 仍 403(角色头不可注入绕过) | ✅ 403 |
| 错误口令登录 | 401 `{"success":false,"message":"用户名或密码错误"}` | ✅ 401 |
| 非法租户头(非 UUID) | 400/401 波动(已知缺陷 D-08,入口未校验 UUID 格式) | ✅ 记录 |

---

## 5. 业务 API 速查

> 前缀约定:`GW  = http://10.167.2.175:30081`(需 `-H 'Host: platform.local'`);
> `NP(svc) = http://10.167.2.175:<NodePort>`(NodePort 直连**无需 Host 头**)。
> 所有请求默认带头:`X-Tenant-Id: f4f85538-0a3a-4638-810e-3c9015cbee02`、`Content-Type: application/json`。
> ⚠️ **列表类接口(`/api/v1/courses`、`/api/v1/tenants` 等无子路径的 GET)经网关会命中 nginx 前缀 301(实测 2026-10-22,连续 6 次均 301)**,请**直连 NodePort** 调用;带子路径的接口(如 `/api/v1/kt/mastery`)经网关正常。此为网关接线缺陷(D-07 同类),记录在案。

### 5.1 健康检查(无需登录)

| 接口 | 方法 | 示例 | 预期 | 实测(2026-10-22) |
|---|---|---|---|---|
| 网关健康 | GET | `$GW/health` | 200 `healthy` | ✅ |
| 各服务 actuator | GET | `NP(iam-service)/api/v1/actuator/health`(tenant/course/teaching 同;ops 用 `/api/v1/ops/actuator/health`) | 200 `{"status":"UP",...}` | ✅ tenant/iam/course/teaching 均 UP;ops DOWN(D-01) |
| KT 服务 | GET | `http://10.167.2.175:31703/health` | 200 `{"status":"UP","service":"kt-service"}` | ✅ |

### 5.2 认证域(iam-service,31826 / 网关 `/api/v1/auth/*`)

| 接口 | 方法 | Body | 预期 | 实测 |
|---|---|---|---|---|
| 登录 | POST | `{"username":"admin","password":"Admin@12345"}` | 200 + `data.accessToken`(D-03 已修复) | ✅ 200 |
| 错误口令 | POST | 口令改错 | 401 `用户名或密码错误` | ✅ |

### 5.3 租户域(tenant-service,31965 / 网关 `/api/v1/tenants|users|credit-transfer/*`)

| 接口 | 方法 | 预期 | 实测 |
|---|---|---|---|
| 租户注册 | POST `/api/v1/tenants` | 匿名 403(@PreAuthorize SUPER_ADMIN) | ✅ |
| 租户列表 | GET `/api/v1/tenants?page=0&size=5` | 匿名 403;带 token 后 200(当前 SKIP,等 D-03) | ✅ 403 |
| 学分互认规则列表 | GET `/api/v1/credit-transfer/rules`(tenantId 必填) | 匿名 403 | ✅ |

### 5.4 课程域(course-service,31429 / 网关 `/api/v1/courses|knowledge|resources|recommendations/*`)

| 接口 | 方法 | Body 示例 | 预期 | 实测(2026-10-22) |
|---|---|---|---|---|
| 课程列表 | GET `NP(course)/api/v1/courses?page=0&size=5` | — | 200 `{"success":true,"data":{"content":[...]}}` | ✅ 200,`content:[]`(此前 D-04 500,TiDB 编码问题已恢复) |
| 创建课程 | POST `NP(course)/api/v1/courses` | `{"code":"E2E000001","name":"测试课程","description":"...","subject":"工业互联网","gradeLevel":"二年级","semester":"2026 秋"}` | 需鉴权:匿名 401/403;带 token 201(当前 D-03 阻塞) | ✅ 匿名 401/403 契约成立 |
| 课程详情 | GET `NP(course)/api/v1/courses/{id}` | — | 200 / 404 | ✅ |

### 5.5 教学域(teaching-service,32471 / 网关 `/api/v1/paths|assignments|analytics|kt|multimodal/*`)

| 接口 | 方法 | Body 示例 | 预期 | 实测(2026-10-22) |
|---|---|---|---|---|
| 创建学习路径 | POST `NP(teaching)/api/v1/learning-paths` | `{"tenantId":"<租户UUID>","courseId":"00000000-0000-0000-0000-0000000000aa","title":"demo-path","description":"..."}` | 201,返回 `id/status=DRAFT/version=0` | ✅ 201,id=`a4811186-...` |
| 加步骤 | POST `.../learning-paths/{id}/steps` | `{"stepType":"VIDEO","title":"step1","contentId":"00000000-0000-0000-0000-0000000000cc","contentType":"VIDEO","estimatedMinutes":10,"isRequired":true}` | 200/201;**新建后立即加步骤可能 500(已知 D-06,StaleObjectStateException)** | 复现于 10-20 |
| 并发发布(OCC) | POST `.../learning-paths/{id}/publish` ×2 并发 | — | 恰好 1 个 200,另 1 个 409/500 | 10-20 因 D-06 无法走到 |
| 作业/考试/讨论资源 | GET `NP(teaching)/api/v1/assignments` 等 | — | 200 / 404 | ✅ |
| **KT 掌握度** | GET `NP(teaching)/api/v1/kt/mastery?studentId=lan-demo-1` | — | 200:`overallMastery∈[0,1]` + `weakKps` 列表 | ✅ `overallMastery=0.3968,coldStart=true,weakKps=[{kp:29,mastery:0.025},...]` |
| **KT 预测** | POST `NP(teaching)/api/v1/kt/predict` | `{"studentId":"lan-demo-1","kps":[1,2,3],"difficulty":3}` | 200 `probability∈[0,1]` | ✅ `{"probability":0.4445,"recommendation":"建议先复习薄弱前置点"}` |
| **KT 模型信息** | GET `NP(teaching)/api/v1/kt/model/info` | — | 200,`models:["BKT(EM)","DKT(LSTM)"]` + eval 指标 | ✅ |
| 上述三个 KT 接口经网关 | 同上,`$GW/api/v1/kt/...` | — | 同 | ✅ 均通(带 Host 头) |

### 5.6 运维域(ops-service,30970)

| 接口 | 方法 | 预期 | 实测 |
|---|---|---|---|
| 总览 | GET `NP(ops)/api/v1/ops/overview` | 匿名 401 `缺少有效凭证` | ✅ |
| 带角色头访问 | 加 `X-User-Roles: ADMIN` | **仍 401** —— ops 缺角色头→Authentication 过滤器(已知 D-05),业务端点外部暂不可达 | 记录 |

### 5.7 未对外暴露(外部测试不可达,勿浪费时间)

- `graphql-gateway`(8097)、`ai-service`(8085)、`agent-service`(8090)、`realtime-gateway`(8095)、`ops-web`:均为 ClusterIP 且网关无路由,经网关访问返回 404 是**正确行为**。
- kt-service(31703)除 `/health` 外的路由均 404(FastAPI 默认,实测 `/docs`、`/openapi.json` 404)—— 算法调用统一走 teaching-service 的 `/api/v1/kt/*`。

---

## 6. 自动化测试运行指南

### 6.1 获取代码与依赖

```bash
# 测试同事请从仓库管理员处获取仓库地址后克隆
git clone <仓库地址> personalized-learning-platform-plus
cd personalized-learning-platform-plus

python3 -m venv .venv && source .venv/bin/activate
pip install -r tests/e2e/requirements.txt     # pytest>=8.0, requests>=2.31
```

### 6.2 运行全平台 e2e 套件

```bash
python3 -m pytest tests/e2e/test_full_platform.py -v
```

**预期输出**(以 2026-10-20 基线为准,55 用例):约 **42 PASS / 5 FAIL / 8 SKIP**。

- FAIL 均为**真实缺陷复现**(D-03 登录、D-04 课程列表、D-02 web-student 30090、D-05/D-06 等),不是环境问题;FAIL 的断言消息里直接写了缺陷编号。
- SKIP 均因 D-03 无 token(注释注明)。
- 环境变量可覆盖目标:`E2E_GW`、`E2E_GW_SSL`、`E2E_HOST`、`NP_*` 各服务端口、`E2E_TENANT`、`E2E_USER/E2E_PASS`。
- 套件内置规则:**能通就测,不通标注 SKIP/FAIL 并给根因,禁止伪造通过**。

### 6.3 基线报告

完整基线(含每条用例证据与缺陷根因)见 `report/2026-10-20-full-platform-e2e-report.md`。新同事跑完后对照该报告:若某 FAIL 变 PASS 或新增 FAIL,请在缺陷清单上更新状态。

---

## 7. 压测复现指南

> 压测目标统一打**网关或 NodePort**。注意网关 Host 头问题:压测工具默认 `Host=<目标IP>`,网关 175 上会 404 —— **压测入口用 `http://10.167.2.176:30081`**(176 IP 已在网关 `server_name` 中,实测经 176 访问 `/api/v1/kt/mastery` 返回 200),或本机 hosts 解析 `platform.local`。

### 7.1 K6(脚本在仓库 `tests/k6/`)

| 脚本 | 场景 | 命令 |
|---|---|---|
| `smoke.js` | 冒烟:1 VU × 30s 健康确认 | `BASE_URL=http://10.167.2.176:30081 k6 run tests/k6/smoke.js` |
| `stress_1000.js` | 1000 并发爬坡(100→500→1000→1000→0,共 22m);阈值:读 P95<800ms、错误率<0.5%、429<1% | `BASE_URL=http://10.167.2.176:30081 k6 run tests/k6/stress_1000.js` |
| `stress_100000.js` / `fullchain_100000.js` | 10 万级压力 | 同上,替换脚本名 |
| `scripts/k6_ab_undertow.js` | Tomcat↔Undertow AB 对比 | 见脚本内注释,NodePort 用 undertow 变体端口 |

说明:`stress_1000.js` 场景为 6 个模拟租户(`tenant-01`~`tenant-06`),90% 读(`/api/v1/courses`)+10% AI 异步提交(`/api/v1/generation/async`)。注意 `/api/v1/courses` 列表经网关存在 §5 所述 301 问题,K6 读路径阈值需把 301 计入或直连 31429 压测。

### 7.2 Locust(脚本在仓库 `locust/`)

```bash
pip install locust
cd locust

# 本机起 master + web UI(默认 http://localhost:8089)
locust -f locustfile_1000u.py --host http://10.167.2.176:30081
# 浏览器打开 http://localhost:8089 配置并发数/爬坡率,或 headless:
locust -f locustfile_1000u.py --headless -u 1000 -r 50 -t 30m --host http://10.167.2.176:30081
```

- 场景(来自 `locustfile_1000u.py`):6 校 × 各 167 并发 ≈ 1000 并发;链路:登录(iam)→ 课程/资源浏览(course)→ 学习路径(teaching)→ AI 异步提交 → 运维总览。
- 通过标准:业务读 P95 < 800ms、429 率 < 1%、错误率 < 0.5%。
- 集群内另部署有 `k6-loadgen`(ClusterIP 6565,集群内压测源),外部同事不必使用。
- 历史压测结论见 `PERFORMANCE_TEST_REPORT.md` / `docs/PERFORMANCE_LOAD_TEST_REPORT.md`。

---

## 8. 常见问题

| 现象 | 原因 | 处理 |
|---|---|---|
| **访问 `http://10.167.2.175:30081/api/v1/...` 全部 404**(nginx HTML 404 或 `{"error":"Not Found","message":"API endpoint not found"}`) | 网关按 `server_name` 匹配,IP 直访 175 落到 localhost 健康检查 server 块(已知 D-07) | ① 加 `-H 'Host: platform.local'`;② 或 `/etc/hosts` 加 `10.167.2.175 platform.local` 用域名访问;③ 或改走 `10.167.2.176:30081`(176 已在 server_name 内) |
| **`/api/v1/courses`、`/api/v1/tenants` 等 GET 返回 301** | 网关 location 定义为 `/api/v1/courses/`(带斜杠),无子路径的请求被 nginx 前缀重定向 | **直连 NodePort**(31429/31965)调用列表接口;或等网关修复 |
| **登录 403 空体**(`{"success":false,...}` 或空) | 已知 P0 缺陷 D-03:口令正确但角色查询 SQL(TiDB 不支持 `unnest`)崩溃,token 签发不出 | 确认口令无误后即为该缺陷;错误口令返回 401 可区分;等待修复,期间 token 类用例 SKIP |
| **401 `缺少有效凭证` / 403** | 未带 `Authorization: Bearer <token>` 或 token 过期;部分接口(如 tenant 管理)设计上要求 SUPER_ADMIN | 补 token;匿名 401/403 本身是正确契约(见 §5) |
| **CORS 报错(浏览器控制台)** | 网关已统一加 `Access-Control-Allow-Origin: *` 等头并处理 OPTIONS 预检(实测响应头存在);若仍报错,检查是否走了未带网关头的直连地址 | 前端请求统一走网关域名(§2.2 hosts 方案) |
| **NodePort 端口连不上(curl 000/timeout)** | 多为测试机网络/VPN 问题;175/176 防火墙当前全开(firewalld inactive,已实测) | 先 `curl http://10.167.2.175:30081/health` 分层定位;再试 176;仍不通联系平台管理员检查交换机/安全组 |
| **web-student 30090 打不开** | 历史端口 30090 从未在集群创建(D-02);现行有效端口是 **30096** | 用 `http://10.167.2.175:30096/` |
| **ops-service health 显示 DOWN** | 已知 D-01:ops 配置的 Redis 地址不可达,业务不影响其它服务测试 | 记录即可,等修复 |

---

## 9. 故障排查

### 9.1 外部探针(测试人员可直接用)

```bash
# 逐层健康探针(均实测过)
curl -s http://10.167.2.175:30081/health                 # 网关   → healthy
curl -s http://10.167.2.176:30096/ -o /dev/null -w '%{http_code}\n'   # 学生端 → 200
curl -s http://10.167.2.175:31703/health                 # KT     → {"status":"UP",...}
for p in 31965 31826 31429 32471; do \
  curl -s http://10.167.2.175:$p/api/v1/actuator/health | head -c 60; echo " <- $p"; done
curl -s http://10.167.2.175:30970/api/v1/ops/actuator/health | head -c 60   # ops(DOWN 属已知)
```

### 9.2 集群侧排查(需 ssh 到 10.167.2.175,联系平台管理员)

```bash
ssh 10.167.2.175

kubectl -n qianrenqianmian get pods -o wide          # 全部服务 Running 为正常
kubectl -n qianrenqianmian get svc -o wide           # 端口对照本文 §1.2
kubectl -n qianrenqianmian logs deploy/iam-service --tail=100     # 例:登录 403 看这里(unnest 报错= D-03)
kubectl -n qianrenqianmian logs deploy/nginx-gateway --tail=100   # 网关 access/error 日志
kubectl -n qianrenqianmian exec deploy/nginx-gateway -- nginx -T  # 网关 live 配置(路由/Host 匹配依据)
kubectl -n infra get svc | grep -E 'kt|vector'        # infra 域服务
```

### 9.3 监控入口

- Prometheus / Grafana 经网关 `/metrics`、`/grafana/` 有 **IP 白名单(10.167.2.0/24)**,局域网内测试机可直接访问;`/jaeger/`、`/locust/` 当前返回 503(未部署,网关占位)。

---

## 附:已知缺陷速查(2026-10-20 基线,详见 e2e 报告)

| 编号 | 级别 | 摘要 | 对测试的影响 |
|---|---|---|---|
| D-03 | P0 | IAM 登录 token 无法签发(TiDB 不支持 `unnest`) | 所有需 token 的写路径不可测(SKIP) |
| D-04 | P0 | course 列表 500(TiDB 二进制→utf8mb4) | **已于 2026-10-22 实测恢复 200**(content 为空) |
| D-02 | P1 | web-student 30090 未接线 | 改用实测有效的 **30096** |
| D-05/D-01 | P1/P2 | ops 角色头过滤器缺失 + Redis DOWN | ops 业务端点 401,health DOWN |
| D-06 | P2 | 新建学习路径立即加步骤 500 | OCC 并发发布链路无法执行 |
| D-07 | P2 | 网关 server_name 不含 175 IP;`/health` 未挂 443 | IP 直访 175 需带 Host 头(§8) |
| D-08 | P3 | 非法租户头 500/400 波动 | 输入需保证 UUID 格式 |

> 本指南由平台组维护;发现与实际不符的条目,请以你实测结果为准并反馈更新。

## 附录 A: 2026-10-22 修订(登录链路修复后)

登录链路修复后, 局域网测试须知:

1. **登录必须带 X-Tenant-Id 头**(经网关或 NodePort 均需): 
   `X-Tenant-Id: da98138f-486f-42db-944c-219a5a44bf52`(TEN0001 赵伟实验学校)
2. **鉴权业务 API 必须走网关 30081**: 服务仅信任网关注入的 `X-Gateway-Verified` 头, NodePort 直连业务路径返回 403(防伪造, 属安全设计)。仅 `POST /api/v1/auth/login` 与健康检查可直连。
3. **业务请求需带 `Authorization: Bearer <token>` + `X-Tenant-Id` 双头**; 列表端点(如 `/api/v1/users`, `/api/v1/roles`)现在无尾斜杠也可访问(网关已加精确匹配块)。
4. **web-student 端口为 30096**(非 30090)。
5. **压测限流提示**: 网关对单源 IP 限流 100 r/s(burst 200), 自动化压测会被 503 限流拒绝, 属预期; 压测走集群内 k6-loadgen(见 report/2026-10-23-loadtest-report.md)。
6. e2e 用例: `pytest tests/e2e/test_full_platform.py`(51 passed/4 skipped, 2026-10-22)。
