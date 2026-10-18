package com.platform.tenant.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 更新租户管理员请求
 */
public record TenantUpdateAdminRequest(
    @NotBlank String adminUserId
) {}