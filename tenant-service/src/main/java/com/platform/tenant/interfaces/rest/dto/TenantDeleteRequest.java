package com.platform.tenant.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 删除租户请求
 */
public record TenantDeleteRequest(
    @NotBlank String reason
) {}