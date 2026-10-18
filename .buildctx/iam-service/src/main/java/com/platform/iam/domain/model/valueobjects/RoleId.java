package com.platform.iam.domain.model.valueobjects;

import java.util.Objects;
import java.util.UUID;

/**
 * 角色ID值对象
 */

/**
 * 权限动作枚举
 */

public final class RoleId {
    private final UUID value;

    private RoleId(UUID value) {
        this.value = Objects.requireNonNull(value, "角色ID不能为空");
    }

    public static RoleId of(UUID value) {
        return new RoleId(value);
    }

    public static RoleId of(String value) {
        return new RoleId(UUID.fromString(value));
    }

    public static RoleId generate() {
        return new RoleId(UUID.randomUUID());
    }

    public UUID getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RoleId roleId = (RoleId) o;
        return Objects.equals(value, roleId.value);
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