package com.platform.tenant.interfaces.rest.dto;

import com.platform.tenant.domain.model.valueobjects.Quota;
import jakarta.validation.constraints.NotNull;

/**
 * 更新租户配额请求
 */
public record TenantUpdateQuotaRequest(
    @NotNull Quota quota
) {}