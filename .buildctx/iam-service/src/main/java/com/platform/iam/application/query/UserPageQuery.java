package com.platform.iam.application.query;

import com.platform.iam.domain.model.User;
import com.platform.iam.domain.model.Role;
import com.platform.iam.domain.model.valueobjects.*;
import java.util.List;
import java.util.Set;

/**
 * 用户查询服务接口
 */
public record UserPageQuery(
    TenantId tenantId,
    int page,
    int size,
    String status,
    String keyword,
    String sortBy,
    String sortOrder
) {
    public UserPageQuery {
        page = Math.max(0, page);
        size = Math.min(Math.max(1, size), 100);
        sortBy = sortBy != null ? sortBy : "createdAt";
        sortOrder = sortOrder != null ? sortOrder : "desc";
    }
}
