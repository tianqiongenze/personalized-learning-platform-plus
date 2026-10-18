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
public record UpdateTenantInfoCommand(
    @NotNull TenantId tenantId,
    @NotBlank @Size(max = 200) String name,
    @Size(max = 1000) String description,
    @NotBlank String updatedBy
) {}

/**
 * 更新租户编码命令
 */
