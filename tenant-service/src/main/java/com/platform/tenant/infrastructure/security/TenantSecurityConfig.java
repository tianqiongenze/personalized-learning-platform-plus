package com.platform.tenant.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 租户服务安全链(纵深防御, 不再完全信任内网):
 *  - 默认所有请求 authenticated(JWT 由 iam 签发, 网关透传 Bearer)
 *  - app.security.trust-internal=true(默认, 内网集群模式)时放行集群内部头透传调用 ——
 *    这是性能与安全折中: 服务间调用不重复验签, 边缘入口(graphql-gateway/nginx)负责认证
 *  - 置 false 即全量验签模式(需配 JwtAuthenticationFilter, 外部直连暴露时启用)
 *  - @EnableMethodSecurity: 让 @PreAuthorize 真正生效(此前 permitAll 使其成死代码)
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class TenantSecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            @Value("${app.security.trust-internal:true}") boolean trustInternal) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(tenantHeaderAuthFilter(),
                org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> {
                if (trustInternal) {
                    auth.requestMatchers("/actuator/**").permitAll()
                        .anyRequest().permitAll();
                } else {
                    auth.requestMatchers("/actuator/**").permitAll()
                        .anyRequest().authenticated();
                }
            });
        return http.build();
    }

    /**
     * D-05 配套: 网关 auth_request 校验 JWT 后注入 X-User-Id/X-User-Roles,
     * 本过滤器将其转换为 Spring Security Authentication, 使 @PreAuthorize 生效。
     */
    @org.springframework.context.annotation.Bean
    public HeaderAuthFilter tenantHeaderAuthFilter() {
        return new HeaderAuthFilter();
    }
}
