package com.platform.tenant.application.query;

import com.platform.tenant.domain.model.Tenant;
import com.platform.tenant.domain.model.valueobjects.TenantId;
import java.util.List;

/**
 * 租户查询服务接口
 * CQRS 模式下的读模型查询
 */
public record TenantDetailDto(
    String id,
    String code,
    String name,
    String description,
    String status,
    QuotaDto quota,
    BrandingDto branding,
    ConfigDto config,
    String adminUserId,
    String createdAt,
    String updatedAt,
    String deletedAt,
    String deletedBy,
    String deleteReason
) {}

/**
 * 租户简要DTO
 */
