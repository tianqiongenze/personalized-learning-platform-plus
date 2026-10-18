package com.platform.iam.domain.event;

import com.platform.iam.domain.model.valueobjects.*;
import java.time.Instant;
import java.util.UUID;

/**
 * 领域事件基类
 */

/**
 * 用户更新事件
 */

public final class UserDeletedEvent extends DomainEvent {
    private final UserId userId;
    private final TenantId tenantId;
    private final String deletedBy;
    private final String reason;

    public UserDeletedEvent(UserId userId, TenantId tenantId, String deletedBy, String reason) {
        super("UserDeleted");
        this.userId = userId;
        this.tenantId = tenantId;
        this.deletedBy = deletedBy;
        this.reason = reason;
    }

    public UserId getUserId() { return userId; }
    public TenantId getTenantId() { return tenantId; }
    public String getDeletedBy() { return deletedBy; }
    public String getReason() { return reason; }
}
