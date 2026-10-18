package com.platform.iam.application.command;

import com.platform.iam.domain.model.valueobjects.*;
import jakarta.validation.constraints.*;
import java.util.Set;

/**
 * 创建用户命令
 */
public record ChangePasswordCommand(
    @NotNull UserId userId,
    @NotBlank @Size(min = 8, max = 100) String oldPassword,
    @NotBlank @Size(min = 8, max = 100) String newPassword,
    @NotBlank String changedBy
) {}

/**
 * 重置密码命令（管理员）
 */
