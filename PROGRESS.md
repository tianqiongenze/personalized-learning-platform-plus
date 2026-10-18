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

## 进行中 / 待办

- **P2-A** web-student 5 页占位补全(Courses/LearningPath/Resources/Profile/Review), GraphQL 契约已核实(courses/learningPaths/assignments/reviewPlan/masteryProfile/recommendations)。
- **P2-B** KT 评测闭环: BKT 主线 + 离线 AUC 评测(train_all.py 修 UnboundLocalError + 防泄漏评测口径)。
- **P2-C** 学分互认规则引擎。
- 遗留(低优先): k8s ConfigMap 明文占位口令(JWT_SECRET/GRAFANA_PASSWORD/MINIO)→ external-secrets.yaml Vault 方案待评估; outbox 生产者侧业务事务内 save() 调用点补齐; relay 已投递行清理/死信 DLQ。
