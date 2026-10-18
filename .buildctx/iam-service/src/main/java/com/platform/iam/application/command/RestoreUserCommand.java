package com.platform.iam.application.command;

import com.platform.iam.domain.model.valueobjects.*;
import jakarta.validation.constraints.*;
import java.util.Set;

/**
 * 创建用户命令
 */
public record RestoreUserCommand(
    @NotNull UserId userId,
    @NotBlank String restoredBy
) {}

/**
 * 过期用户命令
 */
