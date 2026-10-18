package com.platform.tenant.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 更新租户基本信息请求
 */
public record TenantUpdateInfoRequest(
    @NotBlank @Size(max = 200) String name,
    @Size(max = 1000) String description
) {}