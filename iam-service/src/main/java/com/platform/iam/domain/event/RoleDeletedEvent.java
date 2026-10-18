package com.platform.iam.domain.event;

import com.platform.iam.domain.model.valueobjects.*;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * 角色创建事件
 */

/**
 * 角色更新事件
 */

public final class RoleDeletedEvent extends DomainEvent {
    private final RoleId roleId;
    private final TenantId tenantId;
    private final String deletedBy;
    private final String reason;

    public RoleDeletedEvent(RoleId roleId, TenantId tenantId, String deletedBy, String reason) {
        super("RoleDeleted");
        this.roleId = roleId;
        this.tenantId = tenantId;
        this.deletedBy = deletedBy;
        this.reason = reason;
    }

    public RoleId getRoleId() { return roleId; }
    public TenantId getTenantId() { return tenantId; }
    public String getDeletedBy() { return deletedBy; }
    public String getReason() { return reason; }
}
