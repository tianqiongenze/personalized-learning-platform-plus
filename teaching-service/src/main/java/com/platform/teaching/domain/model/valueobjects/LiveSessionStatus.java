package com.platform.teaching.domain.model.valueobjects;

import java.util.Objects;

/**
 * 直播会话状态枚举
 */
public enum LiveSessionStatus {
    SCHEDULED("已排期", false),
    LIVE("直播中", true),
    ENDED("已结束", false),
    RECORDING("录制中", true),
    ARCHIVED("已归档", false),
    CANCELLED("已取消", false);

    private final String displayName;
    private final boolean active;

    LiveSessionStatus(String displayName, boolean active) {
        this.displayName = displayName;
        this.active = active;
    }

    public String getDisplayName() { return displayName; }
    public boolean isActive() { return active; }

    public boolean canTransitionTo(LiveSessionStatus target) {
        if (this == target) return true;
        return switch (this) {
            case SCHEDULED -> target == LIVE || target == CANCELLED;
            case LIVE -> target == ENDED || target == RECORDING || target == CANCELLED;
            case RECORDING -> target == ENDED || target == CANCELLED;
            case ENDED -> target == ARCHIVED;
            case ARCHIVED -> false;
            case CANCELLED -> false;
        };
    }

    public LiveSessionStatus transitionTo(LiveSessionStatus target) {
        if (!canTransitionTo(target)) {
            throw new IllegalStateException(
                String.format("直播状态不允许从 %s 流转到 %s", this.displayName, target.displayName)
            );
        }
        return target;
    }
}