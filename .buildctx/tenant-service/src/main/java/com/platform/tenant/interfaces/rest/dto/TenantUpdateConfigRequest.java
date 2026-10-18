package com.platform.tenant.interfaces.rest.dto;

import com.platform.tenant.domain.model.valueobjects.TenantConfig;
import jakarta.validation.constraints.NotNull;

/**
 * 更新租户配置请求
 */
public record TenantUpdateConfigRequest(
    @NotNull TenantConfig config
) {}