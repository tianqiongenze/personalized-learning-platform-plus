package com.platform.tenant.interfaces.rest.dto;

import com.platform.tenant.domain.model.valueobjects.Branding;
import jakarta.validation.constraints.NotNull;

/**
 * 更新租户品牌定制请求
 */
public record TenantUpdateBrandingRequest(
    @NotNull Branding branding
) {}