package com.platform.iam.application.command;

import com.platform.iam.domain.model.valueobjects.*;
import jakarta.validation.constraints.*;
import java.util.Set;

/**
 * 创建用户命令
 */
public record AddRolePermissionCommand(
    @NotNull RoleId roleId,
    @NotNull Permission permission,
    @NotBlank String addedBy
) {}

/**
 * 移除角色权限命令
 */
