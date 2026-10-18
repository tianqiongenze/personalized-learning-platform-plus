package com.platform.iam.interfaces.rest.dto;

import com.platform.iam.domain.model.valueobjects.Permission;
import jakarta.validation.constraints.*;
import java.util.Set;

/**
 * 用户登录请求
 */
public record UserStatisticsResponse(
    long total,
    long active,
    long pending,
    long suspended,
    long locked,
    long expired,
    long deleted
) {}

/**
 * 角色统计响应
 */
