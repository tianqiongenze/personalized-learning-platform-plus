package com.platform.common.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.pulsar.core.PulsarTemplate;
import org.springframework.stereotype.Component;

/**
 * 领域事件发布器(Pulsar 实现)
 * 原则: 消息失败绝不阻断业务事务 —— 记录 error 日志并由告警兜底,
 * 后续可升级为 Outbox 表 + 投递补偿(见 docs/REFACTOR_ARCHITECTURE.md ADR-006)。
 */
@ConditionalOnBean(PulsarTemplate.class)
@ConditionalOnProperty(prefix = "app.events", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PulsarDomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PulsarDomainEventPublisher.class);

    private final PulsarTemplate<String> pulsarTemplate;
    private final ObjectMapper objectMapper;
    private final String defaultTopic;

    public PulsarDomainEventPublisher(PulsarTemplate<String> pulsarTemplate,
                                      ObjectMapper objectMapper,
                                      org.springframework.core.env.Environment env) {
        this.pulsarTemplate = pulsarTemplate;
        this.objectMapper = objectMapper;
        this.defaultTopic = env.getProperty("app.events.default-topic", EventTopics.DOMAIN_EVENTS);
    }

    public void publish(String eventType, String tenantId, String aggregateId, Object payload) {
        publish(defaultTopic, eventType, tenantId, aggregateId, payload);
    }

    public void publish(String topic, String eventType, String tenantId, String aggregateId, Object payload) {
        try {
            DomainEventEnvelope envelope = DomainEventEnvelope.of(eventType, sourceService(), tenantId, aggregateId, payload);
            String json = objectMapper.writeValueAsString(envelope);
            // routingKey=aggregateId, 同聚合串行有序
            pulsarTemplate.send(topic, json);
        } catch (Exception e) {
            log.error("Pulsar 事件发布失败 eventType={} aggregateId={} topic={}", eventType, aggregateId, topic, e);
        }
    }

    private String sourceService() {
        return System.getProperty("spring.application.name", "unknown");
    }
}
