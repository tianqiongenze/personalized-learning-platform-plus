package com.platform.tenant.domain.event;

import com.platform.tenant.domain.model.Tenant;
import com.platform.tenant.domain.model.valueobjects.TenantId;
import java.time.Instant;
import java.util.UUID;

/**
 * 领域事件基类
 */

/**
 * 租户配额变更事件
 */

public final class TenantUpdatedEvent extends DomainEvent {
    private final String changedFields;
    private final String changedBy;

    public TenantUpdatedEvent(TenantId tenantId, String changedFields, String changedBy) {
        super(tenantId);
        this.changedFields = changedFields;
        this.changedBy = changedBy;
    }

    public String getChangedFields() { return changedFields; }
    public String getChangedBy() { return changedBy; }
}
