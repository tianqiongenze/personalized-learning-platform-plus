package com.platform.iam.application.command;

import com.platform.iam.domain.model.valueobjects.*;
import jakarta.validation.constraints.*;
import java.util.Set;

/**
 * 创建用户命令
 */
public record AssignRoleCommand(
    @NotNull UserId userId,
    @NotNull RoleId roleId,
    @NotBlank String assignedBy
) {}

/**
 * 撤销角色命令
 */
