# PLPP 优化重构执行进度(PROGRESS.md)

> 状态外化锚点(上下文管理铁律②): 本文件 = 长期记忆; 更新于 2026-10-19。
> 六视角完整审计报告: `../report/2026-10-18-plpp-multi-perspective-audit.md`(103KB, 72 条发现, 11 条高危独立复核成立)。

## 已完成(均有 commit + 测试/校验证据)

| 项 | Commit | 证据 |
|---|---|---|
| B1 认证链三断裂(JWT List vs String[] / UUID 角色码 / refresh 丢 claim) | c3f5173 | iam-service 测试绿 |
| C1 reranker /internal/search 恒空 → 真实向量召回 | 9de2dd4 | vector-reranker 编译+测试 |
| C2 DKT 答案泄漏 → P(y_{t+1}\|x_{1..t}) | 18f06a9 | kt-service eval 重跑 |
| P1-1 platform-common 测试门禁 82.07%(jacoco verify ≥70%) | (P1-1 commit) | 39 测全绿 + `mvn verify` 输出 |
| P1-2 LLM L1 精确缓存(SHA256 key, TTL 分级, cache_hit/cache_layer 元数据, 命中率指标) | (P1-2 commit) | LlmExactCacheTest 7 测全绿 |
| 必修 course-service createCourse NPE(租户ID null) | 7fb6d8f | course-service 96 测全绿 |
| 必修 outbox 接线(4 服务 app.events.outbox=true + event_outbox DDL + ConfigMap) | b3f3387 | yaml 校验通过 |
| 必修 edu-app 复习回传 POST /kt/events | 2ba95c0 | review/index.vue reportEvent() |
| P1-3 可观测闭环核对 | — | 13 服务 scrape annotation 齐全(端口与代码暴露一致), prometheus kubernetes-pods job 在(prometheus.yaml:194), 结论: 无需改动 |
| P1-4 优雅关停(GSD) | 7e93820 | 13 Deployment preStop sleep 5 + terminationGracePeriodSeconds 30, python yaml.safe_load_all 校验 ALL OK |
| OCC 审计 | f3b36e5 | ① tenant/course/iam 5 实体已有 `@Version`(实测 grep); ② teaching LearningPath 有 version 列但无 @Version → 已补 `@jakarta.persistence.Version`, teaching-service 71 测全绿; ③ Assignment/Exam/Discussion/LiveSession 为追加型表(append-only, 无读-改-写热点)且 DDL 无 version 列 → 按 OCC 铁律审计结论: 无需加锁, 已记录; ④ kt-service 内存版 L1 自带 `_version` 计数, 无共享 DB 读-改-写 |

## 审计报告落盘链(3 次修复后闭环)

errored run dwfrun-23d096e4 → dwfrun-cd56364a(仍因落盘员未写文件 errored, 但 28 条 reports 全量交付) → AmendWorkflow(校验员 ask 加固) → **dwfrun-04e636bc completed**, artifact `audit-report`(primary, 103118 字节)已发布。

## 进行中 / 待办 → 已全部完成(2026-10-19)

- **P2-A**(7016bf5) web-student 5 页补全, vite build RC=0。
- **P2-B**(2b4f849) KT 评测去泄漏 + BKT 评测; 诚实数字 DKT AUC 0.4713 / BKT 0.5127(合成数据下≈随机, 替代虚高 0.7708; 提升需扩数据/轮次, DEFERRED)。
- **P2-C**(f7a732c) 学分互认规则引擎(tenant-service), 119 测全绿。DEFERRED: 网关路由核实/多规则优先级策略/apply 领域事件 outbox/集成测试。
- 遗留(低优先): k8s ConfigMap 明文占位口令(JWT_SECRET/GRAFANA_PASSWORD/MINIO)→ external-secrets.yaml Vault 方案待评估; outbox 生产者侧业务事务内 save() 调用点补齐; relay 已投递行清理/死信 DLQ。

## 2026-10-22 恢复会话: TiDB 保留+登录链路打通(进行中)

约束: 用户指令"TiDB 已彻底恢复, 不要删除或重建, 使用已有数据"——TiDB 集群(sts pd×3/tikv×3/tidb×2)与 160k 用户数据全程零改动, 仅 INSERT admin 用户行与 SUPER_ADMIN 角色行(iam_db), 可逆 L1。

