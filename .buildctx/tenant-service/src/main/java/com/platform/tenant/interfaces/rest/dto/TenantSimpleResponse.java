package com.platform.tenant.interfaces.rest.dto;

/**
 * 租户简要响应
 */
public record TenantSimpleResponse(
    String id,
    String code,
    String name
) {}