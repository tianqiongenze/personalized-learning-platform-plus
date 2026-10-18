package com.platform.tenant.application.query;

import com.platform.tenant.domain.model.Tenant;
import com.platform.tenant.domain.model.valueobjects.TenantId;
import java.util.List;

/**
 * 租户查询服务接口
 * CQRS 模式下的读模型查询
 */
public record TenantListDto(
    String id,
    String code,
    String name,
    String status,
    String plan,
    int currentUsers,
    int maxUsers,
    String createdAt
) {}

/**
 * 租户详情DTO
 */
