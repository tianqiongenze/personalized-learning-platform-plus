package com.platform.iam.domain.model;

import com.platform.iam.domain.event.*;
import com.platform.iam.domain.model.valueobjects.*;
import java.time.Instant;
import java.util.*;

/**
 * 用户聚合根
 * 核心业务实体，封装用户生命周期管理的所有业务规则
 */
public class User {
    private final UserId id;
    private final TenantId tenantId;
    private String username;
    private String email;
    private String phone;
    private String passwordHash;
    private UserStatus status;
    private final Set<RoleId> roleIds = new HashSet<>();
    private Instant lastLoginAt;
    private int failedLoginAttempts;
    private Instant lockedAt;
    private String lockedReason;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;
    private String deletedBy;
    private String deleteReason;
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    /**
     * 重建构造函数(从持久化恢复, 跳过密码哈希等创建期校验)。
     * 修复: 原实现向强制非空 passwordHash 的创建期构造函数传 null 再反射注入,
     * 导致任何持久化恢复都会抛 Objects.requireNonNull —— 用户永远无法重建。
     */
    private User(UserId id, TenantId tenantId, String username, String email, String phone,
                 String passwordHash, UserStatus status, Instant createdAt,
                 boolean rehydrating) {
        this.id = Objects.requireNonNull(id, "用户ID不能为空");
        this.tenantId = Objects.requireNonNull(tenantId, "租户ID不能为空");
        this.username = validateUsername(username);
        this.email = validateEmail(email);
        this.phone = phone != null ? phone.trim() : null;
        if (rehydrating) {
            this.passwordHash = passwordHash;   // 重建允许为脏数据/占位
        } else {
            this.passwordHash = Objects.requireNonNull(passwordHash, "密码哈希不能为空");
        }
        this.status = status != null ? status : UserStatus.PENDING;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = this.createdAt;
        this.failedLoginAttempts = 0;
    }

    // 构造函数 - 仅工厂方法使用
    private User(UserId id, TenantId tenantId, String username, String email, String phone,
                 String passwordHash, UserStatus status, Instant createdAt) {
        this(id, tenantId, username, email, phone, passwordHash, status, createdAt, false);
    }

