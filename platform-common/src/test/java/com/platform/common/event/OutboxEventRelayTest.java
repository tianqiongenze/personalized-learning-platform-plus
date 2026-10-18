package com.platform.common.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OutboxEventRelay 单测: save 原子落库(失败回滚)/relay 批量投递/失败计数/死信边界。
 * JdbcTemplate/Pulsar 发布器 mock 隔离; 追踪真实执行路径。
 */
class OutboxEventRelayTest {

    private JdbcTemplate jdbc;
    private PulsarDomainEventPublisher publisher;
    private OutboxEventRelay relay;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        publisher = mock(PulsarDomainEventPublisher.class);
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        om.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        relay = new OutboxEventRelay(jdbc, publisher, om);
    }

    @Test
    void saveInsertsOutboxRowAndReturnsMessageId() {
        // save 传参: id(UUID), topic, eventType, tenantId, aggregateId, json, messageId —— 7 个
        when(jdbc.update(contains("INSERT INTO event_outbox"),
                any(UUID.class), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(1);
        String messageId = relay.save("t.topic", "UserCreated", "tenant-1", "agg-1", java.util.Map.of("k", "v"));
        assertNotNull(messageId);
        verify(jdbc, times(1)).update(contains("INSERT INTO event_outbox"),
                any(UUID.class), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void saveFailureThrowsToRollbackBusinessTx() {
        when(jdbc.update(contains("INSERT INTO event_outbox"),
                any(UUID.class), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("db down"));
        assertThrows(IllegalStateException.class,
                () -> relay.save("t", "E", "tenant", "agg", java.util.Map.of()));
    }

    @Test
    void relayPublishesAndMarksDelivered() {
        UUID id = UUID.randomUUID();
        OutboxEventRelay.OutboxRow row = new OutboxEventRelay.OutboxRow(
                id, "t.topic", "UserCreated", "tenant-1", "agg-1",
                "{\"eventType\":\"UserCreated\"}", "msg-1", 0);
        when(jdbc.query(anyString(), any(RowMapper.class), eq(OutboxEventRelay.MAX_ATTEMPTS)))
                .thenReturn(List.of(row));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        relay.relay();
        verify(publisher, times(1)).publish(eq("t.topic"), eq("UserCreated"), eq("tenant-1"), eq("agg-1"), any());
        verify(jdbc, times(1)).update(contains("delivered_at"), any(Object[].class));
    }

    @Test
    void relayFailureIncrementsAttemptsAndNoDelivered() {
        UUID id = UUID.randomUUID();
        OutboxEventRelay.OutboxRow row = new OutboxEventRelay.OutboxRow(
                id, "t.topic", "UserCreated", null, "agg-1", "{}", "msg-2", 0);
        when(jdbc.query(anyString(), any(RowMapper.class), eq(OutboxEventRelay.MAX_ATTEMPTS)))
                .thenReturn(List.of(row));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        org.mockito.Mockito.doThrow(new RuntimeException("pulsar down"))
                .when(publisher).publish(any(), any(), org.mockito.ArgumentMatchers.isNull(), any(), any());
        relay.relay();
        verify(jdbc, times(1)).update(contains("attempts"), any(Object[].class));
        verify(jdbc, never()).update(contains("delivered_at"), any(Object[].class));
    }

    @Test
    void relayEmptyBatchIsNoop() {
        when(jdbc.query(anyString(), any(RowMapper.class), eq(OutboxEventRelay.MAX_ATTEMPTS)))
                .thenReturn(List.of());
        relay.relay();
        verify(publisher, never()).publish(any(), any(), any(), any(), any());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
}
