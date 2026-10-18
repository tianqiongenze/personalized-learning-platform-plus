package com.platform.iam.interfaces.rest.dto;

import com.platform.iam.domain.model.valueobjects.Permission;
import jakarta.validation.constraints.*;
import java.util.Set;

/**
 * 用户登录请求
 */

/**
 * 用户列表响应
 */

public record RefreshTokenRequest(
    @NotBlank String refreshToken
) {}

/**
 * 创建用户请求
 */
