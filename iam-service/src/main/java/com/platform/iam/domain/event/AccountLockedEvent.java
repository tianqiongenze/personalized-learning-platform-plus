package com.platform.iam.domain.event;

import com.platform.iam.domain.model.valueobjects.*;
import java.time.Instant;
import java.util.UUID;

/**
 * 领域事件基类
 */
public final class AccountLockedEvent extends DomainEvent {
    private final UserId userId;
    private final TenantId tenantId;
    private final String lockedBy;
    private final String reason;
    private final int failedAttempts;

    public AccountLockedEvent(UserId userId, TenantId tenantId, String lockedBy, String reason, int failedAttempts) {
        super("AccountLocked");
        this.userId = userId;
        this.tenantId = tenantId;
        this.lockedBy = lockedBy;
        this.reason = reason;
        this.failedAttempts = failedAttempts;
    }

    public UserId getUserId() { return userId; }
    public TenantId getTenantId() { return tenantId; }
    public String getLockedBy() { return lockedBy; }
    public String getReason() { return reason; }
    public int getFailedAttempts() { return failedAttempts; }
}
