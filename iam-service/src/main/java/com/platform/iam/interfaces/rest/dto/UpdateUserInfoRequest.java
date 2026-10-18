package com.platform.iam.interfaces.rest.dto;

import com.platform.iam.domain.model.valueobjects.Permission;
import jakarta.validation.constraints.*;
import java.util.Set;

/**
 * 用户登录请求
 */
public record UpdateUserInfoRequest(
    @NotBlank @Email String email,
    @Size(max = 20) String phone
) {}

/**
 * 修改密码请求
 */
