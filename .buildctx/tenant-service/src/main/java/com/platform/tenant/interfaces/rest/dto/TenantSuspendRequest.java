package com.platform.tenant.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 暂停租户请求
 */
public record TenantSuspendRequest(
    @NotBlank String reason
) {}