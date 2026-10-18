package com.platform.iam.interfaces.rest.dto;

import com.platform.iam.domain.model.valueobjects.Permission;
import jakarta.validation.constraints.*;
import java.util.Set;

/**
 * 用户登录请求
 */
public record ChangePasswordRequest(
    @NotBlank @Size(min = 8, max = 100) String oldPassword,
    @NotBlank @Size(min = 8, max = 100) String newPassword
) {}

/**
 * 重置密码请求（管理员）
 */
