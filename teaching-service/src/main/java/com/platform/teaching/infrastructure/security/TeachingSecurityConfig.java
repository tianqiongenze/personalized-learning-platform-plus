package com.platform.teaching.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 教学服务安全链。
 *
 * 修复(真实缺陷): 与 course-service 同源问题 —— 引入 spring-boot-starter-security
 * 但未定义 SecurityFilterChain, 默认 HTTP Basic 会拦截全部业务端点(401)。
 *
 * 平台鉴权模型: 网关统一认证, 内部调用信任 X-Tenant-Id / X-User-* 头。
 */
@Configuration
@EnableWebSecurity
public class TeachingSecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
