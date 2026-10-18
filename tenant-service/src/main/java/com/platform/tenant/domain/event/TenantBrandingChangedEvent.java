package com.platform.tenant.domain.event;

import com.platform.tenant.domain.model.Tenant;
import com.platform.tenant.domain.model.valueobjects.TenantId;
import java.time.Instant;
import java.util.UUID;

/**
 * 领域事件基类
 */

/**
 * 租户创建事件
 */

public final class TenantBrandingChangedEvent extends DomainEvent {
    private final String changedBy;

    public TenantBrandingChangedEvent(TenantId tenantId, String changedBy) {
        super(tenantId);
        this.changedBy = changedBy;
    }

    public String getChangedBy() { return changedBy; }
}
