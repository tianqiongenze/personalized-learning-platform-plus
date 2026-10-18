package com.platform.iam.domain.event;

import com.platform.iam.domain.model.valueobjects.*;
import java.time.Instant;
import java.util.UUID;

/**
 * 领域事件基类
 */

/**
 * 用户删除事件
 */

public final class UserUpdatedEvent extends DomainEvent {
    private final UserId userId;
    private final TenantId tenantId;
    private final String changedFields;
    private final String updatedBy;

    public UserUpdatedEvent(UserId userId, TenantId tenantId, String changedFields, String updatedBy) {
        super("UserUpdated");
        this.userId = userId;
        this.tenantId = tenantId;
        this.changedFields = changedFields;
        this.updatedBy = updatedBy;
    }

    public UserId getUserId() { return userId; }
    public TenantId getTenantId() { return tenantId; }
    public String getChangedFields() { return changedFields; }
    public String getUpdatedBy() { return updatedBy; }
}
