package com.platform.course.domain.model.valueobjects;

import java.util.Objects;

/**
 * 章节状态枚举
 */
public enum ChapterStatus {
    DRAFT("草稿", false),
    PUBLISHED("已发布", true),
    DELETED("已删除", false);

    private final String displayName;
    private final boolean published;

    ChapterStatus(String displayName, boolean published) {
        this.displayName = displayName;
        this.published = published;
    }

    public String getDisplayName() { return displayName; }
    public boolean isPublished() { return published; }
}