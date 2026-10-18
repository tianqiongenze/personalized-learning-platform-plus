package com.platform.iam.interfaces.rest.dto;

import com.platform.iam.domain.model.valueobjects.Permission;
import jakarta.validation.constraints.*;
import java.util.Set;

/**
 * 用户登录请求
 */
public record SetRolePermissionsRequest(
    @NotNull Set<PermissionDto> permissions
) {}

/**
 * 添加角色权限请求
 */
