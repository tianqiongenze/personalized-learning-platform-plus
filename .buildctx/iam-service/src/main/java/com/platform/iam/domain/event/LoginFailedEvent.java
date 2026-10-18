package com.platform.iam.domain.event;

import com.platform.iam.domain.model.valueobjects.*;
import java.time.Instant;
import java.util.UUID;

/**
 * 领域事件基类
 */

/**
 * 用户创建事件
 */

public final class LoginFailedEvent extends DomainEvent {
    private final String username;
    private final TenantId tenantId;
    private final String ipAddress;
    private final String reason;
    private final int attemptCount;

    public LoginFailedEvent(String username, TenantId tenantId, String ipAddress, String reason, int attemptCount) {
        super("LoginFailed");
        this.username = username;
        this.tenantId = tenantId;
        this.ipAddress = ipAddress;
        this.reason = reason;
        this.attemptCount = attemptCount;
    }

    public String getUsername() { return username; }
    public TenantId getTenantId() { return tenantId; }
    public String getIpAddress() { return ipAddress; }
    public String getReason() { return reason; }
    public int getAttemptCount() { return attemptCount; }
}
