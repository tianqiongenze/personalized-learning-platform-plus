package com.platform.course.interfaces.rest.dto;

import java.util.List;

/**
 * 分页响应包装(与既有 query.PageResult 同构, 便于前端统一解析)。
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
