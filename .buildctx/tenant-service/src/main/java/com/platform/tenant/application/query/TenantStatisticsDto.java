package com.platform.tenant.application.query;

import com.platform.tenant.domain.model.Tenant;
import com.platform.tenant.domain.model.valueobjects.TenantId;
import java.util.List;

/**
 * 租户查询服务接口
 * CQRS 模式下的读模型查询
 */
public record TenantStatisticsDto(
    long total,
    long active,
    long suspended,
    long expired,
    long locked,
    long deleted,
    long draft
) {}
