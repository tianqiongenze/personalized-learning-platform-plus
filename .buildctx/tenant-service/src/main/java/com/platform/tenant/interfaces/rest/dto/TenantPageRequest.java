package com.platform.tenant.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 租户分页查询请求
 */
public record TenantPageRequest(
    int page,
    int size,
    String status,
    String keyword,
    String sortBy,
    String sortOrder
) {
    public TenantPageRequest {
        page = Math.max(0, page);
        size = Math.min(Math.max(1, size), 100);
        sortBy = sortBy != null ? sortBy : "createdAt";
        sortOrder = sortOrder != null ? sortOrder : "desc";
    }
}