package com.platform.iam.domain.event;

import com.platform.iam.domain.model.valueobjects.*;
import java.time.Instant;
import java.util.UUID;

/**
 * 领域事件基类
 */

/**
 * 权限添加事件
 */

public final class RoleRevokedEvent extends DomainEvent {
    private final UserId userId;
    private final TenantId tenantId;
    private final RoleId roleId;
    private final String revokedBy;

    public RoleRevokedEvent(UserId userId, TenantId tenantId, RoleId roleId, String revokedBy) {
        super("RoleRevoked");
        this.userId = userId;
        this.tenantId = tenantId;
        this.roleId = roleId;
        this.revokedBy = revokedBy;
    }

    public UserId getUserId() { return userId; }
    public TenantId getTenantId() { return tenantId; }
    public RoleId getRoleId() { return roleId; }
    public String getRevokedBy() { return revokedBy; }
}
