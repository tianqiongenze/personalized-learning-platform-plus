package com.platform.iam.domain.model.valueobjects;

import java.util.Objects;

/**
 * 用户状态枚举
 */
public enum UserStatus {
    PENDING("待激活", false),
    ACTIVE("激活", true),
    SUSPENDED("暂停", false),
    LOCKED("锁定", false),
    EXPIRED("过期", false),
    DELETED("已删除", false);

    private final String displayName;
    private final boolean active;

    UserStatus(String displayName, boolean active) {
        this.displayName = displayName;
        this.active = active;
    }

    public String getDisplayName() { return displayName; }
    public boolean isActive() { return active; }

    public boolean canTransitionTo(UserStatus target) {
        if (this == target) return true;
        return switch (this) {
            case PENDING -> target == ACTIVE || target == DELETED;
            case ACTIVE -> target == SUSPENDED || target == LOCKED || target == EXPIRED || target == DELETED;
            case SUSPENDED -> target == ACTIVE || target == DELETED;
            case LOCKED -> target == ACTIVE || target == DELETED;
            case EXPIRED -> target == ACTIVE || target == DELETED;
            case DELETED -> target == ACTIVE; // 仅支持恢复
        };
    }

    public UserStatus transitionTo(UserStatus target) {
        if (!canTransitionTo(target)) {
            throw new IllegalStateException(
                String.format("用户状态不允许从 %s 流转到 %s", this.displayName, target.displayName)
            );
        }
        return target;
    }
}