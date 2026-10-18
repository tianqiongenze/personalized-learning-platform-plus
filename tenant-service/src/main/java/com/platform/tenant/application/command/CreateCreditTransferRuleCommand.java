package com.platform.tenant.application.command;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 创建学分互认规则命令
 */
public record CreateCreditTransferRuleCommand(
    @NotBlank String sourceTenantId,
    @NotBlank String targetTenantId,
    @NotBlank @Size(max = 100) String sourceCourseCode,
    @NotBlank @Size(max = 100) String targetCourseCode,
    @NotNull @DecimalMin("0.01") @DecimalMax("999.99") BigDecimal creditRatio,
    @NotNull @Min(0) @Max(100) Integer minScore
) {}
