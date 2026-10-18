package com.platform.gateway.config;

import com.platform.gateway.security.GatewayJwtFilter;
import graphql.scalars.ExtendedScalars;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * GraphQL 运行时装配: JSON/Long 标量 + 安全链。
 * 边缘网关是认证的第一道门(此前 permitAll 全放行, JWT 校验形同虚设):
 *  - /graphiql 匿名(调试); 其余请求经 GatewayJwtFilter 验签后放行
 *  - app.security.enabled=false 可整体关闭(仅本地开发), 默认开启
 *  - 域服务(iam/tenant)信任内网透传, 验签在本网关统一完成, 不重复
 */
@Configuration
public class GraphQLConfig {

    @Bean
    public RuntimeWiringConfigurer runtimeWiringConfigurer() {
        return wiring -> wiring
                .scalar(ExtendedScalars.Json)
                .scalar(ExtendedScalars.GraphQLLong);
    }

    @Configuration
    @EnableWebSecurity
    static class SecurityConfig {
        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http,
                GatewayJwtFilter jwtFilter,
                @Value("${app.security.enabled:true}") boolean securityEnabled) throws Exception {
            http.csrf(csrf -> csrf.disable());
            if (securityEnabled) {
                http.addFilterBefore(jwtFilter,
                        org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
                    .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/graphiql**").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        .anyRequest().authenticated());
            } else {
                http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            }
            return http.build();
        }
    }
}