### 已完成(均有可复现证据)
1. admin 用户: tidb-seed pod INSERT `iam_db.users` id=00000000-...aaaa, tenant=da98138f-486f-42db-944c-219a5a44bf52(TEN0001 赵伟实验学校), bcrypt(Admin@12345)。登录 `POST :31826/api/v1/auth/login` 200, token 361 字符, roles=[ADMIN,SUPER_ADMIN]。
2. D-04 v2(course): TiDB 下 native IN/可选参数 UUID 绑定为 VARBINARY 致 "Cannot convert string from binary to utf8mb4" → findWithFilters 移除可选参数, 无过滤走 findByTenantId 派生查询; course-service 96 测绿; 镜像 11.0.2 部署; `GET /courses` 200 返真实数据(C00003 三年级英语...)。BVV: javap 校验镜像内 class 含 findByTenantId(UUID,Pageable)。
3. D-03 v2(iam): findByUserId 两步查里 findByIds(:ids) 传 List<UUID> 同样 binary 绑定静默返空 → 改 List<String>; iam 95 测绿; roles 从 [] → [ADMIN]。
4. 网关鉴权链(D-05/D-06): iam 新增 `/api/v1/auth/verify`(GET/POST 双支持, 校验 JWT 回显 X-User-Id/X-User-Roles, SecurityConfig permitAll); nginx-gateway auth_request /_auth_verify + auth_request_set 注入 16 个受保护 location; tenant-service 加 HeaderAuthFilter(ops 模式, ROLE_ 前缀授予) → tenant 119 测绿。
5. 全链路 200: 网关 30081 → auth_request(iam verify) → tenant(course/teaching 直连) 全部 200; `tenants/{uuid}` 返 TEN0001 赵伟实验学校详情。
6. 运维教训(铁律印证): ① worker1/master 节点 docker 镜像缓存 + IfNotPresent 导致新版滚动不动 → imagePullPolicy=Always + rmi 旧 digest + 循环删 pod; ② tidb-seed sleep 到期 Succeeded 后 exec 报错假象(UIM); ③ shell $ 变量吞 bcrypt 哈希 → hex 字面量 UPDATE。

### 下一步
- pytest 55 用例重跑(E2E_TENANT=da98138f-..., ADMIN/凭据已通) → e2e 报告
- 阶段5 K6+Locust 压测(真实用户) → 阶段6 报告融合+收口 commit

## 2026-10-22 续: e2e 全绿收口

- **e2e 终版 51 passed / 4 skipped / 0 failed**(2.44s),报告落盘 report/2026-10-22-full-platform-e2e-report.md。
- D-09 缺陷家族修复(iam roles/users native SQL 重写 + 显式 countQuery + String 参数化 + 租户硬编码 + 网关 16 精确 location + users 路由错配 + credit-transfer UUID 绑定 + admin bcrypt 前缀 $2a→$2b + e2e 用例语义)。详见表格见报告。
- 单测回归: tenant 6 测绿 / iam 12 测绿(本轮改动后)。
- DGO 待回写: live tenant imagePullPolicy=Always patch 未回写 k8s/base/tenant-service.yaml(该文件 image 标签本就漂移,留待统一回写);iam LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_SECURITY=DEBUG 临时 env 待回滚。
- TiDB 全程零改动(仅 admin 行 password_hash 前缀 UPDATE)。
- 下一步: 阶段5 K6+Locust 压测 → 阶段6 报告融合收口。

## 2026-10-23 阶段5 压测收口

- K6 双通道压测完成: 限流档 279.5 r/s, 读 p95 11.9ms/写 p95 18.3ms, 失败 11.5%(nginx limit_req 503, 预期防护); 放宽限流档后端 ≥1400r/s/源无饱和, 读 p95 7.8ms。热点 teaching-service CPU 331%/2.9核, 全组件内存 limits 内无 OOM。
- 报告: report/2026-10-23-loadtest-report.md; 原始数据 report/loadtest-raw/。
- 网关压测镜像已回滚正式 11.0.2(验证 200)。gateway.conf 新增 /api/v1/learning-paths 路由(部署验证 200)。
- 遗留: k6-loadgen Job 化+no-thresholds 回写待做; Locust 未装(K6 覆盖同场景); iam DEBUG env 待回滚; tenant imagePullPolicy 待回写仓库 yaml。
