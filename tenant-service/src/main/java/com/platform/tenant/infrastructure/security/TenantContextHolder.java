package com.platform.tenant.infrastructure.security;

import java.util.Optional;

/**
 * 租户上下文持有器
 * 使用 ThreadLocal 存储当前请求的租户信息
 */
public final class TenantContextHolder {

    private static final ThreadLocal<TenantContext> CONTEXT = new ThreadLocal<>();

    private TenantContextHolder() {}

    public static void setContext(TenantContext context) {
        CONTEXT.set(context);
    }

    public static Optional<TenantContext> getContext() {
        return Optional.ofNullable(CONTEXT.get());
    }

    public static String getTenantId() {
        return getContext().map(TenantContext::tenantId).orElse(null);
    }

    public static String getTenantCode() {
        return getContext().map(TenantContext::tenantCode).orElse(null);
    }

    public static String getUserId() {
        return getContext().map(TenantContext::userId).orElse(null);
    }

    public static void clear() {
        CONTEXT.remove();
    }

    public record TenantContext(
        String tenantId,
        String tenantCode,
        String userId,
        String[] roles
    ) {}
}