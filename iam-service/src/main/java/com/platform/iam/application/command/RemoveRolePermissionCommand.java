package com.platform.iam.application.command;

import com.platform.iam.domain.model.valueobjects.*;
import jakarta.validation.constraints.*;
import java.util.Set;

/**
 * 创建用户命令
 */
public record RemoveRolePermissionCommand(
    @NotNull RoleId roleId,
    @NotNull Permission permission,
    @NotBlank String removedBy
) {}

/**
 * 停用角色命令
 */
