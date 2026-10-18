package com.platform.tenant.domain.event;

import com.platform.tenant.domain.model.Tenant;
import com.platform.tenant.domain.model.valueobjects.TenantId;
import java.time.Instant;
import java.util.UUID;

/**
 * 领域事件基类
 */

/**
 * 租户品牌定制变更事件
 */

public final class TenantRestoredEvent extends DomainEvent {
    private final String restoredBy;

    public TenantRestoredEvent(TenantId tenantId, String restoredBy) {
        super(tenantId);
        this.restoredBy = restoredBy;
    }

    public String getRestoredBy() { return restoredBy; }
}
