package com.platform.iam.application.command;

import com.platform.iam.domain.model.valueobjects.*;
import jakarta.validation.constraints.*;
import java.util.Set;

/**
 * 创建用户命令
 */
public record DeleteRoleCommand(
    @NotNull RoleId roleId,
    @NotBlank String deletedBy,
    @NotBlank String reason
) {}
