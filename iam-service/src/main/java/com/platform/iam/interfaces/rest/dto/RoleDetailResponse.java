package com.platform.iam.interfaces.rest.dto;

import com.platform.iam.domain.model.valueobjects.Permission;
import jakarta.validation.constraints.*;
import java.util.Set;

/**
 * 用户登录请求
 */
public record RoleDetailResponse(
    String id,
    String tenantId,
    String code,
    String name,
    String description,
    String status,
    boolean isSystem,
    Set<PermissionDto> permissions,
    java.time.Instant createdAt,
    java.time.Instant updatedAt,
    java.time.Instant deletedAt,
    String deletedBy,
    String deleteReason
) {}

/**
 * 分页响应
 */
