package com.platform.course.domain.model.valueobjects;

import java.util.Objects;

/**
 * 资源状态枚举
 */
public enum ResourceStatus {
    DRAFT("草稿", false),
    PUBLISHED("已发布", true),
    DELETED("已删除", false);

    private final String displayName;
    private final boolean published;

    ResourceStatus(String displayName, boolean published) {
        this.displayName = displayName;
        this.published = published;
    }

    public String getDisplayName() { return displayName; }
    public boolean isPublished() { return published; }
}