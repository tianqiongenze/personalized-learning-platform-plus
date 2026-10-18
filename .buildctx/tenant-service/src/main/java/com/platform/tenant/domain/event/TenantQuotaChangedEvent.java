package com.platform.tenant.domain.event;

import com.platform.tenant.domain.model.Tenant;
import com.platform.tenant.domain.model.valueobjects.TenantId;
import java.time.Instant;
import java.util.UUID;

/**
 * 领域事件基类
 */

/**
 * 租户恢复事件
 */

public final class TenantQuotaChangedEvent extends DomainEvent {
    private final String oldQuota;
    private final String newQuota;
    private final String changedBy;

    public TenantQuotaChangedEvent(TenantId tenantId, String oldQuota, String newQuota, String changedBy) {
        super(tenantId);
        this.oldQuota = oldQuota;
        this.newQuota = newQuota;
        this.changedBy = changedBy;
    }

    public String getOldQuota() { return oldQuota; }
    public String getNewQuota() { return newQuota; }
    public String getChangedBy() { return changedBy; }
}
