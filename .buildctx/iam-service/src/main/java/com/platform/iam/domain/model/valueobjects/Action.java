package com.platform.iam.domain.model.valueobjects;

import java.util.Objects;

/**
 * 权限资源类型枚举
 */
public enum Action {
    CREATE("创建"),
    READ("读取"),
    UPDATE("更新"),
    DELETE("删除"),
    LIST("列表"),
    EXPORT("导出"),
    IMPORT("导入"),
    ASSIGN("分配"),
    UNASSIGN("取消分配"),
    ACTIVATE("激活"),
    SUSPEND("暂停"),
    RESTORE("恢复"),
    LOCK("锁定"),
    UNLOCK("解锁"),
    APPROVE("审批"),
    REJECT("驳回");

    private final String displayName;

    Action(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
