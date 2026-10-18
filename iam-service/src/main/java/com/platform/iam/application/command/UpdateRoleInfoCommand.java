package com.platform.iam.application.command;

import com.platform.iam.domain.model.valueobjects.*;
import jakarta.validation.constraints.*;
import java.util.Set;

/**
 * 创建用户命令
 */
public record UpdateRoleInfoCommand(
    @NotNull RoleId roleId,
    @NotBlank @Size(max = 100) String name,
    @Size(max = 500) String description,
    @NotBlank String updatedBy
) {}

/**
 * 更新角色编码命令
 */
