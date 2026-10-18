package com.platform.iam.interfaces.rest.dto;

import com.platform.iam.domain.model.valueobjects.Permission;
import jakarta.validation.constraints.*;
import java.util.Set;

/**
 * 用户登录请求
 */
public record CreateUserRequest(
    @NotBlank @Size(min = 3, max = 50) String username,
    @NotBlank @Email String email,
    @Size(max = 20) String phone,
    @NotBlank @Size(min = 8, max = 100) String password,
    Set<String> roleCodes
) {}

/**
 * 更新用户信息请求
 */
