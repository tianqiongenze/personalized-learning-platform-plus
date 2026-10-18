package com.platform.tenant.domain.model.valueobjects;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * 租户配置值对象
 * 封装租户功能开关、集成配置、限制设置
 */
public final class TenantConfig {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final Map<String, Boolean> features;
    private final Map<String, Map<String, String>> integrations;
    private final Map<String, Object> limits;
    private final Map<String, String> metadata;

    private TenantConfig(Map<String, Boolean> features,
                         Map<String, Map<String, String>> integrations,
                         Map<String, Object> limits,
                         Map<String, String> metadata) {
        this.features = features != null ? Collections.unmodifiableMap(features) : Collections.emptyMap();
        this.integrations = integrations != null ? Collections.unmodifiableMap(integrations) : Collections.emptyMap();
        this.limits = limits != null ? Collections.unmodifiableMap(limits) : Collections.emptyMap();
        this.metadata = metadata != null ? Collections.unmodifiableMap(metadata) : Collections.emptyMap();
    }

    public static TenantConfig of(Map<String, Boolean> features,
                                  Map<String, Map<String, String>> integrations,
                                  Map<String, Object> limits,
                                  Map<String, String> metadata) {
        return new TenantConfig(features, integrations, limits, metadata);
    }

    /** JSONB 反序列化 Creator(修复: 无 Creator 导致 tenants 创建 500) */
    @com.fasterxml.jackson.annotation.JsonCreator
    public static TenantConfig create(
            @com.fasterxml.jackson.annotation.JsonProperty("features") java.util.Map<String, Boolean> features,
            @com.fasterxml.jackson.annotation.JsonProperty("integrations") java.util.Map<String, java.util.Map<String, String>> integrations,
            @com.fasterxml.jackson.annotation.JsonProperty("limits") java.util.Map<String, Object> limits,
            @com.fasterxml.jackson.annotation.JsonProperty("metadata") java.util.Map<String, String> metadata) {
        return new TenantConfig(
                features == null ? java.util.Collections.emptyMap() : features,
                integrations == null ? java.util.Collections.emptyMap() : integrations,
                limits == null ? java.util.Collections.emptyMap() : limits,
                metadata == null ? java.util.Collections.emptyMap() : metadata);
    }

    public static TenantConfig defaultConfig() {
        return new TenantConfig(
            Map.of(
                "knowledge_tracing", true,
                "recommendation", true,
                "multimodal_analysis", true,
                "llm_assistant", false,
                "ab_testing", true,
                "analytics_dashboard", true,
                "content_management", true,
                "collaboration", false
            ),
            Map.of(
                "sso", Map.of("enabled", "false", "provider", "cas"),
                "lti", Map.of("enabled", "true", "version", "1.3"),
                "xapi", Map.of("enabled", "false")
            ),
            Map.of(
                "max_file_upload_mb", 100,
                "max_concurrent_kt_requests", 100,
                "max_recommendation_batch_size", 50
            ),
            Collections.emptyMap()
        );
    }

    public Map<String, Boolean> getFeatures() { return features; }
    public Map<String, Map<String, String>> getIntegrations() { return integrations; }
    public Map<String, Object> getLimits() { return limits; }
    public Map<String, String> getMetadata() { return metadata; }

    public boolean isFeatureEnabled(String feature) {
        return features.getOrDefault(feature, false);
    }

    public String getIntegrationConfig(String integration, String key) {
        Map<String, String> config = integrations.get(integration);
        return config != null ? config.get(key) : null;
    }

    public Object getLimit(String key) {
        return limits.get(key);
    }

    public TenantConfig withFeature(String feature, boolean enabled) {
        Map<String, Boolean> newFeatures = new java.util.HashMap<>(features);
        newFeatures.put(feature, enabled);
        return new TenantConfig(newFeatures, integrations, limits, metadata);
    }

    public TenantConfig withIntegration(String integration, Map<String, String> config) {
        Map<String, Map<String, String>> newIntegrations = new java.util.HashMap<>(integrations);
        newIntegrations.put(integration, config != null ? Collections.unmodifiableMap(config) : Collections.emptyMap());
        return new TenantConfig(features, newIntegrations, limits, metadata);
    }

    public TenantConfig withLimit(String key, Object value) {
        Map<String, Object> newLimits = new java.util.HashMap<>(limits);
        newLimits.put(key, value);
        return new TenantConfig(features, integrations, newLimits, metadata);
    }

    public TenantConfig withMetadata(String key, String value) {
        Map<String, String> newMetadata = new java.util.HashMap<>(metadata);
        newMetadata.put(key, value);
        return new TenantConfig(features, integrations, limits, newMetadata);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TenantConfig that = (TenantConfig) o;
        return Objects.equals(features, that.features) &&
               Objects.equals(integrations, that.integrations) &&
               Objects.equals(limits, that.limits) &&
               Objects.equals(metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(features, integrations, limits, metadata);
    }

    @Override
    public String toString() {
        return "TenantConfig{" +
               "features=" + features +
               ", integrations=" + integrations +
               ", limits=" + limits +
               ", metadata=" + metadata +
               '}';
    }

    /**
     * 从JSON字符串反序列化为TenantConfig对象
     *
     * @param json JSON字符串
     * @return TenantConfig对象
     */
    public static TenantConfig fromJson(String json) {
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<TenantConfig>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("无效的JSON格式: " + e.getMessage(), e);
        }
    }

    /**
     * 序列化为JSON字符串
     *
     * @return JSON字符串
     */
    public String toJson() {
        try {
            return OBJECT_MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            throw new IllegalStateException("序列化失败: " + e.getMessage(), e);
        }
    }
}