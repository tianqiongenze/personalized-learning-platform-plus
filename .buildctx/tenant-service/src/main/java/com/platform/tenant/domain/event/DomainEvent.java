package com.platform.tenant.domain.event;

import com.platform.tenant.domain.model.Tenant;
import com.platform.tenant.domain.model.valueobjects.TenantId;
import java.time.Instant;
import java.util.UUID;

/**
 * 领域事件基类
 */
public abstract class DomainEvent {
    private final UUID eventId;
    private final Instant occurredAt;
    private final TenantId tenantId;

    protected DomainEvent(TenantId tenantId) {
        this.eventId = UUID.randomUUID();
        this.occurredAt = Instant.now();
        this.tenantId = tenantId;
    }

    public UUID getEventId() { return eventId; }
    public Instant getOccurredAt() { return occurredAt; }
    public TenantId getTenantId() { return tenantId; }
}
