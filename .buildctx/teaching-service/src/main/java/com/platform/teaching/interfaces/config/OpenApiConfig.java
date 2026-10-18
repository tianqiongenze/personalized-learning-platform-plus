package com.platform.teaching.interfaces.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI/Swagger 配置
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.servlet.context-path:/api/v1}")
    private String contextPath;

    @Value("${server.port:8084}")
    private String serverPort;

    @Bean
    public OpenAPI teachingServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("教学互动服务 API")
                        .description("个性化学习平台 Plus - 教学互动服务\n\n提供学习路径、作业、考试、讨论、直播等教学互动功能")
                        .version("1.0.0")
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0.html")))
                .servers(List.of(
                        new Server().url("http://localhost:" + serverPort + contextPath).description("本地开发环境"),
                        new Server().url("https://api.platform.example.com" + contextPath).description("生产环境")
                ))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT 认证令牌")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}