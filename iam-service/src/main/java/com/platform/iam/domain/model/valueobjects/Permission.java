package com.platform.iam.domain.model.valueobjects;

import jakarta.persistence.Embeddable;
import lombok.Getter;

import java.util.Objects;

/**
 * 权限值对象: 资源类型 + 动作 (+ 可选资源ID)
 */
@Getter
@Embeddable
public class Permission {

    private ResourceType resourceType;
    private Action action;
    private String resourceId;

    protected Permission() {
        // JPA
    }

    private Permission(ResourceType resourceType, Action action, String resourceId) {
        this.resourceType = Objects.requireNonNull(resourceType, "资源类型不能为空");
        this.action = Objects.requireNonNull(action, "动作不能为空");
        this.resourceId = resourceId == null || resourceId.isBlank() ? null : resourceId.trim();
    }

    public static Permission of(ResourceType resourceType, Action action, String resourceId) {
        return new Permission(resourceType, action, resourceId);
    }

    public static Permission of(String resourceType, String action) {
        return new Permission(ResourceType.valueOf(resourceType), Action.valueOf(action), null);
    }

    /** 由编码解析权限: 格式 resourceType:action[:resourceId], 支持 "*" 通配 */
    public static Permission fromCode(String code) {
        if (code == null || code.isBlank() || "*".equals(code.trim())) {
            return new Permission(ResourceType.ALL, Action.ALL, null);
        }
        String[] parts = code.trim().split(":");
        ResourceType rt = ResourceType.valueOf(parts[0].toUpperCase());
        Action ac = parts.length > 1 ? Action.valueOf(parts[1].toUpperCase()) : Action.ALL;
        return new Permission(rt, ac, parts.length > 2 ? parts[2] : null);
    }

    /** 通配权限(如 "*")匹配任意 */
    public boolean matches(Permission other) {
        if (resourceType == ResourceType.ALL || other.resourceType == ResourceType.ALL) {
            return true;
        }
        boolean resourceMatch = resourceType == other.resourceType
            && (resourceId == null || other.resourceId == null || resourceId.equals(other.resourceId));
        boolean actionMatch = action == Action.ALL || other.action == Action.ALL || action == other.action;
        return resourceMatch && actionMatch;
    }

    public String getCode() {
        return resourceType.name().toLowerCase() + ":" + action.name().toLowerCase()
            + (resourceId == null ? "" : ":" + resourceId);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Permission p
            && resourceType == p.resourceType && action == p.action
            && Objects.equals(resourceId, p.resourceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resourceType, action, resourceId);
    }

    @Override
    public String toString() {
        return getCode();
    }

    /** 权限资源类型 */
    public enum ResourceType {
        USER("用户"), ROLE("角色"), COURSE("课程"), RESOURCE("学习资源"),
        KNOWLEDGE_POINT("知识点"), TENANT("租户"), REPORT("报表"), SYSTEM("系统"), ALL("全部");

        private final String displayName;

        ResourceType(String displayName) { this.displayName = displayName; }

        public String getDisplayName() { return displayName; }
    }

    /** 权限动作 */
    public enum Action {
        CREATE("创建"), READ("读取"), UPDATE("更新"), DELETE("删除"),
        LIST("列表"), EXPORT("导出"), IMPORT("导入"), ASSIGN("分配"), UNASSIGN("取消分配"),
        ACTIVATE("激活"), SUSPEND("暂停"), RESTORE("恢复"), LOCK("锁定"), UNLOCK("解锁"),
        APPROVE("审批"), REJECT("驳回"), ALL("全部");

        private final String displayName;

        Action(String displayName) { this.displayName = displayName; }

        public String getDisplayName() { return displayName; }
    }
}
