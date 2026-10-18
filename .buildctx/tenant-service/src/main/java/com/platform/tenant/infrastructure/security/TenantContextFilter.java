package com.platform.tenant.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 租户上下文过滤器
 * 从请求头中提取租户信息并设置入 ThreadLocal
 */
@Component
public class TenantContextFilter extends OncePerRequestFilter {

    private static final String TENANT_ID_HEADER = "X-Tenant-ID";
    private static final String TENANT_CODE_HEADER = "X-Tenant-Code";
    private static final String USER_ID_HEADER = "X-User-ID";
    private static final String USER_ROLES_HEADER = "X-User-Roles";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            String tenantId = request.getHeader(TENANT_ID_HEADER);
            String tenantCode = request.getHeader(TENANT_CODE_HEADER);
            String userId = request.getHeader(USER_ID_HEADER);
            String rolesHeader = request.getHeader(USER_ROLES_HEADER);

            if (tenantId != null || tenantCode != null) {
                String[] roles = rolesHeader != null ? rolesHeader.split(",") : new String[0];
                TenantContextHolder.setContext(new TenantContextHolder.TenantContext(
                    tenantId, tenantCode, userId, roles
                ));
            }

            filterChain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
        }
    }
}