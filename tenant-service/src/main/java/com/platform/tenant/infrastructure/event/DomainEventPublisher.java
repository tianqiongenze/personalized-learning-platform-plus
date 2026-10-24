package com.platform.tenant.infrastructure.event;

import com.platform.tenant.domain.event.DomainEvent;
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
        String tenant = event.getTenantId() != null ? event.getTenantId().getValue().toString() : null;
        if (relay != null) {
            // EER: 同库原子落 outbox, relay 至少一次投递
            relay.save(EventTopics.DOMAIN_EVENTS, event.getClass().getSimpleName(),
                    tenant, String.valueOf(event.getEventId()), event);
        } else {
            pulsarPublisher.ifAvailable(p -> p.publish(
                    event.getClass().getSimpleName(), tenant, null, event));
        }
    }

    public void publishAll(List<DomainEvent> events) {
        events.forEach(this::publish);
    }

}
