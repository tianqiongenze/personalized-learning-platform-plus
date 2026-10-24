package com.platform.iam.infrastructure.event;

import com.platform.iam.domain.event.DomainEvent;
import com.platform.common.event.EventTopics;
import com.platform.common.event.OutboxEventRelay;
import com.platform.common.event.PulsarDomainEventPublisher;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 领域事件发布器: 进程内事件(同步) + Pulsar 事件总线(跨服务, 失败不阻断事务)
 */
@Component
public class DomainEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;
    private final ObjectProvider<PulsarDomainEventPublisher> pulsarPublisher;
    private final ObjectProvider<OutboxEventRelay> outboxRelay;

    public DomainEventPublisher(ApplicationEventPublisher applicationEventPublisher,
                                ObjectProvider<PulsarDomainEventPublisher> pulsarPublisher,
                                ObjectProvider<OutboxEventRelay> outboxRelay) {
        this.applicationEventPublisher = applicationEventPublisher;
        this.pulsarPublisher = pulsarPublisher;
        this.outboxRelay = outboxRelay;
    }

    public void publish(DomainEvent event) {
        applicationEventPublisher.publishEvent(event);
        OutboxEventRelay relay = outboxRelay.getIfAvailable();
        if (relay != null) {
            // EER: 事件与业务同库原子落 outbox, relay 至少一次投递(事件不丢)
            relay.save(EventTopics.DOMAIN_EVENTS, event.getEventType(),
                    extractTenantId(event), String.valueOf(event.getEventId()), event);
        } else {
            pulsarPublisher.ifAvailable(p -> p.publish(
                    event.getEventType(), extractTenantId(event), null, event));
        }
    }

    public void publishAll(List<DomainEvent> events) {
        events.forEach(this::publish);
    }

    private String extractTenantId(DomainEvent event) {
        try {
            var field = event.getClass().getDeclaredField("tenantId");
            field.setAccessible(true);
            Object v = field.get(event);
            return v != null ? v.toString() : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
