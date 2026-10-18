package com.platform.iam.infrastructure.persistence.entity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.platform.iam.domain.model.valueobjects.*;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;
import java.util.Set;

/**
 * 用户JPA实体
 */
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_users_tenant_username", columnList = "tenant_id, username", unique = true),
    @Index(name = "idx_users_tenant_email", columnList = "tenant_id, email", unique = true),
    @Index(name = "idx_users_tenant_phone", columnList = "tenant_id, phone", unique = true),
    @Index(name = "idx_users_status", columnList = "status"),
    @Index(name = "idx_users_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEntity {

    @Id
    @Column(name = "id", columnDefinition = "char(36)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID id;

    @Column(name = "tenant_id", columnDefinition = "char(36)", nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID tenantId;

    @Column(name = "username", length = 50, nullable = false)
    private String username;

    @Column(name = "email", length = 255, nullable = false)
    private String email;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "password_hash", length = 255, nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private UserStatus status;

    @Column(name = "role_ids", columnDefinition = "json")
    @Convert(converter = com.platform.iam.infrastructure.persistence.UuidArrayJsonConverter.class)
    private UUID[] roleIds;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "locked_reason", length = 500)
    private String lockedReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by", length = 100)
    private String deletedBy;

    @Column(name = "delete_reason", length = 500)
    private String deleteReason;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    // ========== 转换方法 ==========

    public UserId getUserId() {
        return id != null ? UserId.of(id) : null;
    }

    public void setUserId(UserId userId) {
        this.id = userId != null ? userId.getValue() : null;
    }

    public TenantId getTenantIdValue() {
        return tenantId != null ? TenantId.of(tenantId) : null;
    }

    public void setTenantIdValue(TenantId tenantId) {
        this.tenantId = tenantId != null ? tenantId.getValue() : null;
    }

    public Set<RoleId> getRoleIdSet() {
        if (roleIds == null) return Set.of();
        return java.util.Arrays.stream(roleIds)
            .map(RoleId::of)
            .collect(java.util.stream.Collectors.toSet());
    }

    public void setRoleIdSet(Set<RoleId> roleIds) {
        this.roleIds = roleIds != null ? 
            roleIds.stream().map(RoleId::getValue).toArray(UUID[]::new) : 
            new UUID[0];
    }
}