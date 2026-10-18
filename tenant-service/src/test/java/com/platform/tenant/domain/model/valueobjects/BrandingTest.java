package com.platform.tenant.domain.model.valueobjects;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Branding 值对象测试")
class BrandingTest {

    @Nested
    @DisplayName("创建品牌定制")
    class CreateBranding {

        @Test
        @DisplayName("应该成功创建有效品牌定制")
        void shouldCreateValidBranding() {
            Branding branding = Branding.of(
                "https://example.com/logo.png",
                "#1890ff",
                "#52c41a",
                "https://example.com/favicon.ico",
                "custom.example.com",
                "邮件模板内容",
                "登录页模板内容",
                Map.of("--custom-color", "#ff0000")
            );
            
            assertThat(branding.getLogoUrl()).isEqualTo("https://example.com/logo.png");
            assertThat(branding.getPrimaryColor()).isEqualTo("#1890ff");
            assertThat(branding.getSecondaryColor()).isEqualTo("#52c41a");
            assertThat(branding.getFaviconUrl()).isEqualTo("https://example.com/favicon.ico");
            assertThat(branding.getCustomDomain()).isEqualTo("custom.example.com");
            assertThat(branding.getEmailTemplate()).isEqualTo("邮件模板内容");
            assertThat(branding.getLoginPageTemplate()).isEqualTo("登录页模板内容");
            assertThat(branding.getCustomCssVariables()).containsEntry("--custom-color", "#ff0000");
        }

        @Test
        @DisplayName("默认品牌定制应正确")
        void shouldHaveCorrectDefaultBranding() {
            Branding defaultBranding = Branding.defaultBranding();
            
            assertThat(defaultBranding.getLogoUrl()).isNull();
            assertThat(defaultBranding.getPrimaryColor()).isEqualTo("#1890ff");
            assertThat(defaultBranding.getSecondaryColor()).isEqualTo("#52c41a");
            assertThat(defaultBranding.getFaviconUrl()).isNull();
            assertThat(defaultBranding.getCustomDomain()).isNull();
            assertThat(defaultBranding.getCustomCssVariables()).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {"#1890ff", "#FF0000", "#abcdef", "#ABCDEF", "#000000", "#FFFFFF"})
        @DisplayName("有效颜色值应被接受")
        void shouldAcceptValidColors(String color) {
            Branding branding = Branding.of(null, color, "#52c41a", null, null, null, null, null);
            assertThat(branding.getPrimaryColor()).isEqualTo(color.toLowerCase());
        }

        @ParameterizedTest
        @ValueSource(strings = {"1890ff", "#1890f", "#1890fg", "red", "blue", "", "  "})
        @DisplayName("无效颜色值应抛出异常")
        void shouldRejectInvalidColors(String color) {
            assertThatThrownBy(() -> Branding.of(null, color, "#52c41a", null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("主色调必须是有效的十六进制颜色值");
        }

        @ParameterizedTest
        @ValueSource(strings = {"custom.example.com", "sub.domain.example.com", "example.com", "a-b.example.com"})
        @DisplayName("有效域名应被接受")
        void shouldAcceptValidDomains(String domain) {
            Branding branding = Branding.of(null, "#1890ff", "#52c41a", null, domain, null, null, null);
            assertThat(branding.getCustomDomain()).isEqualTo(domain);
        }

        @ParameterizedTest
        @ValueSource(strings = {"-example.com", "example-.com", "example..com", "example.com/", "http://example.com", "exa mple.com"})
        @DisplayName("无效域名应抛出异常")
        void shouldRejectInvalidDomains(String domain) {
            assertThatThrownBy(() -> Branding.of(null, "#1890ff", "#52c41a", null, domain, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("自定义域名格式无效");
        }

        @Test
        @DisplayName("大写域名应被规范化为小写并接受")
        void shouldNormalizeUppercaseDomain() {
            Branding branding = Branding.of(null, "#1890ff", "#52c41a", null, "EXAMPLE.COM", null, null, null);
            assertThat(branding.getCustomDomain()).isEqualTo("example.com");
        }

        @Test
        @DisplayName("空域名应返回null")
        void shouldReturnNullForEmptyDomain() {
            Branding branding = Branding.of(null, "#1890ff", "#52c41a", null, "", null, null, null);
            assertThat(branding.getCustomDomain()).isNull();
            
            Branding branding2 = Branding.of(null, "#1890ff", "#52c41a", null, "  ", null, null, null);
            assertThat(branding2.getCustomDomain()).isNull();
        }
    }

    @Nested
    @DisplayName("With 方法")
    class WithMethods {

        @Test
        @DisplayName("withPrimaryColor应返回新实例")
        void shouldReturnNewInstanceWithPrimaryColor() {
            Branding original = Branding.defaultBranding();
            Branding modified = original.withPrimaryColor("#ff0000");
            
            assertThat(modified.getPrimaryColor()).isEqualTo("#ff0000");
            assertThat(original.getPrimaryColor()).isEqualTo("#1890ff");
            assertThat(modified).isNotSameAs(original);
        }

        @Test
        @DisplayName("withCustomDomain应返回新实例")
        void shouldReturnNewInstanceWithCustomDomain() {
            Branding original = Branding.defaultBranding();
            Branding modified = original.withCustomDomain("new.example.com");
            
            assertThat(modified.getCustomDomain()).isEqualTo("new.example.com");
            assertThat(original.getCustomDomain()).isNull();
        }

        @Test
        @DisplayName("withCustomCssVariables应返回新实例且不可变")
        void shouldReturnNewInstanceWithCustomCssVariables() {
            Branding original = Branding.defaultBranding();
            Map<String, String> vars = new java.util.HashMap<>();
            vars.put("--color", "#ff0000");
            Branding modified = original.withCustomCssVariables(vars);
            
            assertThat(modified.getCustomCssVariables()).containsEntry("--color", "#ff0000");
            assertThat(original.getCustomCssVariables()).isEmpty();
            
            // 修改原map不应影响新实例
            vars.put("--another", "#00ff00");
            assertThat(modified.getCustomCssVariables()).doesNotContainKey("--another");
        }
    }

    @Nested
    @DisplayName("相等性与哈希")
    class EqualityAndHash {

        @Test
        @DisplayName("相同值的品牌定制应相等")
        void shouldBeEqualWhenSameValues() {
            Branding b1 = Branding.of("logo.png", "#1890ff", "#52c41a", "favicon.ico", 
                "custom.com", "email", "login", Map.of("--c", "#fff"));
            Branding b2 = Branding.of("logo.png", "#1890ff", "#52c41a", "favicon.ico", 
                "custom.com", "email", "login", Map.of("--c", "#fff"));
            
            assertThat(b1).isEqualTo(b2);
            assertThat(b1.hashCode()).isEqualTo(b2.hashCode());
        }

        @Test
        @DisplayName("不同值的品牌定制不相等")
        void shouldNotBeEqualWhenDifferentValues() {
            Branding b1 = Branding.defaultBranding();
            Branding b2 = Branding.of(null, "#ff0000", "#52c41a", null, null, null, null, null);
            
            assertThat(b1).isNotEqualTo(b2);
        }
    }
}