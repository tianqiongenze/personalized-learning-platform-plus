package com.platform.course.domain.model.valueobjects;

import java.util.Objects;

/**
 * 课程状态枚举
 */
public enum CourseStatus {
    DRAFT("草稿", false),
    PUBLISHED("已发布", true),
    ARCHIVED("已归档", false),
    DELETED("已删除", false);

    private final String displayName;
    private final boolean published;

    CourseStatus(String displayName, boolean published) {
        this.displayName = displayName;
        this.published = published;
    }

    public String getDisplayName() { return displayName; }
    public boolean isPublished() { return published; }

    public boolean canTransitionTo(CourseStatus target) {
        if (this == target) return true;
        return switch (this) {
            case DRAFT -> target == PUBLISHED || target == DELETED;
            case PUBLISHED -> target == ARCHIVED || target == DELETED;
            case ARCHIVED -> target == PUBLISHED || target == DELETED;
            case DELETED -> false; // 删除不可逆
        };
    }

    public CourseStatus transitionTo(CourseStatus target) {
        if (!canTransitionTo(target)) {
            throw new IllegalStateException(
                String.format("课程状态不允许从 %s 流转到 %s", this.displayName, target.displayName)
            );
        }
        return target;
    }
}