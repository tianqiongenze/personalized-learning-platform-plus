package com.platform.iam.application.command;

import com.platform.iam.domain.model.valueobjects.*;
import jakarta.validation.constraints.*;
import java.util.Set;

/**
 * 创建用户命令
 */
public record UpdateUsernameCommand(
    @NotNull UserId userId,
    @NotBlank @Size(min = 3, max = 50) String username,
    @NotBlank String updatedBy
) {}

/**
 * 修改密码命令
 */
