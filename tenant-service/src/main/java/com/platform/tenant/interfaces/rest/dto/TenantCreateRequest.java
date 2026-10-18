package com.platform.tenant.interfaces.rest.dto;

import com.platform.tenant.domain.model.valueobjects.Branding;
import com.platform.tenant.domain.model.valueobjects.Quota;
import com.platform.tenant.domain.model.valueobjects.TenantConfig;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 创建租户请求
 */
public record TenantCreateRequest(
    @NotBlank @Size(min = 3, max = 50) String code,
    @NotBlank @Size(max = 200) String name,
    @Size(max = 1000) String description,
    @NotNull Quota quota,
    @NotNull Branding branding,
    @NotNull TenantConfig config,
    @NotBlank String adminUserId
) {}