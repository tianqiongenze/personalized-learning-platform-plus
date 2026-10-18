package com.platform.tenant.interfaces.rest.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 创建学分互认规则请求
 */
public record CreateCreditTransferRuleRequest(
    @NotBlank String sourceTenantId,
    @NotBlank String targetTenantId,
    @NotBlank @Size(max = 100) String sourceCourseCode,
    @NotBlank @Size(max = 100) String targetCourseCode,
    @NotNull BigDecimal creditRatio,
    @NotNull @Min(0) @Max(100) Integer minScore
) {}
