package com.platform.tenant.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * 学分互认记录 JPA 实体
 * 对应数据库表 credit_transfer_records
 */
@Entity
@Table(name = "credit_transfer_records", indexes = {
    @Index(name = "idx_ctr_records_student", columnList = "student_id"),
    @Index(name = "idx_ctr_records_rule", columnList = "rule_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreditTransferRecordEntity {

    @Id
    @Column(name = "id", columnDefinition = "char(36)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID id;

    @Column(name = "student_id", columnDefinition = "char(36)", nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID studentId;

    @Column(name = "rule_id", columnDefinition = "char(36)", nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID ruleId;

    @Column(name = "source_score", nullable = false)
    private Integer sourceScore;

    @Column(name = "granted_credits", nullable = false, precision = 5, scale = 2)
    private BigDecimal grantedCredits;

    @Column(name = "applied_at", nullable = false)
    private Instant appliedAt;
}
