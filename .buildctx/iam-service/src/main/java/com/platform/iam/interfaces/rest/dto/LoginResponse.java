package com.platform.iam.interfaces.rest.dto;

import com.platform.iam.domain.model.valueobjects.Permission;
import jakarta.validation.constraints.*;
import java.util.Set;

/**
 * 用户登录请求
 */
public record LoginResponse(
    String accessToken,
    String refreshToken,
    long expiresIn,
    String tokenType,
    UserInfo user
) {}

/**
 * 用户信息
 */
