package com.platform.common.web;

import jakarta.servlet.Filter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 全局异常处理 + 链路追踪自动装配。
 * 对所有 Java 服务生效(可用 app.error-handler.enabled=false 关闭)。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({Filter.class, OncePerRequestFilter.class})
@ConditionalOnProperty(prefix = "app.error-handler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WebErrorAutoConfiguration {

    @Bean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    /**
     * 入口侧补齐 traceId: 网关未透传 X-Request-Id / traceparent 时生成, 并写入 MDC。
     * 使 ERROR 日志与 Loki/Jaeger/Grafana 可关联 —— 修复"重构前实时日志无关联 ID"缺陷。
     */
    @Bean
    public OncePerRequestFilter traceContextFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest request,
                                            jakarta.servlet.http.HttpServletResponse response,
                                            jakarta.servlet.FilterChain chain)
                    throws jakarta.servlet.ServletException, java.io.IOException {
                String tid = request.getHeader("X-Request-Id");
                if (tid == null || tid.isBlank()) {
                    tid = request.getHeader("traceparent");
                }
                if (tid == null || tid.isBlank()) {
                    tid = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
                }
                MDCBridge.set("traceId", tid);
                response.setHeader("X-Request-Id", tid);
                try {
                    chain.doFilter(request, response);
                } finally {
                    MDCBridge.remove("traceId");
                }
            }
        };
    }

    /** 便于单元测试替换 MDC 实现 */
    static final class MDCBridge {
        static void set(String k, String v) {
            org.slf4j.MDC.put(k, v);
        }

        static void remove(String k) {
            org.slf4j.MDC.remove(k);
        }
    }
}
