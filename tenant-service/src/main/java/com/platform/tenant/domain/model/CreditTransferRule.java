package com.platform.tenant.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * 学分互认规则领域模型
 * 定义源租户课程学分向目标租户折算的业务规则
 */
public class CreditTransferRule {

    private final String id;
    private final String sourceTenantId;
    private final String targetTenantId;
    private final String sourceCourseCode;
    private final String targetCourseCode;
    private final BigDecimal creditRatio;
    private final Integer minScore;
    private String status;
    private Long version;

    private CreditTransferRule(String id, String sourceTenantId, String targetTenantId,
                               String sourceCourseCode, String targetCourseCode,
                               BigDecimal creditRatio, Integer minScore,
                               String status, Long version) {
        this.id = Objects.requireNonNull(id, "规则ID不能为空");
        this.sourceTenantId = Objects.requireNonNull(sourceTenantId, "源租户ID不能为空");
        this.targetTenantId = Objects.requireNonNull(targetTenantId, "目标租户ID不能为空");
        this.sourceCourseCode = validateCourseCode(sourceCourseCode);
        this.targetCourseCode = validateCourseCode(targetCourseCode);
        this.creditRatio = validateRatio(creditRatio);
        this.minScore = validateMinScore(minScore);
        this.status = status != null ? status : "ACTIVE";
        this.version = version != null ? version : 0L;
    }

    private String validateCourseCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("课程编码不能为空");
        }
        String trimmed = code.trim();
        if (trimmed.length() > 100) {
            throw new IllegalArgumentException("课程编码不能超过100字符");
        }
        return trimmed;
    }

    private BigDecimal validateRatio(BigDecimal ratio) {
        if (ratio == null) {
            return BigDecimal.ONE;
        }
        if (ratio.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("学分折算比例必须大于0");
        }
        return ratio;
    }

    private Integer validateMinScore(Integer minScore) {
        if (minScore == null) {
            return 60;
        }
        if (minScore < 0 || minScore > 100) {
            throw new IllegalArgumentException("最低分数必须在0-100之间");
        }
        return minScore;
    }

    /**
     * 创建新规则（工厂方法）
     */
    public static CreditTransferRule create(String sourceTenantId, String targetTenantId,
                                            String sourceCourseCode, String targetCourseCode,
                                            BigDecimal creditRatio, Integer minScore) {
        return new CreditTransferRule(
            java.util.UUID.randomUUID().toString(),
            sourceTenantId, targetTenantId,
            sourceCourseCode, targetCourseCode,
            creditRatio, minScore, "ACTIVE", 0L
        );
    }

    /**
     * 从持久化重建（工厂方法）
     */
    public static CreditTransferRule reconstruct(String id, String sourceTenantId, String targetTenantId,
                                                 String sourceCourseCode, String targetCourseCode,
                                                 BigDecimal creditRatio, Integer minScore,
                                                 String status, Long version) {
        return new CreditTransferRule(id, sourceTenantId, targetTenantId,
            sourceCourseCode, targetCourseCode, creditRatio, minScore, status, version);
    }

    /**
     * 判断成绩是否满足互认条件（ACTIVE 且 score >= min_score）
     */
    public boolean evaluate(int score) {
        return "ACTIVE".equals(this.status) && score >= this.minScore;
    }

    /**
     * 按折算比例计算授予学分（score 不达标时返回 0）
     */
    public BigDecimal grantedCredits(int score) {
        if (!evaluate(score)) {
            return BigDecimal.ZERO;
        }
        return creditRatio.multiply(BigDecimal.valueOf(score))
            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    public boolean matches(String tenantId, String courseCode) {
        return this.sourceTenantId.equals(tenantId)
            && this.sourceCourseCode.equals(courseCode);
    }

    public void disable() {
        this.status = "DISABLED";
    }

    // ========== Getters ==========

    public String getId() { return id; }
    public String getSourceTenantId() { return sourceTenantId; }
    public String getTargetTenantId() { return targetTenantId; }
    public String getSourceCourseCode() { return sourceCourseCode; }
    public String getTargetCourseCode() { return targetCourseCode; }
    public BigDecimal getCreditRatio() { return creditRatio; }
    public Integer getMinScore() { return minScore; }
    public String getStatus() { return status; }
    public Long getVersion() { return version; }
}
