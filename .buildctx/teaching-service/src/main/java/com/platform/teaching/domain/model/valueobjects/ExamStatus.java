package com.platform.teaching.domain.model.valueobjects;

import java.util.Objects;

/**
 * 考试状态枚举
 */
public enum ExamStatus {
    DRAFT("草稿", false),
    PUBLISHED("已发布", true),
    ONGOING("进行中", true),
    ENDED("已结束", false),
    GRADED("已评分", false),
    ARCHIVED("已归档", false),
    DELETED("已删除", false);

    private final String displayName;
    private final boolean active;

    ExamStatus(String displayName, boolean active) {
        this.displayName = displayName;
        this.active = active;
    }

    public String getDisplayName() { return displayName; }
    public boolean isActive() { return active; }

    public boolean canTransitionTo(ExamStatus target) {
        if (this == target) return true;
        return switch (this) {
            case DRAFT -> target == PUBLISHED || target == DELETED;
            case PUBLISHED -> target == ONGOING || target == ARCHIVED || target == DELETED;
            case ONGOING -> target == ENDED || target == ARCHIVED || target == DELETED;
            case ENDED -> target == GRADED || target == ARCHIVED || target == DELETED;
            case GRADED -> target == ARCHIVED || target == DELETED;
            case ARCHIVED -> target == DELETED;
            case DELETED -> false;
        };
    }

    public ExamStatus transitionTo(ExamStatus target) {
        if (!canTransitionTo(target)) {
            throw new IllegalStateException(
                String.format("考试状态不允许从 %s 流转到 %s", this.displayName, target.displayName)
            );
        }
        return target;
    }
}