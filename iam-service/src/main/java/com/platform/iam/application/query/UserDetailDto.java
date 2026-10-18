package com.platform.iam.application.query;

import com.platform.iam.domain.model.User;
import com.platform.iam.domain.model.Role;
import com.platform.iam.domain.model.valueobjects.*;
import java.util.List;
import java.util.Set;

/**
 * 用户查询服务接口
 */
public record UserDetailDto(
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
 * 用户简要DTO
 */
