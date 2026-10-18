package com.platform.tenant.domain.model.valueobjects;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * 品牌定制化值对象
 * 封装租户品牌定制的业务规则
 */
public final class Branding {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final String logoUrl;
    private final String primaryColor;
    private final String secondaryColor;
    private final String faviconUrl;
    private final String customDomain;
    private final String emailTemplate;
    private final String loginPageTemplate;
    private final Map<String, String> customCssVariables;

    private Branding(String logoUrl, String primaryColor, String secondaryColor,
                     String faviconUrl, String customDomain, String emailTemplate,
                     String loginPageTemplate, Map<String, String> customCssVariables) {
        this.logoUrl = logoUrl;
        this.primaryColor = validateColor(primaryColor, "主色调");
        this.secondaryColor = validateColor(secondaryColor, "辅助色");
        this.faviconUrl = faviconUrl;
        this.customDomain = validateDomain(customDomain);
        this.emailTemplate = emailTemplate;
        this.loginPageTemplate = loginPageTemplate;
        this.customCssVariables = customCssVariables != null ? 
            Collections.unmodifiableMap(customCssVariables) : Collections.emptyMap();
    }

    private String validateColor(String color, String fieldName) {
        if (color == null || color.trim().isEmpty()) {
            return "#1890ff"; // 默认蓝色
        }
        String trimmed = color.trim();
        if (!trimmed.matches("^#[0-9a-fA-F]{6}$")) {
            throw new IllegalArgumentException(fieldName + "必须是有效的十六进制颜色值，如 #1890ff");
        }
        return trimmed;
    }

    private String validateDomain(String domain) {
        if (domain == null || domain.trim().isEmpty()) {
            return null;
        }
        String trimmed = domain.trim().toLowerCase();
        if (!trimmed.matches("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)*$")) {
            throw new IllegalArgumentException("自定义域名格式无效");
        }
        return trimmed;
    }

    public static Branding of(String logoUrl, String primaryColor, String secondaryColor,
                              String faviconUrl, String customDomain, String emailTemplate,
                              String loginPageTemplate, Map<String, String> customCssVariables) {
        return new Branding(logoUrl, primaryColor, secondaryColor, faviconUrl,
                           customDomain, emailTemplate, loginPageTemplate, customCssVariables);
    }

    public static Branding defaultBranding() {
        return new Branding(null, "#1890ff", "#52c41a", null, null, null, null, Collections.emptyMap());
    }

    public String getLogoUrl() { return logoUrl; }
    public String getPrimaryColor() { return primaryColor; }
    public String getSecondaryColor() { return secondaryColor; }
    public String getFaviconUrl() { return faviconUrl; }
    public String getCustomDomain() { return customDomain; }
    public String getEmailTemplate() { return emailTemplate; }
    public String getLoginPageTemplate() { return loginPageTemplate; }
    public Map<String, String> getCustomCssVariables() { return customCssVariables; }

    public Branding withLogoUrl(String logoUrl) {
        return new Branding(logoUrl, primaryColor, secondaryColor, faviconUrl, customDomain, emailTemplate, loginPageTemplate, customCssVariables);
    }

    public Branding withPrimaryColor(String primaryColor) {
        return new Branding(logoUrl, primaryColor, secondaryColor, faviconUrl, customDomain, emailTemplate, loginPageTemplate, customCssVariables);
    }

    public Branding withSecondaryColor(String secondaryColor) {
        return new Branding(logoUrl, primaryColor, secondaryColor, faviconUrl, customDomain, emailTemplate, loginPageTemplate, customCssVariables);
    }

    public Branding withCustomDomain(String customDomain) {
        return new Branding(logoUrl, primaryColor, secondaryColor, faviconUrl, customDomain, emailTemplate, loginPageTemplate, customCssVariables);
    }

    public Branding withEmailTemplate(String emailTemplate) {
        return new Branding(logoUrl, primaryColor, secondaryColor, faviconUrl, customDomain, emailTemplate, loginPageTemplate, customCssVariables);
    }

    public Branding withLoginPageTemplate(String loginPageTemplate) {
        return new Branding(logoUrl, primaryColor, secondaryColor, faviconUrl, customDomain, emailTemplate, loginPageTemplate, customCssVariables);
    }

    public Branding withCustomCssVariables(Map<String, String> customCssVariables) {
        return new Branding(logoUrl, primaryColor, secondaryColor, faviconUrl, customDomain, emailTemplate, loginPageTemplate, customCssVariables);
    }

    /**
     * 从JSON字符串反序列化为Branding对象
     *
     * @param json JSON字符串
     * @return Branding对象
     */
    public static Branding fromJson(String json) {
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<Branding>() {});
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Branding branding = (Branding) o;
        return Objects.equals(logoUrl, branding.logoUrl) &&
               Objects.equals(primaryColor, branding.primaryColor) &&
               Objects.equals(secondaryColor, branding.secondaryColor) &&
               Objects.equals(faviconUrl, branding.faviconUrl) &&
               Objects.equals(customDomain, branding.customDomain) &&
               Objects.equals(emailTemplate, branding.emailTemplate) &&
               Objects.equals(loginPageTemplate, branding.loginPageTemplate) &&
               Objects.equals(customCssVariables, branding.customCssVariables);
    }

    @Override
    public int hashCode() {
        return Objects.hash(logoUrl, primaryColor, secondaryColor, faviconUrl, customDomain, emailTemplate, loginPageTemplate, customCssVariables);
    }

    @Override
    public String toString() {
        return "Branding{" +
               "logoUrl='" + logoUrl + '\'' +
               ", primaryColor='" + primaryColor + '\'' +
               ", secondaryColor='" + secondaryColor + '\'' +
               ", faviconUrl='" + faviconUrl + '\'' +
               ", customDomain='" + customDomain + '\'' +
               ", emailTemplate='" + emailTemplate + '\'' +
               ", loginPageTemplate='" + loginPageTemplate + '\'' +
               ", customCssVariables=" + customCssVariables +
               '}';
    }
}