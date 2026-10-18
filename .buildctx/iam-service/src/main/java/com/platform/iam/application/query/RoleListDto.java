package com.platform.iam.application.query;

import com.platform.iam.domain.model.User;
import com.platform.iam.domain.model.Role;
import com.platform.iam.domain.model.valueobjects.*;
import java.util.List;
import java.util.Set;

/**
 * 用户查询服务接口
 */
public record RoleListDto(
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
 * 角色详情DTO
 */
