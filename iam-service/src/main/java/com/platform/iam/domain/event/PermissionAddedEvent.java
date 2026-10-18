package com.platform.iam.domain.event;

import com.platform.iam.domain.model.valueobjects.*;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * 角色创建事件
 */

/**
 * 登录事件
 */

public final class PermissionAddedEvent extends DomainEvent {
    private final RoleId roleId;
    private final TenantId tenantId;
    private final Permission permission;
    private final String addedBy;

    public PermissionAddedEvent(RoleId roleId, TenantId tenantId, Permission permission, String addedBy) {
        super("PermissionAdded");
        this.roleId = roleId;
        this.tenantId = tenantId;
        this.permission = permission;
        this.addedBy = addedBy;
    }

    public RoleId getRoleId() { return roleId; }
    public TenantId getTenantId() { return tenantId; }
    public Permission getPermission() { return permission; }
    public String getAddedBy() { return addedBy; }
}
