package com.platform.iam.application.command;

import com.platform.iam.domain.model.valueobjects.*;
import jakarta.validation.constraints.*;
import java.util.Set;

/**
 * 创建用户命令
 */
public record CreateUserCommand(
    @NotNull TenantId tenantId,
    @NotBlank @Size(min = 3, max = 50) String username,
    @NotBlank @Email String email,
    @Size(max = 20) String phone,
    @NotBlank @Size(min = 8, max = 100) String password,
    Set<RoleId> roleIds
) {}

/**
 * 更新用户基本信息命令
 */
