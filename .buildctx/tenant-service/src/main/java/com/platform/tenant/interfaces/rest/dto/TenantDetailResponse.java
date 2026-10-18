package com.platform.tenant.interfaces.rest.dto;

import com.platform.tenant.domain.model.valueobjects.Branding;
import com.platform.tenant.domain.model.valueobjects.Quota;
import com.platform.tenant.domain.model.valueobjects.TenantConfig;

/**
 * 租户详情响应
 */
public record TenantDetailResponse(
    String id,
    String code,
    String name,
    String description,
    String status,
    com.platform.tenant.application.query.QuotaDto quota,
    com.platform.tenant.application.query.BrandingDto branding,
    com.platform.tenant.application.query.ConfigDto config,
    String adminUserId,
    String createdAt,
    String updatedAt,
    String deletedAt,
    String deletedBy,
    String deleteReason
) {}