package com.platform.common.tenant;

/**
 * 租户上下文(ThreadLocal)
 * 由 TenantRateLimitFilter / TenantContextFilter 从请求头 X-Tenant-Id 装载,
 * 传递到: 多级缓存 key(隔离各高校缓存)、事件信封、日志 MDC。
 * 务必在请求结束清理, 防止线程池串号。
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    public static final String DEFAULT_TENANT = "public";

    private TenantContext() {}

    public static void set(String tenantId) {
        CURRENT.set(tenantId == null || tenantId.isBlank() ? DEFAULT_TENANT : tenantId);
    }

    public static String get() {
        return CURRENT.get() == null ? DEFAULT_TENANT : CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
