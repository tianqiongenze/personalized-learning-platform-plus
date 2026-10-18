package com.platform.iam.domain.model.valueobjects;

import java.util.Objects;

/**
 * 权限资源类型枚举
 */
public enum ResourceType {
    TENANT("租户"),
    USER("用户"),
    ROLE("角色"),
    PERMISSION("权限"),
    COURSE("课程"),
    KNOWLEDGE_POINT("知识点"),
    RESOURCE("资源"),
    ASSIGNMENT("作业"),
    EXAM("考试"),
    ANALYTICS("学情分析"),
    SYSTEM("系统管理");

    private final String displayName;

    ResourceType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
