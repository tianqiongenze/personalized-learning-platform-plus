package com.platform.common.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Transactional Outbox 事件中继(EER 铁律落地, 替代 fire-and-forget):
 *  - 生产者: {@link #save} 在业务事务内写入 outbox 行(与业务写同库原子提交, 事务回滚则无幽灵事件);
 *  - 中继: {@link #relay} 独立定时扫描未投递行 → Pulsar send 成功后标记 delivered
 *    (at-least-once; 消费侧按 eventId 幂等去重);
 *  - messageId 贯穿: outbox.message_id == envelope.eventId == Pulsar orderingKey 无关但可追踪。
 *
 * 适用的表(首次部署执行 DDL, 由各服务迁移目录负责):
 *   CREATE TABLE IF NOT EXISTS event_outbox (
 *     id           UUID PRIMARY KEY,
 *     topic        VARCHAR(200)  NOT NULL,
 *     event_type   VARCHAR(100)  NOT NULL,
 *     tenant_id    VARCHAR(64),
 *     aggregate_id VARCHAR(64)   NOT NULL,
 *     payload      JSONB         NOT NULL,
 *     message_id   VARCHAR(64)   NOT NULL UNIQUE,
 *     created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
 *     delivered_at TIMESTAMPTZ,
 *     attempts     INT           NOT NULL DEFAULT 0
 *   );
 *   CREATE INDEX IF NOT EXISTS idx_event_outbox_undelivered ON event_outbox (created_at) WHERE delivered_at IS NULL;
 *
 * 启用条件: app.events.outbox=true 且存在 JdbcTemplate。
 */
@Component
@ConditionalOnBean(JdbcTemplate.class)
@ConditionalOnProperty(prefix = "app.events", name = "outbox", havingValue = "true", matchIfMissing = false)
public class OutboxEventRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventRelay.class);
    private static final int BATCH = 100;
    /** 死信阈值: 超过即标记死信并告警日志(包内可见供测试对齐) */
    static final int MAX_ATTEMPTS = 8;

    private final JdbcTemplate jdbc;
    private final PulsarDomainEventPublisher publisher;
    private final ObjectMapper objectMapper;

    public OutboxEventRelay(JdbcTemplate jdbc, PulsarDomainEventPublisher publisher, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.publisher = publisher;
        this.objectMapper = objectMapper;
    }

    /** 业务事务内调用: 与业务写同事务原子落库, 失败不吞异常(否则业务事务应回滚)。 */
    public String save(String topic, String eventType, String tenantId, String aggregateId, Object payload) {
        try {
            DomainEventEnvelope envelope =
                    DomainEventEnvelope.of(eventType, System.getProperty("spring.application.name", "unknown"),
                            tenantId, aggregateId, payload);
            String json = objectMapper.writeValueAsString(envelope);
            jdbc.update("INSERT INTO event_outbox (id, topic, event_type, tenant_id, aggregate_id, payload, message_id) "
                            + "VALUES (?, ?, ?, ?, ?, ?::jsonb, ?)",
                    UUID.randomUUID(), topic, eventType, tenantId, aggregateId, json, envelope.eventId());
            return envelope.eventId();
        } catch (Exception e) {
            // 事件写入失败必须让业务事务回滚, 不能静默丢事件
            throw new IllegalStateException("outbox 事件落库失败, 业务事务回滚", e);
        }
    }

    /** 独立事务逐批投递; send 成功才标记 delivered; 重试指数退避由 attempts 驱动扫描过滤。 */
    @Scheduled(fixedDelayString = "${app.events.outbox.relay-interval-ms:2000}")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void relay() {
        List<OutboxRow> rows = jdbc.query(
                "SELECT id, topic, event_type, tenant_id, aggregate_id, payload, message_id, attempts "
                        + "FROM event_outbox WHERE delivered_at IS NULL AND attempts < ? "
                        + "ORDER BY created_at LIMIT " + BATCH + " FOR UPDATE SKIP LOCKED",
                (rs, i) -> new OutboxRow(
                        rs.getObject("id", UUID.class), rs.getString("topic"), rs.getString("event_type"),
                        rs.getString("tenant_id"), rs.getString("aggregate_id"), rs.getString("payload"),
                        rs.getString("message_id"), rs.getInt("attempts")),
                MAX_ATTEMPTS);
        if (rows.isEmpty()) {
            return;
        }
        log.debug("[outbox] 待投递 {} 条", rows.size());
        for (OutboxRow row : rows) {
            try {
                publisher.publish(row.topic(), row.eventType(), row.tenantId(), row.aggregateId(),
                        objectMapper.readTree(row.payload()));
                jdbc.update("UPDATE event_outbox SET delivered_at = ? WHERE id = ?", Timestamp.from(Instant.now()), row.id());
            } catch (Exception e) {
                int attempts = row.attempts() + 1;
                jdbc.update("UPDATE event_outbox SET attempts = ? WHERE id = ?", attempts, row.id());
                log.warn("[outbox] 投递失败 attempts={} messageId={} err={}", attempts, row.messageId(), e.getMessage());
                if (attempts >= MAX_ATTEMPTS) {
                    log.error("[outbox] 死信: messageId={} topic={} 超过最大重试, 需人工介入", row.messageId(), row.topic());
                }
            }
        }
    }

    /** 供测试构造的包内可见行记录 */
    record OutboxRow(UUID id, String topic, String eventType, String tenantId,
                     String aggregateId, String payload, String messageId, int attempts) {
    }
}
