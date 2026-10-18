package com.platform.iam.application.command;

import com.platform.iam.domain.model.valueobjects.*;
import jakarta.validation.constraints.*;
import java.util.Set;

/**
 * 创建用户命令
 */
public record UpdateUserInfoCommand(
    @NotNull UserId userId,
    @NotBlank @Email String email,
    @Size(max = 20) String phone,
    @NotBlank String updatedBy
) {}

/**
 * 更新用户名命令
 */
