package com.platform.iam.domain.event;

import com.platform.iam.domain.model.valueobjects.*;
import java.time.Instant;
import java.util.UUID;

/**
 * 领域事件基类
 */

/**
 * 角色状态变更名变更事件
 */

public final class UserCreatedEvent extends DomainEvent {
    private final UserId userId;
    private final TenantId tenantId;
    private final String username;
    private final String email;
    private final String createdBy;

    public UserCreatedEvent(UserId userId, TenantId tenantId, String username, String email, String createdBy) {
        super("UserCreated");
        this.userId = userId;
        this.tenantId = tenantId;
        this.username = username;
        this.email = email;
        this.createdBy = createdBy;
    }

    public UserId getUserId() { return userId; }
    public TenantId getTenantId() { return tenantId; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getCreatedBy() { return createdBy; }
}
