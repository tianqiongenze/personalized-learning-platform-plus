package com.platform.tenant.interfaces.rest.dto;

import com.platform.tenant.domain.model.valueobjects.Branding;
import com.platform.tenant.domain.model.valueobjects.Quota;
import com.platform.tenant.domain.model.valueobjects.TenantConfig;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 租户列表响应
 */
public record TenantListResponse(
    String id,
    String code,
    String name,
    String status,
    String plan,
    int currentUsers,
    int maxUsers,
    String createdAt
) {}