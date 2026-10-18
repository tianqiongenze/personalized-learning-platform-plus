package com.platform.tenant.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 锁定租户请求
 */
public record TenantLockRequest(
    @NotBlank String reason
) {}