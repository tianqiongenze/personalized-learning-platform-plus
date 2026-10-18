package com.platform.tenant.domain.event;

import com.platform.tenant.domain.model.Tenant;
import com.platform.tenant.domain.model.valueobjects.TenantId;
import java.time.Instant;
import java.util.UUID;

/**
 * 领域事件基类
 */

/**
 * 租户软删除事件
 */

public final class TenantCreatedEvent extends DomainEvent {
    private final String tenantCode;
    private final String tenantName;
    private final String adminEmail;

    public TenantCreatedEvent(TenantId tenantId, String tenantCode, String tenantName, String adminEmail) {
        super(tenantId);
        this.tenantCode = tenantCode;
        this.tenantName = tenantName;
        this.adminEmail = adminEmail;
    }

    public String getTenantCode() { return tenantCode; }
    public String getTenantName() { return tenantName; }
    public String getAdminEmail() { return adminEmail; }
}
