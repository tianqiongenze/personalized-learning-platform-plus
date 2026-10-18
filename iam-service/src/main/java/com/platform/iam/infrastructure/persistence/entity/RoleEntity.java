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
import com.fasterxml.jackson.core.type.TypeReference;
import com.platform.iam.domain.model.valueobjects.Permission;
import java.util.Set;

/**
 * 角色JPA实体
 */
@Entity
@Table(name = "roles", indexes = {
    @Index(name = "idx_roles_tenant_code", columnList = "tenant_id, code", unique = true),
    @Index(name = "idx_roles_status", columnList = "status"),
    @Index(name = "idx_roles_is_system", columnList = "is_system"),
    @Index(name = "idx_roles_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoleEntity {

    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
        new com.fasterxml.jackson.databind.ObjectMapper();

    @Id
    @Column(name = "id", columnDefinition = "char(36)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID id;

    @Column(name = "tenant_id", columnDefinition = "char(36)", nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID tenantId;

    @Column(name = "code", length = 50, nullable = false)
    private String code;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "permissions", columnDefinition = "json")
    private String permissionsJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private RoleStatus status;

    @Column(name = "is_system", nullable = false)
    private boolean isSystem;

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

    public RoleId getRoleId() {
        return id != null ? RoleId.of(id) : null;
    }

    public void setRoleId(RoleId roleId) {
        this.id = roleId != null ? roleId.getValue() : null;
    }

    public TenantId getTenantIdValue() {
        return tenantId != null ? TenantId.of(tenantId) : null;
    }

    public void setTenantIdValue(TenantId tenantId) {
        this.tenantId = tenantId != null ? tenantId.getValue() : null;
    }

    public Set<Permission> getPermissionSet() {
        if (permissionsJson == null || permissionsJson.isEmpty()) return Set.of();
        try {
            return MAPPER.readValue(permissionsJson, new TypeReference<Set<Permission>>() {});
        } catch (Exception ex) {
            return Set.of();
        }
    }

    public void setPermissionSet(Set<Permission> permissions) {
        try {
            this.permissionsJson = MAPPER.writeValueAsString(permissions == null ? Set.of() : permissions);
        } catch (Exception ex) {
            this.permissionsJson = "[]";
        }
    }
}