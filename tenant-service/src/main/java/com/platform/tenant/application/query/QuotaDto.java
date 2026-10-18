package com.platform.tenant.application.query;

import com.platform.tenant.domain.model.Tenant;
import com.platform.tenant.domain.model.valueobjects.TenantId;
import java.util.List;

/**
 * 租户查询服务接口
 * CQRS 模式下的读模型查询
 */
public record QuotaDto(
    int maxUsers,
    int maxCourses,
    long maxStorageBytes,
    int maxApiCallsPerMinute,
    int maxConcurrentSessions,
    int maxKnowledgePoints,
    int maxResourcesPerCourse
) {}

/**
 * 品牌定制DTO
 */
