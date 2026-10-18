package com.platform.course.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 课程服务安全链。
 *
 * 修复(真实缺陷): 模块引入了 spring-boot-starter-security 但缺失 SecurityFilterChain,
 * Spring Boot 默认装配 HTTP Basic 认证, 导致 /api/v1/courses 等全部业务端点返回 401
 * (压测中 30% 请求失败即由此产生, 与性能无关)。
 *
 * 平台鉴权模型: 认证由 API 网关统一完成, 集群内部 REST 调用信任 X-Tenant-Id / X-User-* 头,
 * 服务内仅需无状态放行 + 关闭 CSRF(纯 REST, 无 Cookie 会话)。
 */
@Configuration
@EnableWebSecurity
public class CourseSecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
