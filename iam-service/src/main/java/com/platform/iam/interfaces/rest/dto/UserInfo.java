package com.platform.iam.interfaces.rest.dto;

import com.platform.iam.domain.model.valueobjects.Permission;
import jakarta.validation.constraints.*;
import java.util.Set;

/**
 * 用户登录请求
 */
public record UserInfo(
    String id,
    String tenantId,
    String username,
    String email,
    String phone,
    String[] roles
) {}

/**
 * 刷新令牌请求
 */
