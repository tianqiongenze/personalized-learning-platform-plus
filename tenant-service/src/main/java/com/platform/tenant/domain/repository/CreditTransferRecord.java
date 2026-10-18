package com.platform.tenant.domain.repository;

/**
 * 学分互认记录领域模型（不可变快照）
 */
public record CreditTransferRecord(
    String id,
    String studentId,
    String ruleId,
    Integer sourceScore,
    java.math.BigDecimal grantedCredits,
    java.time.Instant appliedAt
) {
    public static CreditTransferRecord of(String studentId, String ruleId,
                                          Integer sourceScore, java.math.BigDecimal grantedCredits) {
        return new CreditTransferRecord(
            java.util.UUID.randomUUID().toString(),
            studentId, ruleId, sourceScore, grantedCredits,
            java.time.Instant.now()
        );
    }
}
