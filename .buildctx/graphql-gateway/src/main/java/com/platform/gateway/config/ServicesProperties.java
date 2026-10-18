package com.platform.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 各域服务基址配置(services.* in application.yml)
 */
@ConfigurationProperties(prefix = "services")
public record ServicesProperties(String tenant, String iam, String course,
                                 String teaching, String kt, String ai, String ops) {

    public ServicesProperties {
        if (tenant == null) tenant = "http://tenant-service:8081/api/v1";
        if (iam == null) iam = "http://iam-service:8082/api/v1";
        if (course == null) course = "http://course-service:8083/api/v1";
        if (teaching == null) teaching = "http://teaching-service:8084/api/v1";
        if (kt == null) kt = "http://kt-service:8095";
        if (ai == null) ai = "http://ai-service:8090/api/v1";
        if (ops == null) ops = "http://ops-service:8096/api/v1/ops";
    }
}
