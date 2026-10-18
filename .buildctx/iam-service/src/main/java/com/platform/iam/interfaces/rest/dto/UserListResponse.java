package com.platform.iam.interfaces.rest.dto;

import com.platform.iam.domain.model.valueobjects.Permission;
import jakarta.validation.constraints.*;
import java.util.Set;

/**
 * 用户登录请求
 */
public record UserListResponse(
    String id,
    String tenantId,
    String username,
    String email,
    String phone,
    String status,
    java.time.Instant lastLoginAt,
    int failedLoginAttempts,
    java.time.Instant createdAt
) {}

/**
 * 用户详情响应
 */
