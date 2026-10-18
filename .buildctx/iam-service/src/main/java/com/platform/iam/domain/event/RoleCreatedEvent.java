package com.platform.iam.domain.event;

import com.platform.iam.domain.model.valueobjects.*;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * 角色创建事件
 */

/**
 * 角色撤销事件
 */

public final class RoleCreatedEvent extends DomainEvent {
    private final RoleId roleId;
    private final TenantId tenantId;
    private final String code;
    private final String name;
    private final String createdBy;

    public RoleCreatedEvent(RoleId roleId, TenantId tenantId, String code, String name, String createdBy) {
        super("RoleCreated");
        this.roleId = roleId;
        this.tenantId = tenantId;
        this.code = code;
        this.name = name;
        this.createdBy = createdBy;
    }

    public RoleId getRoleId() { return roleId; }
    public TenantId getTenantId() { return tenantId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getCreatedBy() { return createdBy; }
}
