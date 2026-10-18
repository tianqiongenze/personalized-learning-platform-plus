package com.platform.tenant.interfaces.rest.dto;

import java.math.BigDecimal;

/**
 * 学分互认规则响应
 */
public record CreditTransferRuleResponse(
    String id,
    String sourceTenantId,
    String targetTenantId,
    String sourceCourseCode,
    String targetCourseCode,
    BigDecimal creditRatio,
    Integer minScore,
    String status,
    Long version
) {}
