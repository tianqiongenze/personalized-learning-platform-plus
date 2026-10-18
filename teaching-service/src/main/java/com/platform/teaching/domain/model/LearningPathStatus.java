package com.platform.teaching.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 学习路径状态枚举
 */
public enum LearningPathStatus {
    DRAFT("草稿", false),
    PUBLISHED("已发布", true),
    IN_PROGRESS("进行中", true),
    COMPLETED("已完成", false),
    ARCHIVED("已归档", false),
    DELETED("已删除", false);

    private final String displayName;
    private final boolean active;

    LearningPathStatus(String displayName, boolean active) {
        this.displayName = displayName;
        this.active = active;
    }

    public String getDisplayName() { return displayName; }
    public boolean isActive() { return active; }

    public boolean canTransitionTo(LearningPathStatus target) {
        if (this == target) return true;
        return switch (this) {
            case DRAFT -> target == PUBLISHED || target == DELETED;
            case PUBLISHED -> target == IN_PROGRESS || target == COMPLETED || target == ARCHIVED || target == DELETED;
            case IN_PROGRESS -> target == COMPLETED || target == ARCHIVED || target == DELETED;
            case COMPLETED -> target == ARCHIVED || target == DELETED;
            case ARCHIVED -> target == DELETED;
            case DELETED -> false;
        };
    }
}
