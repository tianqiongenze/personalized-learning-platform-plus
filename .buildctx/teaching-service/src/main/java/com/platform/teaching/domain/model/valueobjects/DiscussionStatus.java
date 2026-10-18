package com.platform.teaching.domain.model.valueobjects;

import java.util.Objects;

/**
 * 讨论状态枚举
 */
public enum DiscussionStatus {
    OPEN("开放", true),
    CLOSED("关闭", false),
    PINNED("置顶", true),
    LOCKED("锁定", false),
    ARCHIVED("已归档", false),
    DELETED("已删除", false);

    private final String displayName;
    private final boolean active;

    DiscussionStatus(String displayName, boolean active) {
        this.displayName = displayName;
        this.active = active;
    }

    public String getDisplayName() { return displayName; }
    public boolean isActive() { return active; }
}