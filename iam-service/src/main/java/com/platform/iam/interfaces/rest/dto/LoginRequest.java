package com.platform.iam.interfaces.rest.dto;

import com.platform.iam.domain.model.valueobjects.Permission;
import jakarta.validation.constraints.*;
import java.util.Set;

/**
 * 用户登录请求
 */
public record LoginRequest(
    @NotBlank String username,
    @NotBlank String password,
    String tenantCode
) {}

/**
 * 用户登录响应
 */
