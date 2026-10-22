package com.platform.tenant.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * D-05 修复: 网关(Nginx)在 `/api/v1/ops/` 路由上经 `auth_request` 校验后注入
 * `X-User-Id` / `X-User-Roles`(逗号分隔) 头; 本过滤器把角色头转换为
 * Spring Security 的 Authentication(ROLE_ 前缀授予), 使 SecurityConfig 的
 * hasAnyRole("PLATFORM_ADMIN"/"OPS_READER") 生效。
 *
 * 信任模型: 与其他服务一致 —— 服务仅可从集群内/网关后访问, 网关负责
 * JWT 校验后再注入头(见 nginx-gateway/gateway.conf 的 auth_request 链)。
 * 头缺失/为空时不设置 Authentication, 后续授权链自然 401。
 */
@Component
public class HeaderAuthFilter extends OncePerRequestFilter {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLES_HEADER = "X-User-Roles";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String userId = request.getHeader(USER_ID_HEADER);
        String rolesHeader = request.getHeader(USER_ROLES_HEADER);

        // 安全: 仅信任网关注入(网关 auth_request 已校验 JWT 并覆盖这些头);
        // NodePort 直连可伪造 X-User-Roles, 必须凭 X-Gateway-Verified 防伪。
        boolean gatewayVerified = "1".equals(request.getHeader("X-Gateway-Verified"));
        if (gatewayVerified && rolesHeader != null && !rolesHeader.isBlank()) {
            List<SimpleGrantedAuthority> authorities = Arrays.stream(rolesHeader.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(r -> new SimpleGrantedAuthority(r.startsWith("ROLE_") ? r : "ROLE_" + r))
                    .collect(Collectors.toList());
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userId != null ? userId : "anonymous", null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
