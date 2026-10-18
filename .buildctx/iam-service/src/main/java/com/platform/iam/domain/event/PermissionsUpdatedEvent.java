package com.platform.iam.domain.event;

import com.platform.iam.domain.model.valueobjects.*;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * 角色创建事件
 */

/**
 * 权限批量更新事件
 */

public final class PermissionsUpdatedEvent extends DomainEvent {
    private final RoleId roleId;
    private final TenantId tenantId;
    private final Set<Permission> permissions;
    private final String updatedBy;

    public PermissionsUpdatedEvent(RoleId roleId, TenantId tenantId, Set<Permission> permissions, String updatedBy) {
        super("PermissionsUpdated");
        this.roleId = roleId;
        this.tenantId = tenantId;
        this.permissions = permissions;
        this.updatedBy = updatedBy;
    }

    public RoleId getRoleId() { return roleId; }
    public TenantId getTenantId() { return tenantId; }
    public Set<Permission> getPermissions() { return permissions; }
    public String getUpdatedBy() { return updatedBy; }
}
