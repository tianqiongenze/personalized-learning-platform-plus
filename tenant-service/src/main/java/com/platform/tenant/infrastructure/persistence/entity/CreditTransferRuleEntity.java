package com.platform.tenant.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * 学分互认规则 JPA 实体
 * 对应数据库表 credit_transfer_rules
 */
@Entity
@Table(name = "credit_transfer_rules", indexes = {
    @Index(name = "idx_ctr_source", columnList = "source_tenant_id, source_course_code"),
    @Index(name = "idx_ctr_target", columnList = "target_tenant_id"),
    @Index(name = "idx_ctr_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreditTransferRuleEntity {

    @Id
    @Column(name = "id", columnDefinition = "char(36)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID id;

    @Column(name = "source_tenant_id", columnDefinition = "char(36)", nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID sourceTenantId;

    @Column(name = "target_tenant_id", columnDefinition = "char(36)", nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID targetTenantId;

    @Column(name = "source_course_code", length = 100, nullable = false)
    private String sourceCourseCode;

    @Column(name = "target_course_code", length = 100, nullable = false)
    private String targetCourseCode;

    @Column(name = "credit_ratio", nullable = false, precision = 5, scale = 2)
    private BigDecimal creditRatio;

    @Column(name = "min_score", nullable = false)
    private Integer minScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private CreditTransferRuleStatus status;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum CreditTransferRuleStatus { ACTIVE, DISABLED }
}
