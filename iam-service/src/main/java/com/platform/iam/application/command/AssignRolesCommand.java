package com.platform.iam.application.command;

import com.platform.iam.domain.model.valueobjects.*;
import jakarta.validation.constraints.*;
import java.util.Set;

/**
 * 创建用户命令
 */
public record AssignRolesCommand(
    @NotNull UserId userId,
    @NotNull Set<RoleId> roleIds,
    @NotBlank String assignedBy
) {}

/**
 * 创建角色命令
 */