    private String validateUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        String trimmed = username.trim();
        if (trimmed.length() < 3 || trimmed.length() > 50) {
            throw new IllegalArgumentException("用户名长度必须在3-50字符之间");
        }
        if (!trimmed.matches("^[a-zA-Z0-9_-]+$")) {
            throw new IllegalArgumentException("用户名只能包含字母、数字、下划线和连字符");
        }
        return trimmed;
    }

    private String validateEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("邮箱不能为空");
        }
        String trimmed = email.trim();
        if (!trimmed.matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")) {
            throw new IllegalArgumentException("邮箱格式无效");
        }
        return trimmed.toLowerCase();
    }

    /**
     * 创建新用户（工厂方法）
     */
    public static User create(TenantId tenantId, String username, String email, String phone,
                              String passwordHash, String createdBy) {
        User user = new User(
            UserId.generate(), tenantId, username, email, phone,
            passwordHash, UserStatus.PENDING, Instant.now()
        );
        user.addDomainEvent(new UserCreatedEvent(user.id, user.tenantId, user.username, user.email, createdBy));
        return user;
    }

    /**
     * 从持久化重建用户
     */
    public static User reconstruct(UserId id, TenantId tenantId, String username, String email, String phone,
                                   String passwordHash, UserStatus status, Set<RoleId> roleIds,
                                   Instant lastLoginAt, int failedLoginAttempts, Instant lockedAt,
                                   String lockedReason, Instant createdAt, Instant updatedAt,
                                   Instant deletedAt, String deletedBy, String deleteReason) {
        User user = new User(id, tenantId, username, email, phone, passwordHash, status, createdAt, true);
        user.roleIds.addAll(roleIds);
        user.lastLoginAt = lastLoginAt;
        user.failedLoginAttempts = failedLoginAttempts;
        user.lockedAt = lockedAt;
        user.lockedReason = lockedReason;
        user.updatedAt = updatedAt;
        user.deletedAt = deletedAt;
        user.deletedBy = deletedBy;
        user.deleteReason = deleteReason;
        return user;
    }

    // ========== 业务方法 ==========

    /**
     * 激活用户（从待激活态）
     */
    public void activate(String activatedBy) {
        status = status.transitionTo(UserStatus.ACTIVE);
        updatedAt = Instant.now();
        addDomainEvent(new UserStatusChangedEvent(id, tenantId, UserStatus.PENDING.name(), UserStatus.ACTIVE.name(), 
            activatedBy, "用户激活"));
    }

    /**
     * 暂停用户
     */
    public void suspend(String reason, String suspendedBy) {
        status = status.transitionTo(UserStatus.SUSPENDED);
        updatedAt = Instant.now();
        addDomainEvent(new UserStatusChangedEvent(id, tenantId, status.name(), UserStatus.SUSPENDED.name(), 
            suspendedBy, reason));
    }

    /**
     * 恢复用户（从暂停/锁定/过期/删除态）
     */
    public void restore(String restoredBy) {
        String oldStatus = status.name();
        status = status.transitionTo(UserStatus.ACTIVE);
        updatedAt = Instant.now();
        failedLoginAttempts = 0;
        lockedAt = null;
        lockedReason = null;
        addDomainEvent(new UserStatusChangedEvent(id, tenantId, oldStatus, UserStatus.ACTIVE.name(), 
            restoredBy, "用户恢复"));
    }

    /**
     * 过期用户
     */
    public void expire(String expiredBy) {
        status = status.transitionTo(UserStatus.EXPIRED);
        updatedAt = Instant.now();
        addDomainEvent(new UserStatusChangedEvent(id, tenantId, status.name(), UserStatus.EXPIRED.name(), 
            expiredBy, "账户过期"));
    }

    /**
     * 锁定用户
     */
    public void lock(String reason, String lockedBy) {
        status = status.transitionTo(UserStatus.LOCKED);
        lockedAt = Instant.now();
        lockedReason = reason;
        updatedAt = Instant.now();
        addDomainEvent(new AccountLockedEvent(id, tenantId, lockedBy, reason, failedLoginAttempts));
    }

    /**
     * 软删除用户
     */
    public void delete(String deletedBy, String reason) {
        if (status == UserStatus.DELETED) {
            throw new IllegalStateException("用户已被删除");
        }
        status = status.transitionTo(UserStatus.DELETED);
        deletedAt = Instant.now();
        deletedBy = deletedBy;
        deleteReason = reason;
        updatedAt = Instant.now();
        addDomainEvent(new UserDeletedEvent(id, tenantId, deletedBy, reason));
    }

    /**
     * 更新基本信息
     */
    public void updateInfo(String email, String phone, String updatedBy) {
        String oldEmail = this.email;
        this.email = validateEmail(email);
        this.phone = phone != null ? phone.trim() : null;
        updatedAt = Instant.now();
        String changedFields = "email" + (oldEmail.equals(email) ? "" : " changed") + ", phone";
        addDomainEvent(new UserUpdatedEvent(id, tenantId, changedFields, updatedBy));
    }

    /**
     * 更新用户名（仅待激活态允许）
     */
    public void updateUsername(String username, String updatedBy) {
        if (status != UserStatus.PENDING) {
            throw new IllegalStateException("仅待激活态用户允许修改用户名");
        }
        this.username = validateUsername(username);
        updatedAt = Instant.now();
        addDomainEvent(new UserUpdatedEvent(id, tenantId, "username", updatedBy));
    }

    /**
     * 修改密码
     */
    public void changePassword(String newPasswordHash, String changedBy, boolean selfChanged) {
        this.passwordHash = Objects.requireNonNull(newPasswordHash, "新密码哈希不能为空");
        updatedAt = Instant.now();
        addDomainEvent(new PasswordChangedEvent(id, tenantId, changedBy, selfChanged));
    }

    /**
     * 记录登录成功
     */
    public void recordLoginSuccess(String ipAddress, String userAgent, String deviceFingerprint) {
        lastLoginAt = Instant.now();
        failedLoginAttempts = 0;
        lockedAt = null;
        lockedReason = null;
        updatedAt = Instant.now();
        addDomainEvent(new UserLoggedInEvent(id, tenantId, ipAddress, userAgent, deviceFingerprint));
    }

    /**
     * 记录登录失败
     */
    public void recordLoginFailure(String ipAddress, String reason) {
        failedLoginAttempts++;
        updatedAt = Instant.now();
        addDomainEvent(new LoginFailedEvent(username, tenantId, ipAddress, reason, failedLoginAttempts));
        
        // 连续失败5次自动锁定
        if (failedLoginAttempts >= 5 && status == UserStatus.ACTIVE) {
            lock("连续登录失败 " + failedLoginAttempts + " 次", "system");
        }
    }

    /**
     * 分配角色
     */
    public void assignRole(RoleId roleId, String assignedBy) {
        if (roleIds.add(roleId)) {
            updatedAt = Instant.now();
            addDomainEvent(new RoleAssignedEvent(id, tenantId, roleId, assignedBy));
        }
    }

    /**
     * 撤销角色
     */
    public void revokeRole(RoleId roleId, String revokedBy) {
        if (roleIds.remove(roleId)) {
            updatedAt = Instant.now();
            addDomainEvent(new RoleRevokedEvent(id, tenantId, roleId, revokedBy));
        }
    }

    /**
     * 检查是否拥有角色
     */
    public boolean hasRole(RoleId roleId) {
        return roleIds.contains(roleId);
    }

    /**
     * 检查是否可以登录
     */
    public boolean canLogin() {
        return status.isActive() && failedLoginAttempts < 5;
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

    public UserId getId() { return id; }
    public TenantId getTenantId() { return tenantId; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getPasswordHash() { return passwordHash; }
    public UserStatus getStatus() { return status; }
    public Set<RoleId> getRoleIds() { return Collections.unmodifiableSet(roleIds); }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public int getFailedLoginAttempts() { return failedLoginAttempts; }
    public Instant getLockedAt() { return lockedAt; }
    public String getLockedReason() { return lockedReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public String getDeletedBy() { return deletedBy; }
    public String getDeleteReason() { return deleteReason; }
    public boolean isDeleted() { return status == UserStatus.DELETED; }
    public boolean isActive() { return status.isActive(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(id, user.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "User{" +
               "id=" + id +
               ", tenantId=" + tenantId +
               ", username='" + username + '\'' +
               ", email='" + email + '\'' +
               ", status=" + status +
               '}';
    }
}