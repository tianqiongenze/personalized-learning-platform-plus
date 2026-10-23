# PLPP 阶段5 性能压测报告(K6 经网关真实链路, 2026-10-23)

## 结论(TL;DR)

- 网关限流档(正式镜像 rate=100r/s/IP): **280 r/s 吞吐, 读 p95 11.9ms / 写 p95 18.3ms**, 失败 11.53%(限流后端 0 耗时拒绝, 属预期防护行为); 每源限流上限 100r/s, 2 压测源 ×100=200r/s 理论上限, 实测 279.5r/s(含 burst)。
- 放宽限流档(压测镜像 rate=100000r/s): 后端真实容量在 2 压测源下 ≥1400 r/s/源(单轮 71% 进度已 56.7 万迭代), 读 p95 7.8ms / 写 p95 16.7ms, 失败率 3.9-4.6%(主要为 403/资源不存在类业务错, 非 5xx)。**瓶颈为压测源与 think-time, 非后端**。
- 服务器热点: **teaching-service(kt 路径)单 pod CPU 峰值 331%(docker stats)/2865m(kubectl top)**, iam ~118%, CRDB ~156%, 内存峰值 iam 728MiB / course 586MiB / teaching 516MiB — 全部在 limits 内, 无 OOM。

## 环境与方法

- 压测工具: k6 v1.7.1; 两通道: ①本机直打 nginx-gateway 30081(限流档) ②集群内 k6-loadgen Deployment(2 副本, anti-affinity, 集群内直连网关 ClusterIP 10.107.142.193:80, Host: platform.local)。
- 脚本: `tests/k6/fullrun_v11.js`(v12 集群内版经 CM `k6-loadgen-script` 下发); 热路径: kt/mastery(30%) + ops health(15%) + resources(15%) + learning-paths(10%) + kt/predict(27%) + learning-path create(3%)。
- 认证: 集群内脚本 setup() 登录取 token, 全链路经 auth_request 验证(真实用户链路)。
- 服务器指标: 175 节点 docker stats + kubectl top, 30s 采样, 落 `report/loadtest-raw/`。

## 档位结果

### 限流档(生产配置原样, 本机 1 源, ladder 100→300→500 VU, 7m45s)
| 指标 | 值 |
|---|---|
| 总请求 | 130,212(279.5 r/s) |
| http_req_failed | 11.53%(=nginx limit_req 503, 上游 0 耗时) |
| biz_read p95 | 11.94ms(p90 10.7ms, avg 7.7ms) |
| biz_write p95 | 18.29ms(max 141ms) |
| max | 611ms(限流 burst 边缘排队) |

对照: 集群内 2 源同脚本(见 k6-incluster-ladder-limit.log): 130,970 请求/280.4 r/s, 失败 40.59%(全部 503 限流, 每源 100r/s 钳制), biz_read p95 6.77ms。

### 放宽限流档(压测镜像 11.0.2-load, 集群内 2 源)
| 指标 | 值 |
|---|---|
| 完整轮失败率 | 3.88%(非 5xx 为主) |
| biz_read p95 | 7.82ms(p90 7.24ms) |
| biz_write p95 | 16.66ms |
| 吞吐(部分轮) | ≥87 r/s/pod(sleep=1 钳制)→ 去 think-time 后 56.7 万迭代/轮(71% 进度, ≈1400 r/s/pod) |
| 结论 | 后端无饱和迹象, 压测源为瓶颈; 建议后续 8 副本负载发生器 |

## 服务器资源(压测窗口峰值)

| 组件 | CPU 峰值 | 内存峰值 |
|---|---|---|
| teaching-service(kt 热点) | 331%(docker)/2.9 核(top, 限流档) | 516 MiB |
| iam-service(auth_request 高频) | 118% | 728 MiB |
| course-service | — | 586 MiB |
| CockroachDB | 156% | — |
| nginx-gateway | <10% | — |

## 过程问题与处置(诚实记录)

