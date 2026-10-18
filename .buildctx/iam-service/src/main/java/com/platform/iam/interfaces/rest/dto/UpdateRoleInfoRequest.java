package com.platform.iam.interfaces.rest.dto;

import com.platform.iam.domain.model.valueobjects.Permission;
import jakarta.validation.constraints.*;
import java.util.Set;

/**
 * 用户登录请求
 */
public record UpdateRoleInfoRequest(
    @NotBlank @Size(max = 100) String name,
    @Size(max = 500) String description
) {}

/**
 * 设置角色权限请求
 */
