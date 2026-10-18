package com.platform.iam.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 删除角色请求
 */
public record DeleteRoleRequest(
    @NotBlank String reason
) {}
