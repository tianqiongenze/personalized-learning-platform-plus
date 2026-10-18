package com.platform.tenant.application.command;

import com.platform.tenant.domain.model.valueobjects.Branding;
import com.platform.tenant.domain.model.valueobjects.Quota;
import com.platform.tenant.domain.model.valueobjects.TenantConfig;
import com.platform.tenant.domain.model.valueobjects.TenantId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * 创建租户命令
 */
public record CreateTenantCommand(
    @NotBlank @Size(min = 3, max = 50) String code,
    @NotBlank @Size(max = 200) String name,
    @Size(max = 1000) String description,
    @NotNull Quota quota,
    @NotNull Branding branding,
    @NotNull TenantConfig config,
    @NotBlank String adminUserId
) {}

/**
 * 更新租户基本信息命令
 */
