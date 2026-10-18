package com.platform.tenant.application.command;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 申请学分互认命令
 */
public record ApplyCreditTransferCommand(
    @NotBlank String studentId,
    @NotBlank String sourceTenantId,
    @NotBlank String sourceCourseCode,
    @NotNull @Min(0) @Max(100) Integer score
) {}
