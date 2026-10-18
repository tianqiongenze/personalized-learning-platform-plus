package com.platform.tenant.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 更新租户编码请求
 */
public record TenantUpdateCodeRequest(
    @NotBlank @Size(min = 3, max = 50) String code
) {}