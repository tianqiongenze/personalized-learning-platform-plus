package com.platform.iam.domain.event;

import com.platform.iam.domain.model.valueobjects.*;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * 角色创建事件
 */

/**
 * 密码变更事件
 */

public final class RoleStatusChangedEvent extends DomainEvent {
    private final RoleId roleId;
    private final TenantId tenantId;
    private final String oldStatus;
    private final String newStatus;
    private final String changedBy;

    public RoleStatusChangedEvent(RoleId roleId, TenantId tenantId, String oldStatus, String newStatus, String changedBy) {
        super("RoleStatusChanged");
        this.roleId = roleId;
        this.tenantId = tenantId;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.changedBy = changedBy;
    }

    public RoleId getRoleId() { return roleId; }
    public TenantId getTenantId() { return tenantId; }
    public String getOldStatus() { return oldStatus; }
    public String getNewStatus() { return newStatus; }
    public String getChangedBy() { return changedBy; }
}
