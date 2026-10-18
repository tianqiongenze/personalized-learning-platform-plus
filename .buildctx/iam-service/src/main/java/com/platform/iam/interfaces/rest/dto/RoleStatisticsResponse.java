package com.platform.iam.interfaces.rest.dto;

import com.platform.iam.domain.model.valueobjects.Permission;
import jakarta.validation.constraints.*;
import java.util.Set;

/**
 * 用户登录请求
 */
public record RoleStatisticsResponse(
    long total,
    long active,
    long inactive,
    long deleted,
    long system
) {}

/**
 * 通用响应包装
 */
