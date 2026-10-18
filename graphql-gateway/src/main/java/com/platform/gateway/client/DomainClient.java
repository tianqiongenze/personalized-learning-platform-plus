package com.platform.gateway.client;

import com.platform.gateway.config.ServicesProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

/**
 * 域服务统一调用客户端:
 *  - 复用连接池(RestClient 单例)
 *  - 转发 X-Tenant-Id 租户头(多租户全链路透传)
 *  - 异常映射为 GraphQL ErrorType(NOT_FOUND/BAD_REQUEST/INTERNAL_ERROR)
 */
@Component
public class DomainClient {

    private static final Logger log = LoggerFactory.getLogger(DomainClient.class);

    private final RestClient restClient;
    private final ServicesProperties services;

    public DomainClient(RestClient.Builder builder, ServicesProperties services) {
        this.restClient = builder.build();
        this.services = services;
    }

    public <T> T get(String service, String path, Class<T> type) {
        try {
            return restClient.get()
                    .uri(base(service) + path)
                    .retrieve()
                    .body(type);
        } catch (RestClientException e) {
            throw map(service, path, e);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String service, String path, ParameterizedTypeReference<T> type) {
        try {
            return restClient.get()
                    .uri(base(service) + path)
                    .retrieve()
                    .body(type);
        } catch (RestClientException e) {
            throw map(service, path, e);
        }
    }

    public <T> T post(String service, String path, Object body, Class<T> type) {
        try {
            return restClient.post()
                    .uri(base(service) + path)
                    .body(body)
                    .retrieve()
                    .body(type);
        } catch (RestClientException e) {
            throw map(service, path, e);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getMap(String service, String path) {
        return get(service, path, Map.class);
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getList(String service, String path) {
        List<Map<String, Object>> body = restClient.get()
                .uri(base(service) + path)
                .retrieve()
                .body(new ParameterizedTypeReference<>() { });
        return body == null ? List.of() : body;
    }

    private String base(String service) {
        return switch (service) {
            case "tenant" -> services.tenant();
            case "iam" -> services.iam();
            case "course" -> services.course();
            case "teaching" -> services.teaching();
            case "kt" -> services.kt();
            case "ai" -> services.ai();
            case "ops" -> services.ops();
            default -> throw new IllegalArgumentException("unknown service: " + service);
        };
    }

    private RuntimeException map(String service, String path, RestClientException e) {
        log.warn("[gateway] call {}{} failed: {}", service, path, e.getMessage());
        String msg = e.getMessage() == null ? "" : e.getMessage();
        if (msg.contains("404")) {
            return new DomainException(ErrorType.NOT_FOUND, service + " 资源不存在: " + path, e);
        }
        if (msg.contains("400") || msg.contains("409")) {
            return new DomainException(ErrorType.BAD_REQUEST, service + " 请求非法: " + e.getMessage(), e);
        }
        return new DomainException(ErrorType.INTERNAL_ERROR, service + " 调用失败: " + e.getMessage(), e);
    }

    public static class DomainException extends RuntimeException {
        public final ErrorType errorType;
        public DomainException(ErrorType type, String message, Throwable cause) {
            super(message, cause);
            this.errorType = type;
        }
    }

    @Component
    static class DomainExceptionResolver extends DataFetcherExceptionResolverAdapter {
        @Override
        protected graphql.GraphQLError resolveToSingleError(Throwable ex, graphql.schema.DataFetchingEnvironment env) {
            if (ex instanceof DomainException de) {
                return graphql.GraphQLError.newError()
                        .errorType(de.errorType)
                        .message(de.getMessage())
                        .path(env.getExecutionStepInfo().getPath())
                        .location(env.getField().getSourceLocation())
                        .build();
            }
            return null; // 交由默认处理
        }
    }
}
