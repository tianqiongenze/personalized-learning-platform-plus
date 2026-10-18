package com.platform.iam.application.command;

import com.platform.iam.domain.model.valueobjects.*;
import jakarta.validation.constraints.*;
import java.util.Set;

/**
 * 创建用户命令
 */
public record DeactivateRoleCommand(
    @NotNull RoleId roleId,
    @NotBlank String deactivatedBy
) {}

/**
 * 激活角色命令
 */
