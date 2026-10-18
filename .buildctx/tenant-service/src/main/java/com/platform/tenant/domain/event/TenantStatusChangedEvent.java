package com.platform.tenant.domain.event;

import com.platform.tenant.domain.model.Tenant;
import com.platform.tenant.domain.model.valueobjects.TenantId;
import java.time.Instant;
import java.util.UUID;

/**
 * 领域事件基类
 */

public final class TenantStatusChangedEvent extends DomainEvent {
    private final String fromStatus;
    private final String toStatus;
    private final String reason;
    private final String changedBy;

    public TenantStatusChangedEvent(TenantId tenantId, String fromStatus, String toStatus, 
                                    String reason, String changedBy) {
        super(tenantId);
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.reason = reason;
        this.changedBy = changedBy;
    }

    public String getFromStatus() { return fromStatus; }
    public String getToStatus() { return toStatus; }
    public String getReason() { return reason; }
    public String getChangedBy() { return changedBy; }
}
