package com.platform.iam.application.command;

import com.platform.iam.domain.model.valueobjects.*;
import jakarta.validation.constraints.*;
import java.util.Set;

/**
 * 创建用户命令
 */
public record CreateRoleCommand(
    @NotNull TenantId tenantId,
    @NotBlank @Size(min = 2, max = 50) String code,
    @NotBlank @Size(max = 100) String name,
    @Size(max = 500) String description,
    boolean isSystem,
    Set<Permission> permissions
) {}

/**
 * 更新角色信息命令
 */
