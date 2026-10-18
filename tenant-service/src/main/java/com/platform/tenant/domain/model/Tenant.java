package com.platform.tenant.domain.model;

import com.platform.tenant.domain.event.*;
import com.platform.tenant.domain.model.valueobjects.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 租户聚合根
 * 核心业务实体，封装租户生命周期管理的所有业务规则
 */
public class Tenant {
    private final TenantId id;
    private String code;
    private String name;
    private String description;
    private TenantStatus status;
    private Quota quota;
    private Branding branding;
    private TenantConfig config;
    private String adminUserId;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;
    private String deletedBy;
    private String deleteReason;
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    // 构造函数 - 仅工厂方法使用
    private Tenant(TenantId id, String code, String name, String description,
                   TenantStatus status, Quota quota, Branding branding,
                   TenantConfig config, String adminUserId, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "租户ID不能为空");
        this.code = validateCode(code);
        this.name = validateName(name);
        this.description = description;
        this.status = status != null ? status : TenantStatus.DRAFT;
        this.quota = quota != null ? quota : Quota.defaultQuota();
        this.branding = branding != null ? branding : Branding.defaultBranding();
        this.config = config != null ? config : TenantConfig.defaultConfig();
        this.adminUserId = adminUserId;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = this.createdAt;
    }

    private String validateCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("租户编码不能为空");
        }
        String trimmed = code.trim().toLowerCase();
        if (!trimmed.matches("^[a-z0-9][a-z0-9-]{1,48}[a-z0-9]$")) {
            throw new IllegalArgumentException("租户编码必须为3-50位小写字母数字或连字符，且首尾不能为连字符");
        }
        return trimmed;
    }

    private String validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("租户名称不能为空");
        }
        String trimmed = name.trim();
        if (trimmed.length() > 200) {
            throw new IllegalArgumentException("租户名称不能超过200字符");
        }
        return trimmed;
    }

    /**
     * 创建新租户（工厂方法）
     */
    public static Tenant create(String code, String name, String description,
                                Quota quota, Branding branding, TenantConfig config,
                                String adminUserId) {
        Tenant tenant = new Tenant(
            TenantId.generate(), code, name, description,
            TenantStatus.DRAFT, quota, branding, config, adminUserId, Instant.now()
        );
        tenant.addDomainEvent(new TenantCreatedEvent(tenant.id, tenant.code, tenant.name, adminUserId));
        return tenant;
    }

    /**
     * 从持久化重建租户
     */
    public static Tenant reconstruct(TenantId id, String code, String name, String description,
                                     TenantStatus status, Quota quota, Branding branding,
                                     TenantConfig config, String adminUserId,
                                     Instant createdAt, Instant updatedAt,
                                     Instant deletedAt, String deletedBy, String deleteReason) {
        Tenant tenant = new Tenant(id, code, name, description, status, quota, branding, config, adminUserId, createdAt);
        tenant.updatedAt = updatedAt;
        tenant.deletedAt = deletedAt;
        tenant.deletedBy = deletedBy;
        tenant.deleteReason = deleteReason;
        return tenant;
    }

    // ========== 业务方法 ==========

    /**
     * 激活租户（从草稿态）
     */
    public void activate(String activatedBy) {
        status = status.transitionTo(TenantStatus.ACTIVE);
        updatedAt = Instant.now();
        addDomainEvent(new TenantStatusChangedEvent(id, TenantStatus.DRAFT.name(), TenantStatus.ACTIVE.name(), 
            "租户激活", activatedBy));
    }

    /**
     * 暂停租户
     */
    public void suspend(String reason, String suspendedBy) {
        status = status.transitionTo(TenantStatus.SUSPENDED);
        updatedAt = Instant.now();
        addDomainEvent(new TenantStatusChangedEvent(id, status.name(), TenantStatus.SUSPENDED.name(), 
            reason, suspendedBy));
    }

    /**
     * 恢复租户（从暂停/过期/锁定/删除态）
     */
    public void restore(String restoredBy) {
        String fromStatus = status.name();
        status = status.transitionTo(TenantStatus.ACTIVE);
        updatedAt = Instant.now();
        deletedAt = null;
        deletedBy = null;
        deleteReason = null;
        addDomainEvent(new TenantStatusChangedEvent(id, fromStatus, TenantStatus.ACTIVE.name(), 
            "租户恢复", restoredBy));
        addDomainEvent(new TenantRestoredEvent(id, restoredBy));
    }

    /**
     * 过期租户
     */
    public void expire(String expiredBy) {
        status = status.transitionTo(TenantStatus.EXPIRED);
        updatedAt = Instant.now();
        addDomainEvent(new TenantStatusChangedEvent(id, status.name(), TenantStatus.EXPIRED.name(), 
            "订阅过期", expiredBy));
    }

    /**
     * 锁定租户
     */
    public void lock(String reason, String lockedBy) {
        status = status.transitionTo(TenantStatus.LOCKED);
        updatedAt = Instant.now();
        addDomainEvent(new TenantStatusChangedEvent(id, status.name(), TenantStatus.LOCKED.name(), 
            reason, lockedBy));
    }

    /**
     * 软删除租户
     */
    public void delete(String deletedBy, String reason) {
        if (status == TenantStatus.DELETED) {
            throw new IllegalStateException("租户已被删除");
        }
        status = status.transitionTo(TenantStatus.DELETED);
        deletedAt = Instant.now();
        this.deletedBy = deletedBy;   // 修复: 原为自赋值 `deletedBy = deletedBy;`(空操作),
                                       // 导致软删除后删除人字段永远为 null, 审计链断裂。
        this.deleteReason = reason;
        updatedAt = Instant.now();
        addDomainEvent(new TenantDeletedEvent(id, deletedBy, reason));
    }

    /**
     * 更新基本信息
     */
    public void updateInfo(String name, String description, String updatedBy) {
        String oldName = this.name;
        this.name = validateName(name);
        this.description = description = description != null ? description.trim() : null;
        this.description = description;
        updatedAt = Instant.now();
        String changedFields = "name" + (oldName.equals(name) ? "" : "(changed)") + ", description";
        addDomainEvent(new TenantUpdatedEvent(id, changedFields, updatedBy));
    }

    /**
     * 更新编码（仅草稿态允许）
     */
    public void updateCode(String code, String updatedBy) {
        if (status != TenantStatus.DRAFT) {
            throw new IllegalStateException("仅草稿态租户允许修改编码");
        }
        this.code = validateCode(code);
        updatedAt = Instant.now();
        addDomainEvent(new TenantUpdatedEvent(id, "code", updatedBy));
    }

    /**
     * 更新配额
     */
    public void updateQuota(Quota newQuota, String updatedBy) {
        String oldQuotaStr = this.quota.toString();
        this.quota = Objects.requireNonNull(newQuota, "配额不能为空");
        updatedAt = Instant.now();
        addDomainEvent(new TenantQuotaChangedEvent(id, oldQuotaStr, newQuota.toString(), updatedBy));
    }

    /**
     * 更新品牌定制
     */
    public void updateBranding(Branding newBranding, String updatedBy) {
        this.branding = Objects.requireNonNull(newBranding, "品牌定制不能为空");
        updatedAt = Instant.now();
        addDomainEvent(new TenantBrandingChangedEvent(id, updatedBy));
    }

    /**
     * 更新配置
     */
    public void updateConfig(TenantConfig newConfig, String updatedBy) {
        this.config = Objects.requireNonNull(newConfig, "配置不能为空");
        updatedAt = Instant.now();
        addDomainEvent(new TenantUpdatedEvent(id, "config", updatedBy));
    }

    /**
     * 更新管理员
     */
    public void updateAdminUser(String adminUserId, String updatedBy) {
        this.adminUserId = adminUserId;
        updatedAt = Instant.now();
        addDomainEvent(new TenantUpdatedEvent(id, "adminUserId", updatedBy));
    }

    /**
     * 检查是否可以分配用户
     */
    public boolean canAllocateUser(int currentUsers) {
        return status.isActive() && quota.canAllocateUser(currentUsers);
    }

    /**
     * 检查是否可以分配课程
     */
    public boolean canAllocateCourse(int currentCourses) {
        return status.isActive() && quota.canAllocateCourse(currentCourses);
    }

    /**
     * 检查存储是否超限
     */
    public boolean isStorageExceeded(long usedBytes) {
        return quota.isStorageExceeded(usedBytes);
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

    public TenantId getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public TenantStatus getStatus() { return status; }
    public Quota getQuota() { return quota; }
    public Branding getBranding() { return branding; }
    public TenantConfig getConfig() { return config; }
    public String getAdminUserId() { return adminUserId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public String getDeletedBy() { return deletedBy; }
    public String getDeleteReason() { return deleteReason; }
    public boolean isDeleted() { return status == TenantStatus.DELETED; }
    public boolean isActive() { return status.isActive(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Tenant tenant = (Tenant) o;
        return Objects.equals(id, tenant.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Tenant{" +
               "id=" + id +
               ", code='" + code + '\'' +
               ", name='" + name + '\'' +
               ", status=" + status +
               ", quota=" + quota +
               '}';
    }
}