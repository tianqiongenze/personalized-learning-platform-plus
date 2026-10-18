package com.platform.iam.domain.event;

import com.platform.iam.domain.model.valueobjects.*;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * 角色创建事件
 */

/**
 * 权限移除事件
 */

public final class PermissionRemovedEvent extends DomainEvent {
    private final RoleId roleId;
    private final TenantId tenantId;
    private final Permission permission;
    private final String removedBy;

    public PermissionRemovedEvent(RoleId roleId, TenantId tenantId, Permission permission, String removedBy) {
        super("PermissionRemoved");
        this.roleId = roleId;
        this.tenantId = tenantId;
        this.permission = permission;
        this.removedBy = removedBy;
    }

    public RoleId getRoleId() { return roleId; }
    public TenantId getTenantId() { return tenantId; }
    public Permission getPermission() { return permission; }
    public String getRemovedBy() { return removedBy; }
}
