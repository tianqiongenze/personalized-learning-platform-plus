package com.platform.iam.domain.event;

import com.platform.iam.domain.model.valueobjects.*;
import java.time.Instant;
import java.util.UUID;

/**
 * 领域事件基类
 */

/**
 * 账户锁定事件
 */

public final class PasswordChangedEvent extends DomainEvent {
    private final UserId userId;
    private final TenantId tenantId;
    private final String changedBy;
    private final boolean selfChanged;

    public PasswordChangedEvent(UserId userId, TenantId tenantId, String changedBy, boolean selfChanged) {
        super("PasswordChanged");
        this.userId = userId;
        this.tenantId = tenantId;
        this.changedBy = changedBy;
        this.selfChanged = selfChanged;
    }

    public UserId getUserId() { return userId; }
    public TenantId getTenantId() { return tenantId; }
    public String getChangedBy() { return changedBy; }
    public boolean isSelfChanged() { return selfChanged; }
}
