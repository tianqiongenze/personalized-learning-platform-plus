package com.platform.common.event;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * 跨服务领域事件信封(经 Pulsar 传输的标准格式)
 * 服务内部领域对象 -> envelope(JSON) -> persistent://public/default/{topic}
 */
public record DomainEventEnvelope(
        String eventId,
        String eventType,
        String sourceService,
        String tenantId,
        String aggregateId,
        Instant occurredAt,
        Object payload
) implements Serializable {

    public static DomainEventEnvelope of(String eventType, String sourceService, String tenantId, String aggregateId, Object payload) {
        return new DomainEventEnvelope(UUID.randomUUID().toString(), eventType, sourceService, tenantId, aggregateId, Instant.now(), payload);
    }
}
