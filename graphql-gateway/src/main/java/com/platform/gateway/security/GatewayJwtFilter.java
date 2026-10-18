package com.platform.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 网关 JWT 验签过滤器: 与 iam-service 共享 JWT_SECRET(HS256), 校验签名与过期,
 * 组装 Authentication 并将用户上下文透传给下游域服务(X-User-Id/X-Tenant-Id/X-Roles)。
 * 域服务侧默认信任内网透传(trust-internal), 验签只在边缘做一次。
 */
@Component
public class GatewayJwtFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(GatewayJwtFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final SecretKey secretKey;

    public GatewayJwtFilter(@Value("${app.jwt.secret}") String secret) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest request,
                                    jakarta.servlet.http.HttpServletResponse response,
                                    jakarta.servlet.FilterChain filterChain)
            throws jakarta.servlet.ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }
        String token = authHeader.substring(BEARER_PREFIX.length());
        try {
            Claims claims = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
            if (claims.getExpiration() != null && claims.getExpiration().before(new java.util.Date())) {
                filterChain.doFilter(request, response);
                return;
            }
            Object rolesClaim = claims.get("roles");
            List<SimpleGrantedAuthority> authorities = rolesClaim instanceof List<?> list
                    ? list.stream()
                        .filter(String.class::isInstance)
                        .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                        .collect(Collectors.toList())
                    : List.of();
            String userId = claims.getSubject();

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // 用户上下文透传给下游域服务(内部调用信任这些头, 信任边界=网关)
            jakarta.servlet.http.HttpServletRequest wrapped = new jakarta.servlet.http.HttpServletRequestWrapper(request) {
                @Override public String getHeader(String name) {
                    return switch (name) {
                        case "X-User-Id" -> userId;
                        case "X-Tenant-Id" -> claims.get("tenant_id", String.class);
                        case "X-Roles" -> rolesClaim instanceof List<?> rl
                                ? rl.stream().filter(String.class::isInstance).map(String.class::cast)
                                    .collect(Collectors.joining(","))
                                : null;
                        default -> super.getHeader(name);
                    };
                }
            };
            filterChain.doFilter(wrapped, response);
        } catch (io.jsonwebtoken.JwtException | IllegalArgumentException e) {
            // 无效令牌: 清空上下文按匿名放行, 由 SecurityFilterChain 的 authenticated 规则统一 401/403
            SecurityContextHolder.clearContext();
            log.debug("[gateway] 无效 JWT: {}", e.getMessage());
            filterChain.doFilter(request, response);
        }
    }
}
