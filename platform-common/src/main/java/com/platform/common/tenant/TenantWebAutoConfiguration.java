package com.platform.common.tenant;

import jakarta.servlet.Filter;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * 租户 Web 自动装配(面向"多地区/多高校/1000并发"):
 *  1. TenantFilter: X-Tenant-Id 头 -> TenantContext + MDC, 缓存/事件/日志全链路租户隔离
 *  2. TenantRateLimitFilter: Redis INCR 分钟滑窗, 每租户限流(吵闹邻居防御), 超额 429
 * 选型: 共享集群 + tenantId 逻辑隔离(当前体量最优); namespace-per-tenant 扩展路径见 runbook。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({Filter.class, OncePerRequestFilter.class})
@ConditionalOnProperty(prefix = "app.tenant", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TenantWebAutoConfiguration {

    @Bean
    public OncePerRequestFilter tenantContextFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest request,
                                            jakarta.servlet.http.HttpServletResponse response,
                                            jakarta.servlet.FilterChain chain)
                    throws jakarta.servlet.ServletException, IOException {
                String tenantId = request.getHeader("X-Tenant-Id");
                TenantContext.set(tenantId);
                MDC.put("tenantId", TenantContext.get());
                try {
                    chain.doFilter(request, response);
                } finally {
                    TenantContext.clear();
                    MDC.remove("tenantId");
                }
            }
        };
    }

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    public OncePerRequestFilter tenantRateLimitFilter(
            StringRedisTemplate redis,
            @Value("${app.tenant.default-rate-per-min:1200}") long defaultQuotaPerMin,
            @Value("${app.tenant.burst-allowance:1.5}") double burstAllowance) {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest request,
                                            jakarta.servlet.http.HttpServletResponse response,
                                            jakarta.servlet.FilterChain chain)
                    throws jakarta.servlet.ServletException, IOException {
                String tenant = request.getHeader("X-Tenant-Id");
                if (tenant == null || tenant.isBlank()
                        || request.getRequestURI().contains("/actuator")
                        || request.getRequestURI().contains("/health")) {
                    chain.doFilter(request, response);
                    return;
                }
                String key = "rl:" + tenant + ":" + (System.currentTimeMillis() / 60_000);
                try {
                    Long count = redis.opsForValue().increment(key);
                    if (count != null && count == 1L) {
                        redis.expire(key, Duration.ofSeconds(90));
                    }
                    long limit = Math.round(defaultQuotaPerMin * burstAllowance);
                    if (count != null && count > limit) {
                        response.setStatus(429);
                        response.setHeader("Retry-After", "60");
                        response.setContentType("application/json;charset=UTF-8");
                        response.getWriter().write(
                                "{\"code\":429,\"message\":\"租户请求超出每分钟配额(" + limit + "),请稍后重试\"}");
                        return;
                    }
                } catch (RuntimeException ignored) {
                    // Redis 不可用降级放行, 实例级 resilience4j 兜底
                }
                chain.doFilter(request, response);
            }
        };
    }
}
