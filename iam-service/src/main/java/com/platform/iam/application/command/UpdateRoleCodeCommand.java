package com.platform.iam.application.command;

import com.platform.iam.domain.model.valueobjects.*;
import jakarta.validation.constraints.*;
import java.util.Set;

/**
 * 创建用户命令
 */
public record UpdateRoleCodeCommand(
    @NotNull RoleId roleId,
    @NotBlank @Size(min = 2, max = 50) String code,
    @NotBlank String updatedBy
) {}

/**
 * 设置角色权限命令
 */
