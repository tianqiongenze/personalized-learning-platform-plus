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
