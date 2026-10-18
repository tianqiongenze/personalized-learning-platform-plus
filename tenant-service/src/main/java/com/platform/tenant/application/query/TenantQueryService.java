package com.platform.tenant.application.query;

import org.springframework.cache.annotation.Cacheable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.tenant.domain.model.valueobjects.TenantId;
import com.platform.tenant.domain.model.valueobjects.TenantStatus;
import com.platform.tenant.infrastructure.persistence.entity.TenantEntity;
import com.platform.tenant.infrastructure.persistence.repository.SpringDataTenantRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 租户查询服务(CQRS 读侧) — 直接基于 Spring Data 投影, 不加载聚合根。
 */
@Service
@Transactional(readOnly = true)
public class TenantQueryService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SpringDataTenantRepository repo;

    public TenantQueryService(SpringDataTenantRepository repo) {
        this.repo = repo;
    }

    @Cacheable(cacheManager = "multiLevelCacheManager", cacheNames = "tenantById", key = "#id.value.toString()", unless = "#result == null")
    public TenantDetailDto findById(TenantId id) {
        return repo.findById(id.getValue()).map(TenantQueryService::toDetail).orElse(null);
    }

    @Cacheable(cacheManager = "multiLevelCacheManager", cacheNames = "tenantByCode", key = "#code", unless = "#result == null")
    public TenantDetailDto findByCode(String code) {
        return repo.findByCode(code).map(TenantQueryService::toDetail).orElse(null);
    }

    public TenantDetailDto findByAdminUserId(String adminUserId) {
        return repo.findByAdminUserId(adminUserId).map(TenantQueryService::toDetail).orElse(null);
    }

    public PageResult<TenantListDto> findPage(TenantPageQuery query) {
        Page<TenantEntity> page = repo.findAll(PageRequest.of(
                Math.max(query.page(), 0), Math.max(query.size(), 1),
                Sort.by(Sort.Direction.fromOptionalString(query.sortOrder()).orElse(Sort.Direction.DESC),
                        query.sortBy() == null || query.sortBy().isBlank() ? "createdAt" : query.sortBy())));
        List<TenantListDto> content = page.getContent().stream()
                .map(TenantQueryService::toList).collect(Collectors.toList());
        return new PageResult<>(content, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast());
    }

    @Cacheable(cacheManager = "multiLevelCacheManager", cacheNames = "tenantAllActive", unless = "#result == null")
    public List<TenantSimpleDto> findAllActive() {
        return repo.findByStatus(TenantStatus.ACTIVE).stream()
                .map(t -> new TenantSimpleDto(t.getId().toString(), t.getCode(), t.getName()))
                .collect(Collectors.toList());
    }

    public TenantStatisticsDto statistics() {
        long total = repo.count();
        long active = repo.countByStatus(TenantStatus.ACTIVE);
        long suspended = repo.countByStatus(TenantStatus.SUSPENDED);
        long locked = repo.countByStatus(TenantStatus.LOCKED);
        long expired = repo.countByStatus(TenantStatus.EXPIRED);
        long draft = repo.countByStatus(TenantStatus.DRAFT);
        return new TenantStatisticsDto(total, active, suspended, expired, locked, 0, draft);
    }

    // ================= 映射 =================

    private static TenantDetailDto toDetail(TenantEntity t) {
        return new TenantDetailDto(t.getId().toString(), t.getCode(), t.getName(), t.getDescription(),
                t.getStatus() == null ? null : t.getStatus().name(),
                toQuota(t.getQuotaJson()), toBranding(t.getBrandingJson()), toConfig(t.getConfigJson()),
                t.getAdminUserId(),
                str(t.getCreatedAt()), str(t.getUpdatedAt()), str(t.getDeletedAt()),
                t.getDeletedBy(), t.getDeleteReason());
    }

    private static QuotaDto toQuota(String json) {
        try {
            JsonNode q = MAPPER.readTree(json == null || json.isBlank() ? "{}" : json);
            return new QuotaDto(q.path("maxUsers").asInt(0), q.path("maxCourses").asInt(0),
                    q.path("maxStorageBytes").asLong(0), q.path("maxApiCallsPerMinute").asInt(0),
                    q.path("maxConcurrentSessions").asInt(0), q.path("maxKnowledgePoints").asInt(0),
                    q.path("maxResourcesPerCourse").asInt(0));
        } catch (Exception e) {
            return new QuotaDto(0, 0, 0, 0, 0, 0, 0);
        }
    }

    private static BrandingDto toBranding(String json) {
        try {
            JsonNode b = MAPPER.readTree(json == null || json.isBlank() ? "{}" : json);
            java.util.Map<String, String> css = new java.util.HashMap<>();
            b.path("customCssVariables").fields().forEachRemaining(f -> css.put(f.getKey(), f.getValue().asText()));
            return new BrandingDto(textOrNull(b, "logoUrl"), textOrNull(b, "primaryColor"),
                    textOrNull(b, "secondaryColor"), textOrNull(b, "faviconUrl"),
                    textOrNull(b, "customDomain"), textOrNull(b, "emailTemplate"),
                    textOrNull(b, "loginPageTemplate"), css);
        } catch (Exception e) {
            return new BrandingDto(null, null, null, null, null, null, null, java.util.Map.of());
        }
    }

    private static ConfigDto toConfig(String json) {
        try {
            JsonNode c = MAPPER.readTree(json == null || json.isBlank() ? "{}" : json);
            java.util.Map<String, Boolean> features = new java.util.HashMap<>();
            c.path("features").fields().forEachRemaining(f -> features.put(f.getKey(), f.getValue().asBoolean()));
            java.util.Map<String, java.util.Map<String, String>> integrations = new java.util.HashMap<>();
            c.path("integrations").fields().forEachRemaining(f -> {
                java.util.Map<String, String> m = new java.util.HashMap<>();
                f.getValue().fields().forEachRemaining(x -> m.put(x.getKey(), x.getValue().asText()));
                integrations.put(f.getKey(), m);
            });
            java.util.Map<String, Object> limits = new java.util.HashMap<>();
            c.path("limits").fields().forEachRemaining(f -> limits.put(f.getKey(), f.getValue()));
            java.util.Map<String, String> metadata = new java.util.HashMap<>();
            c.path("metadata").fields().forEachRemaining(f -> metadata.put(f.getKey(), f.getValue().asText()));
            return new ConfigDto(features, integrations, limits, metadata);
        } catch (Exception e) {
            return new ConfigDto(java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of());
        }
    }

    private static String textOrNull(JsonNode n, String field) {
        return n.hasNonNull(field) ? n.get(field).asText() : null;
    }

    private static TenantListDto toList(TenantEntity t) {
        int maxUsers = 0;
        try {
            JsonNode q = MAPPER.readTree(t.getQuotaJson() == null ? "{}" : t.getQuotaJson());
            maxUsers = q.path("maxUsers").asInt(0);
        } catch (Exception ignored) { }
        return new TenantListDto(t.getId().toString(), t.getCode(), t.getName(),
                t.getStatus() == null ? null : t.getStatus().name(),
                "STANDARD", 0, maxUsers, str(t.getCreatedAt()));
    }

    private static String str(java.time.Instant i) {
        return i == null ? null : i.toString();
    }
}