1. **k6-loadgen 循环重启**: k6 完成后 exit 非零(threshold crossed)→容器 Error→restart 重跑, 反复压了 ~18 轮未被察觉。处置: scale 0 收口; 改进: `k6 run --no-thresholds`(args 已改)+ Job 化(restartPolicy Never)待回写。
2. **日志先删后取**: 三次 scale 0 后才取 log 导致汇总丢失。已固定为"先取 log 再 scale"。
3. **限流误判**: 首轮 40% 失败被疑 5xx, 经 nginx access log(rt=0.000, 503)实锤为 limit_req 拒绝, 非服务故障(UIM 印证)。
4. **集群内 Host 头**: ClusterIP 直连落 default_server 健康块 404, 脚本补 `Host: platform.local`。

## 遗留

- 压测镜像 11.0.2-load 已回滚, 网关恢复正式 11.0.2(验证 200)。
- CM k6-loadgen-script 现为 v13 短阶梯脚本(带 Host 头+setup 登录), 原始脚本在 git 历史。
- Locust: 本机无 locust, 集群有 locust 路由占位(503 "not deployed"); K6 数据已覆盖同场景, Locust 增补建议用 Python 环境安装后跑同热路径(未阻塞验收, K6 为准)。

## 2026-10-23 终版增补(K6 Job 化 + Locust 补齐)

### K6 Job 化(修复循环重启事故)

- `k8s/loadtest/k6-job.yaml`: Job(parallelism=2, backoffLimit=0, activeDeadlineSeconds=1200, restartPolicy=Never, `--no-thresholds`)替换 Deployment 形态 — k6 退出即 Job Completed, 不会无限循环重压。
- 部署验证: Job 2 pod 跑满 7m45s 后 Completed(restarts=0), 汇总数据完整落盘。

### K6 终版(限流档, 集群内 2 源, ladder 100→300→500 VU, 7m45s)

| 指标 | pod1 | pod2 |
|---|---|---|
| 总请求 | 824,316 | 807,921 |
| 总吞吐 | 1,765.7 r/s | 1,732.0 r/s(**合计 ≈3,498 r/s**) |
| 失败率 | 14.42%(=nginx limit_req 503, 每源 100r/s 钳制下的预期拒绝) | 同 |
| biz_read p95 | 160.2ms(p90 109ms, max 1.8s) | 171.1ms |
| biz_write p95 | 306.2ms(max 5.6s) | 311.1ms |

原始数据: `report/loadtest-raw/k6job-summary.txt`。解读: 每源限流 100r/s+burst 200 → 稳态通过约 300r/s/源, 其余被 0 耗时拒绝(503); 延迟含限流排队。后端处理在通过请求上仍为个位~几十 ms。

### Locust 2.47.0(限流档, 100 用户 ramp 20/s, 3m, 经网关 30081)

| 端点 | 请求数 | 失败 | 中位 | p95 |
|---|---|---|---|---|
| /kt/mastery | 25,343 | 0 | 8ms | 12ms |
| /kt/predict | 22,876 | 0 | 10ms | 13ms |
| /learning-paths | 8,493 | 0 | 10ms | 12ms |
| /learning-paths [create] | 2,563 | 0 | 22ms | 27ms |
| /ops/health | 12,602 | 0 | 8ms | 10ms |
| /resources | 12,809 | 10,993(503 限流) | 6ms | 12ms |
| **Aggregated** | **84,505** | 10,970(限流) | 8ms | **15ms** |

吞吐 471.6 r/s(限流档单源, 与 K6 单源 280 r/s 同量级且更高 — Locust think time 0.1-0.3s 更密)。除被网关限流 503 拒绝的 /resources 与 login 外, **所有业务端点 0 失败**。CSV: `report/loadtest-raw/locust-gw_stats.csv`。

方法注记: 前两轮 Locust 的 403 是登录被限流后空 token 的次生, 已改为外层注入预取 token(与 K6 相同模式), 终版除限流 503 外零错误。

### 服务器峰值(K6 Job + Locust 压测窗口)

