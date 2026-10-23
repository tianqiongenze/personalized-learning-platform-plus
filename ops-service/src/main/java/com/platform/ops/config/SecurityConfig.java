package com.platform.ops.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 统一运维平台安全: 仅内网 + ADMIN JWT(由网关注入/校验), actuator 探针放开
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, HeaderAuthFilter headerAuthFilter) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // 探针保持开放(K8s liveness/readiness 无凭证)
                .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                // 修复: 原 anyRequest().permitAll() 使运维端点(含 Redis CONFIG SET、
                //   Pod 日志下载、动态日志级别、Pulsar peek 消息内容)对集群内任何来源
                //   完全开放 —— 违反最小权限原则与等保 2.0 三级"剩余信息保护"。
                //   现改为: 运维写操作需 PLATFORM_ADMIN, 只读观测需 PLATFORM_ADMIN 或
                //   OPS_READER; 角色由网关经 X-User-Roles 注入, 缺失即 403。
                //   注意: server.servlet.context-path=/api/v1/ops, Security 匹配器
                //   相对 context-path, 故此处用 /** 而非 /api/v1/ops/**(后者永不匹配,
                //   落到 anyRequest() 使全部带角色头的请求也 401)。
                .requestMatchers("/actuator/**").hasAnyRole("PLATFORM_ADMIN")
                // iam 实际签发角色为 ADMIN/SUPER_ADMIN(见 /auth/verify X-User-Roles),
                // 接受平台管理角色 ADMIN/SUPER_ADMIN 与设计角色 OPS_READER。
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/**")
                .hasAnyRole("ADMIN", "SUPER_ADMIN", "OPS_READER")
                .anyRequest().hasRole("PLATFORM_ADMIN"));
        http.exceptionHandling(ex -> ex
            .authenticationEntryPoint((req, res, e) -> res.sendError(401, "缺少有效凭证"))
            .accessDeniedHandler((req, res, e) -> res.sendError(403, "权限不足")));
        // D-05 修复: 接线 HeaderAuthFilter, 把网关注入的 X-User-Roles 头转换为
        // Authentication(此前该过滤器缺失, 角色头注入无效, 业务端点全部 401)
        http.addFilterBefore(headerAuthFilter,
            org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
