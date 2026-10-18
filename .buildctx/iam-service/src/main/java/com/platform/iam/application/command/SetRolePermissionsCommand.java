package com.platform.iam.application.command;

import com.platform.iam.domain.model.valueobjects.*;
import jakarta.validation.constraints.*;
import java.util.Set;

/**
 * 创建用户命令
 */
public record SetRolePermissionsCommand(
    @NotNull RoleId roleId,
    @NotNull Set<Permission> permissions,
    @NotBlank String updatedBy
) {}

/**
 * 添加角色权限命令
 */
