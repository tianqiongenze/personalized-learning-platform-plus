package com.platform.iam.interfaces.rest.dto;

import com.platform.iam.domain.model.valueobjects.Permission;
import jakarta.validation.constraints.*;
import java.util.Set;

/**
 * 用户登录请求
 */
public record RoleListResponse(
    String id,
    String tenantId,
    String code,
    String name,
    String description,
    String status,
    boolean isSystem,
    int permissionCount,
    java.time.Instant createdAt
) {}

/**
 * 角色详情响应
 */
