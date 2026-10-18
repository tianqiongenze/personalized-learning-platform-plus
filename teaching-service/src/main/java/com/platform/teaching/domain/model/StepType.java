package com.platform.teaching.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 学习路径状态枚举
 */

public enum StepType {
    VIDEO("视频"),
    DOCUMENT("文档"),
    QUIZ("测验"),
    ASSIGNMENT("作业"),
    EXAM("考试"),
    DISCUSSION("讨论"),
    LIVE_SESSION("直播"),
    PRACTICE("练习"),
    READING("阅读"),
    PROJECT("项目");

    private final String displayName;

    StepType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() { return displayName; }
}
