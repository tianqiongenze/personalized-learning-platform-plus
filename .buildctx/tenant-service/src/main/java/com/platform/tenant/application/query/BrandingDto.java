package com.platform.tenant.application.query;

import com.platform.tenant.domain.model.Tenant;
import com.platform.tenant.domain.model.valueobjects.TenantId;
import java.util.List;

/**
 * 租户查询服务接口
 * CQRS 模式下的读模型查询
 */
public record BrandingDto(
    String logoUrl,
    String primaryColor,
    String secondaryColor,
    String faviconUrl,
    String customDomain,
    String emailTemplate,
    String loginPageTemplate,
    java.util.Map<String, String> customCssVariables
) {}

/**
 * 配置DTO
 */
