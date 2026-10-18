package com.platform.iam.domain.event;

import com.platform.iam.domain.model.valueobjects.*;
import java.time.Instant;
import java.util.UUID;

/**
 * 领域事件基类
 */

/**
 * 登录失败事件
 */

public final class UserStatusChangedEvent extends DomainEvent {
    private final UserId userId;
    private final TenantId tenantId;
    private final String oldStatus;
    private final String newStatus;
    private final String changedBy;
    private final String reason;

    public UserStatusChangedEvent(UserId userId, TenantId tenantId, String oldStatus, String newStatus, 
                                  String changedBy, String reason) {
        super("UserStatusChanged");
        this.userId = userId;
        this.tenantId = tenantId;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.changedBy = changedBy;
        this.reason = reason;
    }

    public UserId getUserId() { return userId; }
    public TenantId getTenantId() { return tenantId; }
    public String getOldStatus() { return oldStatus; }
    public String getNewStatus() { return newStatus; }
    public String getChangedBy() { return changedBy; }
    public String getReason() { return reason; }
}
