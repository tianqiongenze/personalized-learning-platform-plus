package com.platform.iam.domain.model.valueobjects;

import java.util.Objects;
import java.util.UUID;

/**
 * 权限ID值对象
 */

/**
 * 权限值对象
 * 格式: resourceType:action:resourceId (resourceId 可选，为空表示全局权限)
 */

public final class PermissionId {
    private final UUID value;

    private PermissionId(UUID value) {
        this.value = Objects.requireNonNull(value, "权限ID不能为空");
    }

    public static PermissionId of(UUID value) {
        return new PermissionId(value);
    }

    public static PermissionId of(String value) {
        return new PermissionId(UUID.fromString(value));
    }

    public static PermissionId generate() {
        return new PermissionId(UUID.randomUUID());
    }

    public UUID getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PermissionId permissionId = (PermissionId) o;
        return Objects.equals(value, permissionId.value);
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