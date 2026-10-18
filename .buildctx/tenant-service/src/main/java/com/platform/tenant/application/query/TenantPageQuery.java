package com.platform.tenant.application.query;

import com.platform.tenant.domain.model.Tenant;
import com.platform.tenant.domain.model.valueobjects.TenantId;
import java.util.List;

/**
 * 租户查询服务接口
 * CQRS 模式下的读模型查询
 */
public record TenantPageQuery(
    int page,
    int size,
    String status,
    String keyword,
    String sortBy,
    String sortOrder
) {
    public TenantPageQuery {
        page = Math.max(0, page);
        size = Math.min(Math.max(1, size), 100);
        sortBy = sortBy != null ? sortBy : "createdAt";
        sortOrder = sortOrder != null ? sortOrder : "desc";
    }
}
