package com.platform.tenant.interfaces.rest.dto;

/**
 * 租户统计响应
 */
public record TenantStatisticsResponse(
    long total,
    long active,
    long suspended,
    long expired,
    long locked,
    long deleted,
    long draft
) {}