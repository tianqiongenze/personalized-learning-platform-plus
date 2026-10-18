package com.platform.tenant.interfaces.rest.dto;

import java.util.List;

/**
 * 分页响应
 */
public record PageResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean first,
    boolean last
) {}