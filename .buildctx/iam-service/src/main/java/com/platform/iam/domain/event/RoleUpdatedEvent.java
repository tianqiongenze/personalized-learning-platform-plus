package com.platform.iam.domain.event;

import com.platform.iam.domain.model.valueobjects.*;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * 角色创建事件
 */

/**
 * 角色删除事件
 */

public final class RoleUpdatedEvent extends DomainEvent {
    private final RoleId roleId;
    private final TenantId tenantId;
    private final String changedFields;
    private final String updatedBy;

    public RoleUpdatedEvent(RoleId roleId, TenantId tenantId, String changedFields, String updatedBy) {
        super("RoleUpdated");
        this.roleId = roleId;
        this.tenantId = tenantId;
        this.changedFields = changedFields;
        this.updatedBy = updatedBy;
    }

    public RoleId getRoleId() { return roleId; }
    public TenantId getTenantId() { return tenantId; }
    public String getChangedFields() { return changedFields; }
    public String getUpdatedBy() { return updatedBy; }
}
