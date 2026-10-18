package com.platform.iam.interfaces.rest.dto;

import com.platform.iam.domain.model.valueobjects.Permission;
import jakarta.validation.constraints.*;
import java.util.Set;

/**
 * 用户登录请求
 */
public record CreateRoleRequest(
    @NotBlank @Size(min = 2, max = 50) String code,
    @NotBlank @Size(max = 100) String name,
    @Size(max = 500) String description,
    boolean isSystem,
    Set<PermissionDto> permissions
) {}

/**
 * 更新角色信息请求
 */
