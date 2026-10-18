package com.platform.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * GraphQL 统一 API 网关
 *
 * 定位: 前端(App/小程序/PC)唯一 API 入口; 各域服务保留 REST 供内部调用。
 * 架构: Schema-first(7 域) + Resolver 委托 RestClient 调域服务 + DataLoader 防 N+1。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class GraphQLGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GraphQLGatewayApplication.class, args);
    }
}
