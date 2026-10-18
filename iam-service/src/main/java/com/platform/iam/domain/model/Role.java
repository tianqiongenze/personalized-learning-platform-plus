package com.platform.iam.domain.model;

import com.platform.iam.domain.event.*;
import com.platform.iam.domain.model.valueobjects.*;
import java.time.Instant;
import java.util.*;

/**
 * 角色聚合根
 * 封装角色生命周期管理的业务规则
 */
public class Role {
    private final RoleId id;
    private final TenantId tenantId;
    private String code;
    private String name;
    private String description;
    private final Set<Permission> permissions = new HashSet<>();
    private RoleStatus status;
    private final boolean isSystem;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;
    private String deletedBy;
    private String deleteReason;
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    // 构造函数 - 仅工厂方法使用
    private Role(RoleId id, TenantId tenantId, String code, String name, String description,
                 boolean isSystem, RoleStatus status, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "角色ID不能为空");
        this.tenantId = Objects.requireNonNull(tenantId, "租户ID不能为空");
        this.code = validateCode(code);
        this.name = validateName(name);
        this.description = description != null ? description.trim() : null;
        this.isSystem = isSystem;
        this.status = status != null ? status : RoleStatus.ACTIVE;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = this.createdAt;
    }

    private String validateCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("角色编码不能为空");
        }
        String trimmed = code.trim().toUpperCase();
        if (!trimmed.matches("^[A-Z0-9_]{2,50}$")) {
            throw new IllegalArgumentException("角色编码必须为2-50位大写字母、数字或下划线");
        }
        return trimmed;
    }

    private String validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("角色名称不能为空");
        }
        String trimmed = name.trim();
        if (trimmed.length() > 100) {
            throw new IllegalArgumentException("角色名称不能超过100字符");
        }
        return trimmed;
    }

    /**
     * 创建新角色（工厂方法）
     */
    public static Role create(TenantId tenantId, String code, String name, String description,
                              boolean isSystem, String createdBy) {
        Role role = new Role(
            RoleId.generate(), tenantId, code, name, description,
            isSystem, RoleStatus.ACTIVE, Instant.now()
        );
        role.addDomainEvent(new RoleCreatedEvent(role.id, role.tenantId, role.code, role.name, createdBy));
        return role;
    }

    /**
     * 创建系统内置角色
     */
    public static Role createSystemRole(TenantId tenantId, String code, String name, String description,
                                        Set<Permission> permissions, String createdBy) {
        Role role = new Role(
            RoleId.generate(), tenantId, code, name, description,
            true, RoleStatus.ACTIVE, Instant.now()
        );
        role.permissions.addAll(permissions);
        role.addDomainEvent(new RoleCreatedEvent(role.id, role.tenantId, role.code, role.name, createdBy));
        return role;
    }

    /**
     * 从持久化重建角色
     */
    public static Role reconstruct(RoleId id, TenantId tenantId, String code, String name, String description,
                                   Set<Permission> permissions, RoleStatus status, boolean isSystem,
                                   Instant createdAt, Instant updatedAt, Instant deletedAt,
                                   String deletedBy, String deleteReason) {
        Role role = new Role(id, tenantId, code, name, description, isSystem, status, createdAt);
        role.permissions.addAll(permissions);
        role.updatedAt = updatedAt;
        role.deletedAt = deletedAt;
        role.deletedBy = deletedBy;
        role.deleteReason = deleteReason;
        return role;
    }

    // ========== 业务方法 ==========

    /**
     * 添加权限
     */
    public void addPermission(Permission permission, String addedBy) {
        if (permissions.add(permission)) {
            updatedAt = Instant.now();
            addDomainEvent(new PermissionAddedEvent(id, tenantId, permission, addedBy));
        }
    }

    /**
     * 移除权限
     */
    public void removePermission(Permission permission, String removedBy) {
        if (permissions.remove(permission)) {
            updatedAt = Instant.now();
            addDomainEvent(new PermissionRemovedEvent(id, tenantId, permission, removedBy));
        }
    }

    /**
     * 批量设置权限
     */
    public void setPermissions(Set<Permission> permissions, String updatedBy) {
        this.permissions.clear();
        this.permissions.addAll(permissions);
        updatedAt = Instant.now();
        addDomainEvent(new PermissionsUpdatedEvent(id, tenantId, permissions, updatedBy));
    }

    /**
     * 检查是否拥有权限
     */
    public boolean hasPermission(Permission permission) {
        return permissions.stream().anyMatch(p -> p.matches(permission));
    }

    /**
     * 检查是否拥有指定资源和动作的权限
     */
    public boolean hasPermission(Permission.ResourceType resourceType, Permission.Action action, String resourceId) {
        Permission checkPerm = Permission.of(resourceType, action, resourceId);
        return hasPermission(checkPerm);
    }

    /**
     * 更新基本信息
     */
    public void updateInfo(String name, String description, String updatedBy) {
        this.name = validateName(name);
        this.description = description != null ? description.trim() : null;
        updatedAt = Instant.now();
        addDomainEvent(new RoleUpdatedEvent(id, tenantId, "name,description", updatedBy));
    }

    /**
     * 更新编码（仅非系统角色允许）
     */
    public void updateCode(String code, String updatedBy) {
        if (isSystem) {
            throw new IllegalStateException("系统内置角色不允许修改编码");
        }
        this.code = validateCode(code);
        updatedAt = Instant.now();
        addDomainEvent(new RoleUpdatedEvent(id, tenantId, "code", updatedBy));
    }

    /**
     * 停用角色
     */
    public void deactivate(String deactivatedBy) {
        if (isSystem) {
            throw new IllegalStateException("系统内置角色不允许停用");
        }
        status = RoleStatus.INACTIVE;
        updatedAt = Instant.now();
        addDomainEvent(new RoleStatusChangedEvent(id, tenantId, RoleStatus.ACTIVE.name(), RoleStatus.INACTIVE.name(), deactivatedBy));
    }

    /**
     * 激活角色
     */
    public void activate(String activatedBy) {
        status = RoleStatus.ACTIVE;
        updatedAt = Instant.now();
        addDomainEvent(new RoleStatusChangedEvent(id, tenantId, RoleStatus.INACTIVE.name(), RoleStatus.ACTIVE.name(), activatedBy));
    }

    /**
     * 删除角色
     */
    public void delete(String deletedBy, String reason) {
        if (isSystem) {
            throw new IllegalStateException("系统内置角色不允许删除");
        }
        if (status == RoleStatus.DELETED) {
            throw new IllegalStateException("角色已被删除");
        }
        status = RoleStatus.DELETED;
        deletedAt = Instant.now();
        deletedBy = deletedBy;
        deleteReason = reason;
        updatedAt = Instant.now();
        addDomainEvent(new RoleDeletedEvent(id, tenantId, deletedBy, reason));
    }

    // ========== 领域事件管理 ==========

    private void addDomainEvent(DomainEvent event) {
        domainEvents.add(event);
    }

    public List<DomainEvent> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    public void clearDomainEvents() {
        domainEvents.clear();
    }

    // ========== Getters ==========

    public RoleId getId() { return id; }
    public TenantId getTenantId() { return tenantId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Set<Permission> getPermissions() { return Collections.unmodifiableSet(permissions); }
    public RoleStatus getStatus() { return status; }
    public boolean isSystem() { return isSystem; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public String getDeletedBy() { return deletedBy; }
    public String getDeleteReason() { return deleteReason; }
    public boolean isDeleted() { return status == RoleStatus.DELETED; }
    public boolean isActive() { return status.isActive(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Role role = (Role) o;
        return Objects.equals(id, role.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Role{" +
               "id=" + id +
               ", tenantId=" + tenantId +
               ", code='" + code + '\'' +
               ", name='" + name + '\'' +
               ", status=" + status +
               ", isSystem=" + isSystem +
               '}';
    }
}