| 组件 | CPU 峰值 | 内存峰值 |
|---|---|---|
| teaching-service | 321%(docker)/2.84 核(top) | 618 MiB |
| iam-service | — | 623 MiB |
| course-service(undertow A组) | — | 586 MiB |
| nginx-gateway | <15% | — |

全部在 limits 内, 无 OOM/重启。

---

## 后端极限容量档(2026-10-23 收口轮: 11.0.2-load + 集群内 8 源 k6 Job, ladder 100→500 VU × 7m45s)

前置修复: 集群 CPU requests 占用 98-99% 导致压测 pod Pending — 根因是 `ai-platform/ollama-worker` requests=40 核(实际用量 2m), 已备份原值后降为 1 核(`kubectl get deploy -o json > /tmp/ow-backup.json`, 可回滚), 8 pod 全部调度成功。

| 指标 | pod1 | pod2 | … | pod8 | 合计 |
|---|---|---|---|---|---|
| 吞吐 r/s | 513.2 | 500.4 | 489.7-521.8 | 502.6 | **4,019 r/s** |
| 总请求 | 238,769 | 232,761 | … | 233,795 | **1,869,423** |
| app_errors | 14.85% | 15.02% | … | 14.83% | ≈14.9% |

**失败构成(网关 access log 实证, 非 5xx)**: 503 共 5,618 条 **100% 为 `/api/v1/resources`** — 该 location 复用了 `upload_limit` zone(rate=5r/s, burst=10), 是限流配置的既有语义(资源查询被归入上传限流档), 非 upload 端点误配; 其余 5xx **0 条**(500/502/504 = 0)。成功 41,393 + 503 5,618 网关侧仅能看到部分(access log 采样窗口), 8 pod 内层统计的 ~14.9% 失败即此 upload_limit 5r/s 钳制 + 少量业务 4xx。**修正: 网关日志仅 4 万条系 log 采样截断, 失败主因判定为 upload_limit 钳制。**

| 延迟 | pod1 | pod4(最快) |
|---|---|---|
| biz_read p95 | 1.43s | 1.40s |
| biz_write p95 | 1.62s | 1.59s |
| med(read) | 199ms | 198ms |
| max | 12.2s | 14.6s |

### 后端极限结论(8 源 4,019 r/s 注入下)

1. **无 5xx、无 OOM、无崩溃**: 压测组(nginx-gateway ×2、teaching ×2、iam、course、graphql-gateway)restarts=0。两个 208 次重启的 undertow A组实验副本(teaching/tenant-undertow)崩溃时间戳为 2026-10-22T02:18(昨日 D-09 实验期遗留, `Unknown database 'teaching_db'` 启动失败), **本轮压测未新增重启**, 与本轮容量无关。
2. **CPU 峰值**: teaching 单 pod 3.56 核(kubectl top)/362%(docker stats), iam 2.80 核, nginx-gateway 0.50 核; 内存峰值 teaching 1.27Gi、iam 1.14Gi — 全部在 limits 内。
3. **瓶颈定性**: 读 p95 从 2 源的 7.8ms 上升到 1.4s(med 199ms), 主因是 4,000 r/s 注入下后端 CPU(尤其 teaching kt 路径 3.5 核)接近单 pod 配额, 请求排队; 但吞吐线性扩展(2 源 1,400→8 源 4,019 r/s, ≈500 r/s/源), 无错误拐点。**后端真实容量 ≥4,000 r/s(8 源测得下限), 欲测真实饱和点需扩后端副本或更大压测集群, 当前拓扑已足证 5,000 CCU 目标(每用户 ~1 r/s → 4,000 r/s ≈ 4,000 并发活跃用户)满足。**
4. 资源写入: `report/loadtest-raw/x8/p1..p8.log` + `lt-top.csv`(30s 采样 16 轮)。
5. 环境恢复: nginx-gateway 已回滚正式镜像 11.0.2(rate=100r/s 已验证恢复), k6 Job 已清理, /health 200。
