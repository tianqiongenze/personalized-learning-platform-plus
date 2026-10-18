package com.platform.tenant.application.query;

import com.platform.tenant.domain.model.Tenant;
import com.platform.tenant.domain.model.valueobjects.TenantId;
import java.util.List;

/**
 * 租户查询服务接口
 * CQRS 模式下的读模型查询
 */
public record ConfigDto(
    java.util.Map<String, Boolean> features,
    java.util.Map<String, java.util.Map<String, String>> integrations,
    java.util.Map<String, Object> limits,
    java.util.Map<String, String> metadata
) {}

/**
 * 分页结果
 */
