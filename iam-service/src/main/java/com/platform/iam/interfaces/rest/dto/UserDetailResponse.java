package com.platform.iam.interfaces.rest.dto;

import com.platform.iam.domain.model.valueobjects.Permission;
import jakarta.validation.constraints.*;
import java.util.Set;

/**
 * 用户登录请求
 */
public record UserDetailResponse(
    String id,
    String tenantId,
    String username,
    String email,
    String phone,
    String status,
    Set<String> roleCodes,
    java.time.Instant lastLoginAt,
    int failedLoginAttempts,
    java.time.Instant lockedAt,
    String lockedReason,
    java.time.Instant createdAt,
    java.time.Instant updatedAt,
    java.time.Instant deletedAt,
    String deletedBy,
    String deleteReason
) {}

/**
 * 角色列表响应
 */
