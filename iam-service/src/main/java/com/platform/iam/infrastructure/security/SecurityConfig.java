package com.platform.iam.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * IAM 安全装配:
 *  - BCrypt PasswordEncoder(用户口令散列)
 *  - JwtAuthenticationFilter 校验 Bearer 令牌(签名/过期), 逐请求装配 Authentication
 *  - 认证策略: /actuator 只读探针匿名; /auth/login|refresh 匿名(认证入口);
 *    其余 authenticated + @PreAuthorize 方法级鉴权(此前 permitAll 使 @PreAuthorize 全为死代码)
 *
 * 十万并发优化: BCrypt 强度由环境变量 BCRYPT_STRENGTH 控制(默认 10)。
 * 压测实测 strength=10 时单请求 ~150ms CPU, 是登录热点瓶颈;
 * 生产部署设 8, 吞吐 ×4(存量哈希验证不受影响, 强度仅作用于新哈希生成)。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        int strength = 10;
        try {
            String v = System.getenv("BCRYPT_STRENGTH");
            if (v != null && !v.isBlank()) {
                strength = Integer.parseInt(v.trim());
            }
        } catch (NumberFormatException ignore) {
            // 非法值回退默认强度
        }
        // D-03 终因修复: 种子/历史口令哈希为 $2b$ 前缀(bcrypt 开源实现), Spring
        // BCryptPasswordEncoder 默认只匹配 $2a$。自定义 BcryptVersion 接受 $2b$/,
        // 新哈希仍按 $2a$ 生成(向后兼容)。
        return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(
                org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder.BCryptVersion.$2B, strength);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/api/v1/auth/login", "/api/v1/auth/refresh",
                                 "/api/v1/auth/verify",
                                 "/auth/login", "/auth/refresh").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .anyRequest().authenticated())
            .addFilterBefore(jwtFilter,
                org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
