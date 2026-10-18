package com.platform.tenant.interfaces.rest.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 申请学分互认请求
 */
public record ApplyCreditTransferRequest(
    @NotBlank String studentId,
    @NotBlank String sourceTenantId,
    @NotBlank String sourceCourseCode,
    @NotNull @Min(0) @Max(100) Integer score
) {}
