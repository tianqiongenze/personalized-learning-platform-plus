package com.platform.iam.application.query;

import com.platform.iam.domain.model.User;
import com.platform.iam.domain.model.Role;
import com.platform.iam.domain.model.valueobjects.*;
import java.util.List;
import java.util.Set;

/**
 * 用户查询服务接口
 */
public record PageResult<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean first,
    boolean last
) {}

/**
 * 用户统计DTO
 */
