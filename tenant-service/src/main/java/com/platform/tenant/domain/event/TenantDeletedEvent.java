package com.platform.tenant.domain.event;

import com.platform.tenant.domain.model.Tenant;
import com.platform.tenant.domain.model.valueobjects.TenantId;
import java.time.Instant;
import java.util.UUID;

/**
 * 领域事件基类
 */

/**
 * 租户更新事件
 */

public final class TenantDeletedEvent extends DomainEvent {
    private final String deletedBy;
    private final String reason;

    public TenantDeletedEvent(TenantId tenantId, String deletedBy, String reason) {
        super(tenantId);
        this.deletedBy = deletedBy;
        this.reason = reason;
    }

    public String getDeletedBy() { return deletedBy; }
    public String getReason() { return reason; }
}
