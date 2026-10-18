package com.platform.iam.application.command;

import com.platform.iam.domain.model.valueobjects.*;
import jakarta.validation.constraints.*;
import java.util.Set;

/**
 * 创建用户命令
 */
public record ResetPasswordCommand(
    @NotNull UserId userId,
    @NotBlank @Size(min = 8, max = 100) String newPassword,
    @NotBlank String resetBy
) {}

/**
 * 激活用户命令
 */
