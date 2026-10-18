package com.platform.common.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pulsar.client.api.PulsarClientException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.pulsar.core.PulsarTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * PulsarDomainEventPublisher 单测: 信封序列化/routingKey 语义/失败不抛(fire-and-forget 语义在
 * 走 Outbox 的服务里由 relay 兜底, 发布器本身保持不阻断业务事务)。
 */
class PulsarDomainEventPublisherTest {

    @SuppressWarnings("unchecked")
    private final PulsarTemplate<String> template = mock(PulsarTemplate.class);
    private final ObjectMapper om = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final MockEnvironment env = new MockEnvironment();
    private PulsarDomainEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new PulsarDomainEventPublisher(template, om, env);
    }

    @Test
    void publishSendsEnvelopeJsonWithRoutingKey() {
        publisher.publish("t.topic", "UserCreated", "tenant-1", "agg-9", java.util.Map.of("x", 1));
        verify(template).send(eq("t.topic"), contains("UserCreated"));
    }

    @Test
    void defaultTopicOverridableViaEnv() {
        env.setProperty("app.events.default-topic", "custom.topic");
        PulsarDomainEventPublisher p = new PulsarDomainEventPublisher(template, om, env);
        p.publish("OrderPlaced", null, "agg-1", java.util.Map.of());
        verify(template).send(eq("custom.topic"), contains("OrderPlaced"));
    }

    @Test
    void sendFailureDoesNotThrow() {
        doThrow(new RuntimeException(new PulsarClientException("broker down")))
                .when(template).send(anyString(), anyString());
        assertDoesNotThrow(() -> publisher.publish("t", "E", "tenant", "agg", java.util.Map.of()));
    }
}
