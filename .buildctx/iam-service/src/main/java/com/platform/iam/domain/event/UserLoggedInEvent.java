package com.platform.iam.domain.event;

import com.platform.iam.domain.model.valueobjects.*;
import java.time.Instant;
import java.util.UUID;

/**
 * 领域事件基类
 */

/**
 * 角色分配事件
 */

public final class UserLoggedInEvent extends DomainEvent {
    private final UserId userId;
    private final TenantId tenantId;
    private final String ipAddress;
    private final String userAgent;
    private final String deviceFingerprint;

    public UserLoggedInEvent(UserId userId, TenantId tenantId, String ipAddress, String userAgent, String deviceFingerprint) {
        super("UserLoggedIn");
        this.userId = userId;
        this.tenantId = tenantId;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.deviceFingerprint = deviceFingerprint;
    }

    public UserId getUserId() { return userId; }
    public TenantId getTenantId() { return tenantId; }
    public String getIpAddress() { return ipAddress; }
    public String getUserAgent() { return userAgent; }
    public String getDeviceFingerprint() { return deviceFingerprint; }
}
