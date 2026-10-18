package com.platform.tenant.infrastructure.persistence.entity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.platform.tenant.domain.model.valueobjects.Branding;
import com.platform.tenant.domain.model.valueobjects.Quota;
import com.platform.tenant.domain.model.valueobjects.TenantConfig;
import com.platform.tenant.domain.model.valueobjects.TenantId;
import com.platform.tenant.domain.model.valueobjects.TenantStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * 租户JPA实体
 * 对应数据库表 tenants
 */
@Entity
@Table(name = "tenants", indexes = {
    @Index(name = "idx_tenants_code", columnList = "code", unique = true),
    @Index(name = "idx_tenants_status", columnList = "status"),
    @Index(name = "idx_tenants_admin_user", columnList = "admin_user_id"),
    @Index(name = "idx_tenants_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantEntity {

    @Id
    @Column(name = "id", columnDefinition = "char(36)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID id;

    @Column(name = "code", length = 50, nullable = false, unique = true)
    private String code;

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private TenantStatus status;

    @Column(name = "quota", columnDefinition = "json", nullable = false)
    private String quotaJson;

    @Column(name = "branding", columnDefinition = "json", nullable = false)
    private String brandingJson;

    @Column(name = "config", columnDefinition = "json", nullable = false)
    private String configJson;

    @Column(name = "admin_user_id", length = 100)
    private String adminUserId;

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

    public TenantId getTenantId() {
        return id != null ? TenantId.of(id) : null;
    }

    public void setTenantId(TenantId tenantId) {
        this.id = tenantId != null ? tenantId.getValue() : null;
    }

    public Quota getQuota() {
        return Quota.fromJson(quotaJson);
    }

    public void setQuota(Quota quota) {
        this.quotaJson = quota != null ? quota.toJson() : Quota.defaultQuota().toJson();
    }

    public Branding getBranding() {
        return Branding.fromJson(brandingJson);
    }

    public void setBranding(Branding branding) {
        this.brandingJson = branding != null ? branding.toJson() : Branding.defaultBranding().toJson();
    }

    public TenantConfig getConfig() {
        return TenantConfig.fromJson(configJson);
    }

    public void setConfig(TenantConfig config) {
        this.configJson = config != null ? config.toJson() : TenantConfig.defaultConfig().toJson();
    }
}