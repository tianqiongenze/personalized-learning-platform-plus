package com.platform.tenant.interfaces.rest.assembler;

import com.platform.tenant.application.query.*;
import com.platform.tenant.domain.model.Tenant;
import com.platform.tenant.domain.model.valueobjects.*;
import com.platform.tenant.interfaces.rest.dto.*;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 租户DTO组装器
 * 领域模型与接口DTO之间的转换
 */
@Component
public class TenantDtoAssembler {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_INSTANT;

    public TenantDetailResponse toDetailResponse(Tenant tenant) {
        return new TenantDetailResponse(
            tenant.getId().toString(),
            tenant.getCode(),
            tenant.getName(),
            tenant.getDescription(),
            tenant.getStatus().name(),
            toQuotaDto(tenant.getQuota()),
            toBrandingDto(tenant.getBranding()),
            toConfigDto(tenant.getConfig()),
            tenant.getAdminUserId(),
            formatInstant(tenant.getCreatedAt()),
            formatInstant(tenant.getUpdatedAt()),
            formatInstant(tenant.getDeletedAt()),
            tenant.getDeletedBy(),
            tenant.getDeleteReason()
        );
    }

    public TenantListResponse toListResponse(Tenant tenant) {
        return new TenantListResponse(
            tenant.getId().toString(),
            tenant.getCode(),
            tenant.getName(),
            tenant.getStatus().name(),
            tenant.getConfig().getFeatures().getOrDefault("plan", false) ? "PROFESSIONAL" : "STANDARD",
            0, // 实际应从用户服务获取
            tenant.getQuota().getMaxUsers(),
            formatInstant(tenant.getCreatedAt())
        );
    }

    public TenantSimpleResponse toSimpleResponse(Tenant tenant) {
        return new TenantSimpleResponse(
            tenant.getId().toString(),
            tenant.getCode(),
            tenant.getName()
        );
    }

    public PageResponse<TenantListResponse> toPageResponse(PageResult<TenantListDto> pageResult) {
        List<TenantListResponse> content = pageResult.content().stream()
            .map(this::toListResponseFromDto)
            .collect(Collectors.toList());

        return new PageResponse<>(
            content,
            pageResult.page(),
            pageResult.size(),
            pageResult.totalElements(),
            pageResult.totalPages(),
            pageResult.first(),
            pageResult.last()
        );
    }

    public TenantStatisticsResponse toStatisticsResponse(TenantStatisticsDto stats) {
        return new TenantStatisticsResponse(
            stats.total(),
            stats.active(),
            stats.suspended(),
            stats.expired(),
            stats.locked(),
            stats.deleted(),
            stats.draft()
        );
    }

    public com.platform.tenant.application.command.CreateTenantCommand toCommand(TenantCreateRequest request) {
        return new com.platform.tenant.application.command.CreateTenantCommand(
            request.code(), request.name(), request.description(),
            request.quota(), request.branding(), request.config(), request.adminUserId());
    }

    /** 应用层返回 TenantDetailDto -> REST 响应 */
    public TenantDetailResponse toDetailResponse(com.platform.tenant.application.query.TenantDetailDto dto) {
        return new TenantDetailResponse(
            dto.id(), dto.code(), dto.name(), dto.description(), dto.status(),
            dto.quota(), dto.branding(), dto.config(),
            dto.adminUserId(), dto.createdAt(), dto.updatedAt(), dto.deletedAt(), dto.deletedBy(), dto.deleteReason());
    }

    private Branding toBrandingFromDto(com.platform.tenant.application.query.BrandingDto b) {
        return b == null ? Branding.defaultBranding() : Branding.of(
            b.logoUrl(), b.primaryColor(), b.secondaryColor(), b.faviconUrl(),
            b.customDomain(), b.emailTemplate(), b.loginPageTemplate(), b.customCssVariables());
    }

    private TenantConfig toConfigFromDto(com.platform.tenant.application.query.ConfigDto c) {
        return c == null ? TenantConfig.defaultConfig() : TenantConfig.of(
            c.features(), c.integrations(), c.limits(), c.metadata());
    }

    public TenantSimpleResponse toSimpleResponse(com.platform.tenant.application.query.TenantSimpleDto dto) {
        return new TenantSimpleResponse(dto.id(), dto.code(), dto.name());
    }

    public Tenant toDomain(TenantCreateRequest request) {
        return Tenant.create(
            request.code(),
            request.name(),
            request.description(),
            request.quota(),
            request.branding(),
            request.config(),
            request.adminUserId()
        );
    }

    private TenantListResponse toListResponseFromDto(TenantListDto dto) {
        return new TenantListResponse(
            dto.id(),
            dto.code(),
            dto.name(),
            dto.status(),
            dto.plan(),
            dto.currentUsers(),
            dto.maxUsers(),
            dto.createdAt()
        );
    }

    private QuotaDto toQuotaDto(Quota quota) {
        return new QuotaDto(
            quota.getMaxUsers(),
            quota.getMaxCourses(),
            quota.getMaxStorageBytes(),
            quota.getMaxApiCallsPerMinute(),
            quota.getMaxConcurrentSessions(),
            quota.getMaxKnowledgePoints(),
            quota.getMaxResourcesPerCourse()
        );
    }

    private BrandingDto toBrandingDto(Branding branding) {
        return new BrandingDto(
            branding.getLogoUrl(),
            branding.getPrimaryColor(),
            branding.getSecondaryColor(),
            branding.getFaviconUrl(),
            branding.getCustomDomain(),
            branding.getEmailTemplate(),
            branding.getLoginPageTemplate(),
            branding.getCustomCssVariables()
        );
    }

    private ConfigDto toConfigDto(TenantConfig config) {
        return new ConfigDto(
            config.getFeatures(),
            config.getIntegrations(),
            config.getLimits(),
            config.getMetadata()
        );
    }

    private String formatInstant(java.time.Instant instant) {
        return instant != null ? FORMATTER.format(instant) : null;
    }
}