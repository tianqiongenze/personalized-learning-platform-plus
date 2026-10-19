# ADR-003: Outbox 事件驱动(Pulsar)

**状态**: Accepted(已全量接线,`app.events.outbox=true`)

## 背景

跨服务业务事件(如课程发布 → 通知/统计/索引更新)若用 fire-and-forget(sendAsync + 队列满静默丢/失败仅 warn),会静默丢失事件导致下游状态永久缺失且无补偿。EER(事件可靠性铁律)要求 transactional outbox。参照 ESP SAD"事件驱动"章节与 microservices.io Transactional Outbox Pattern。

## 决策

`platform-common` `com.platform.common.event` 包(参照 ESP 模式落地):

- **Outbox 同库原子提交**: 业务事务内 业务写 + outbox 行 INSERT 同一 CRDB 事务提交(事务回滚则 outbox 行回滚,无幽灵事件)
- **Relay 投递**: `OutboxEventRelay` 独立扫表,将未发送 outbox 行投递到 Pulsar(`PulsarDomainEventPublisher`),有界批量 + 失败重试
- **幂等贯穿**: `message_id`(outbox 唯一索引 → Pulsar 消息头 → 消费端 `ON CONFLICT (message_id) DO NOTHING` 去重),at-least-once 投递 + 幂等消费 = 业务级 exactly-once
- **信封**: `DomainEventEnvelope`(事件类型/payload/occurredAt/trace 上下文),`EventTopics` 集中定义 topic 常量
- **开关**: `app.events.outbox=true` 全量接线;false 时退化为直发 Pulsar(仅本地开发)

## 后果

- 正面: 关键业务事件零丢失(同事务原子性);消费端幂等可重放;与 ESP 模式对齐降低跨项目认知成本。
- 负面/约束: outbox 表需要清理策略(按已发送+保留期归档,运维动作);relay 扫表有秒级投递延迟(非实时关键路径可接受);重试达上限需进死信(DLP 铁律,DLQ 监控告警为后续运维项)。

## 备选

1. **fire-and-forget 直发 Pulsar** —— 静默丢事件、无补偿,违反 EER 铁律,否决。
2. **CDC(Debezium)读 WAL 投递** —— 部署复杂度高(需 Kafka Connect 兼容层),单/双节点验证拓扑下过重,否决(多节点生产化时可重评估)。
3. **RabbitMQ/Kafka 替代 Pulsar** —— 事件量大且需分层存储/多租户 topic,Pulsar 架构更契合;ESP 已验证 Pulsar 客户端模式,否决。
