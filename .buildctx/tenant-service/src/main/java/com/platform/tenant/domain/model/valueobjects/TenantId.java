package com.platform.tenant.domain.model.valueobjects;

import java.util.Objects;
import java.util.UUID;

/**
 * 租户ID值对象
 * 不可变、自验证、封装业务规则
 */
public final class TenantId {
    private final UUID value;

    private TenantId(UUID value) {
        this.value = Objects.requireNonNull(value, "租户ID不能为空");
    }

    public static TenantId of(UUID value) {
        return new TenantId(value);
    }

    public static TenantId of(String value) {
        return new TenantId(UUID.fromString(value));
    }

    public static TenantId generate() {
        return new TenantId(UUID.randomUUID());
    }

    public UUID getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TenantId tenantId = (TenantId) o;
        return Objects.equals(value, tenantId.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}