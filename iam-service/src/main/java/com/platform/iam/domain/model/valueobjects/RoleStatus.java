package com.platform.iam.domain.model.valueobjects;

import java.util.Objects;

/**
 * 角色状态枚举
 */
public enum RoleStatus {
    ACTIVE("激活", true),
    INACTIVE("停用", false),
    DELETED("已删除", false);

    private final String displayName;
    private final boolean active;

    RoleStatus(String displayName, boolean active) {
        this.displayName = displayName;
        this.active = active;
    }

    public String getDisplayName() { return displayName; }
    public boolean isActive() { return active; }
}