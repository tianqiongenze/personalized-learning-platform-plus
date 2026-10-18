package com.platform.tenant.domain.model.valueobjects;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("TenantConfig 值对象测试")
class TenantConfigTest {

    @Nested
    @DisplayName("创建配置")
    class CreateConfig {

        @Test
        @DisplayName("应该成功创建有效配置")
        void shouldCreateValidConfig() {
            TenantConfig config = TenantConfig.of(
                Map.of("feature1", true, "feature2", false),
                Map.of("sso", Map.of("enabled", "true")),
                Map.of("limit1", 100),
                Map.of("meta1", "value1")
            );
            
            assertThat(config.getFeatures()).containsEntry("feature1", true);
            assertThat(config.getFeatures()).containsEntry("feature2", false);
            assertThat(config.getIntegrations()).containsKey("sso");
            assertThat(config.getLimits()).containsEntry("limit1", 100);
            assertThat(config.getMetadata()).containsEntry("meta1", "value1");
        }

        @Test
        @DisplayName("默认配置应正确")
        void shouldHaveCorrectDefaultConfig() {
            TenantConfig defaultConfig = TenantConfig.defaultConfig();
            
            assertThat(defaultConfig.isFeatureEnabled("knowledge_tracing")).isTrue();
            assertThat(defaultConfig.isFeatureEnabled("recommendation")).isTrue();
            assertThat(defaultConfig.isFeatureEnabled("multimodal_analysis")).isTrue();
            assertThat(defaultConfig.isFeatureEnabled("llm_assistant")).isFalse();
            assertThat(defaultConfig.isFeatureEnabled("ab_testing")).isTrue();
            assertThat(defaultConfig.isFeatureEnabled("analytics_dashboard")).isTrue();
            assertThat(defaultConfig.isFeatureEnabled("content_management")).isTrue();
            assertThat(defaultConfig.isFeatureEnabled("collaboration")).isFalse();
            
            assertThat(defaultConfig.getIntegrationConfig("sso", "enabled")).isEqualTo("true");
            assertThat(defaultConfig.getIntegrationConfig("lti", "version")).isEqualTo("1.3");
            
            assertThat(defaultConfig.getLimit("max_file_upload_mb")).isEqualTo(100);
            assertThat(defaultConfig.getLimit("max_concurrent_kt_requests")).isEqualTo(100);
        }

        @Test
        @DisplayName("空值应转为空Map")
        void shouldConvertNullToEmptyMap() {
            TenantConfig config = TenantConfig.of(null, null, null, null);
            
            assertThat(config.getFeatures()).isEmpty();
            assertThat(config.getIntegrations()).isEmpty();
            assertThat(config.getLimits()).isEmpty();
            assertThat(config.getMetadata()).isEmpty();
        }
    }

    @Nested
    @DisplayName("功能开关检查")
    class FeatureCheck {

        @Test
        @DisplayName("isFeatureEnabled应正确返回")
        void shouldReturnCorrectFeatureStatus() {
            TenantConfig config = TenantConfig.defaultConfig();
            
            assertThat(config.isFeatureEnabled("knowledge_tracing")).isTrue();
            assertThat(config.isFeatureEnabled("llm_assistant")).isFalse();
            assertThat(config.isFeatureEnabled("non_existent")).isFalse(); // 不存在默认false
        }
    }

    @Nested
    @DisplayName("集成配置获取")
    class IntegrationConfig {

        @Test
        @DisplayName("getIntegrationConfig应正确返回")
        void shouldReturnCorrectIntegrationConfig() {
            TenantConfig config = TenantConfig.defaultConfig();
            
            assertThat(config.getIntegrationConfig("sso", "enabled")).isEqualTo("true");
            assertThat(config.getIntegrationConfig("lti", "version")).isEqualTo("1.3");
            assertThat(config.getIntegrationConfig("sso", "non_existent")).isNull();
            assertThat(config.getIntegrationConfig("non_existent", "key")).isNull();
        }
    }

    @Nested
    @DisplayName("限制获取")
    class LimitCheck {

        @Test
        @DisplayName("getLimit应正确返回")
        void shouldReturnCorrectLimit() {
            TenantConfig config = TenantConfig.defaultConfig();
            
            assertThat(config.getLimit("max_file_upload_mb")).isEqualTo(100);
            assertThat(config.getLimit("max_concurrent_kt_requests")).isEqualTo(100);
            assertThat(config.getLimit("non_existent")).isNull();
        }
    }

    @Nested
    @DisplayName("With 方法")
    class WithMethods {

        @Test
        @DisplayName("withFeature应返回新实例")
        void shouldReturnNewInstanceWithFeature() {
            TenantConfig original = TenantConfig.defaultConfig();
            TenantConfig modified = original.withFeature("llm_assistant", true);
            
            assertThat(modified.isFeatureEnabled("llm_assistant")).isTrue();
            assertThat(original.isFeatureEnabled("llm_assistant")).isFalse();
            assertThat(modified).isNotSameAs(original);
        }

        @Test
        @DisplayName("withIntegration应返回新实例")
        void shouldReturnNewInstanceWithIntegration() {
            TenantConfig original = TenantConfig.defaultConfig();
            TenantConfig modified = original.withIntegration("new_integration", Map.of("key", "value"));
            
            assertThat(modified.getIntegrationConfig("new_integration", "key")).isEqualTo("value");
            assertThat(original.getIntegrationConfig("new_integration", "key")).isNull();
        }

        @Test
        @DisplayName("withLimit应返回新实例")
        void shouldReturnNewInstanceWithLimit() {
            TenantConfig original = TenantConfig.defaultConfig();
            TenantConfig modified = original.withLimit("new_limit", 200);
            
            assertThat(modified.getLimit("new_limit")).isEqualTo(200);
            assertThat(original.getLimit("new_limit")).isNull();
        }

        @Test
        @DisplayName("withMetadata应返回新实例")
        void shouldReturnNewInstanceWithMetadata() {
            TenantConfig original = TenantConfig.defaultConfig();
            TenantConfig modified = original.withMetadata("new_meta", "value");
            
            assertThat(modified.getMetadata()).containsEntry("new_meta", "value");
            assertThat(original.getMetadata()).doesNotContainKey("new_meta");
        }
    }

    @Nested
    @DisplayName("相等性与哈希")
    class EqualityAndHash {

        @Test
        @DisplayName("相同值的配置应相等")
        void shouldBeEqualWhenSameValues() {
            TenantConfig c1 = TenantConfig.of(
                Map.of("f1", true),
                Map.of("int1", Map.of("k", "v")),
                Map.of("lim1", 100),
                Map.of("meta1", "val1")
            );
            TenantConfig c2 = TenantConfig.of(
                Map.of("f1", true),
                Map.of("int1", Map.of("k", "v")),
                Map.of("lim1", 100),
                Map.of("meta1", "val1")
            );
            
            assertThat(c1).isEqualTo(c2);
            assertThat(c1.hashCode()).isEqualTo(c2.hashCode());
        }

        @Test
        @DisplayName("不同值的配置不相等")
        void shouldNotBeEqualWhenDifferentValues() {
            TenantConfig c1 = TenantConfig.defaultConfig();
            TenantConfig c2 = TenantConfig.of(
                Map.of("f1", true),
                Map.of(), Map.of(), Map.of()
            );
            
            assertThat(c1).isNotEqualTo(c2);
        }
    }
}