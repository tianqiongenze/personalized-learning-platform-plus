package com.platform.iam.application.query;

import com.platform.iam.domain.model.User;
import com.platform.iam.domain.model.Role;
import com.platform.iam.domain.model.valueobjects.*;
import java.util.List;
import java.util.Set;

/**
 * 用户查询服务接口
 */
public record UserListDto(
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
 * 用户详情DTO
 */
