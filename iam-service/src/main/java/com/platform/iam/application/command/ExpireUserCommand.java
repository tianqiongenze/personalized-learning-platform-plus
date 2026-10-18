package com.platform.iam.application.command;

import com.platform.iam.domain.model.valueobjects.*;
import jakarta.validation.constraints.*;
import java.util.Set;

/**
 * 创建用户命令
 */
public record ExpireUserCommand(
    @NotNull UserId userId,
    @NotBlank String expiredBy
) {}

/**
 * 锁定用户命令
 */
