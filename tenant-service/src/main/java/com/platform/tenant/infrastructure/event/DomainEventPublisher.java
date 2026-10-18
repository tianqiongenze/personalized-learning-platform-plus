package com.platform.tenant.infrastructure.event;

import com.platform.tenant.domain.event.DomainEvent;
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

    public DomainEventPublisher(ApplicationEventPublisher applicationEventPublisher,
                                ObjectProvider<PulsarDomainEventPublisher> pulsarPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
        this.pulsarPublisher = pulsarPublisher;
    }

    public void publish(DomainEvent event) {
        applicationEventPublisher.publishEvent(event);
        pulsarPublisher.ifAvailable(p -> p.publish(
                event.getClass().getSimpleName(),
                event.getTenantId() != null ? event.getTenantId().getValue().toString() : null,
                null, event));
    }

    public void publishAll(List<DomainEvent> events) {
        events.forEach(this::publish);
    }

}
