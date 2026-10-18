package com.platform.tenant.application.query;

import com.platform.tenant.domain.model.Tenant;
import com.platform.tenant.domain.model.valueobjects.TenantId;
import java.util.List;

/**
 * 租户查询服务接口
 * CQRS 模式下的读模型查询
 */

/**
 * 租户分页查询参数
 */

public record TenantSimpleDto(
    String id,
    String code,
    String name
) {}

/**
 * 配额DTO
 */
