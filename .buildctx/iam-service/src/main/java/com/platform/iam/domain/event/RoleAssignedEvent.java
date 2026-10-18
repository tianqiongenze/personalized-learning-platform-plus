package com.platform.iam.domain.event;

import com.platform.iam.domain.model.valueobjects.*;
import java.time.Instant;
import java.util.UUID;

/**
 * 领域事件基类
 */

public final class RoleAssignedEvent extends DomainEvent {
    private final UserId userId;
    private final TenantId tenantId;
    private final RoleId roleId;
    private final String assignedBy;

    public RoleAssignedEvent(UserId userId, TenantId tenantId, RoleId roleId, String assignedBy) {
        super("RoleAssigned");
        this.userId = userId;
        this.tenantId = tenantId;
        this.roleId = roleId;
        this.assignedBy = assignedBy;
    }

    public UserId getUserId() { return userId; }
    public TenantId getTenantId() { return tenantId; }
    public RoleId getRoleId() { return roleId; }
    public String getAssignedBy() { return assignedBy; }
}
