package com.platform.gateway.client;

import com.platform.gateway.config.ServicesProperties;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 域服务统一调用客户端:
 *  - 复用连接池(RestClient 单例), 显式连接/读取超时(app.http.*, 默认 2s/5s)
 *  - 域调用熔断 domainCall(5xx/超时/连接失败计数, 4xx 业务错误不计入), OPEN 态快速失败
 *  - 转发 X-Tenant-Id 租户头(多租户全链路透传)
 *  - 异常映射为 GraphQL ErrorType(NOT_FOUND/BAD_REQUEST/INTERNAL_ERROR)
 */
@Component
public class DomainClient {

    private static final Logger log = LoggerFactory.getLogger(DomainClient.class);

    private final RestClient restClient;
    private final ServicesProperties services;
    private final CircuitBreaker circuitBreaker;

    public DomainClient(RestClient.Builder builder,
                        ServicesProperties services,
                        CircuitBreakerRegistry circuitBreakerRegistry,
                        @Value("${app.http.connect-timeout-ms:2000}") int connectTimeoutMs,
                        @Value("${app.http.read-timeout-ms:5000}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        this.restClient = builder
                .requestFactory(factory)
                // 兑现"转发 X-Tenant-Id 租户头"承诺: 从当前 servlet 请求(GatewayJwtFilter 包装层)取用户上下文头透传下游
                .requestInterceptor((request, body, execution) -> {
                    var attrs = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
                    if (attrs instanceof org.springframework.web.context.request.ServletRequestAttributes sra) {
                        var req = sra.getRequest();
                        copyIfPresent(req, request, "X-User-Id");
                        copyIfPresent(req, request, "X-Tenant-Id");
                        copyIfPresent(req, request, "X-Roles");
                        copyIfPresent(req, request, "X-User-Roles");
                        // iam 全量验签模型需要原始 Bearer 令牌
                        copyIfPresent(req, request, "Authorization");
                        // 本网关即信任边界, 标记下游放行(tenant-service HeaderAuthFilter 依赖)
                        request.getHeaders().add("X-Gateway-Verified", "1");
                    }
                    return execution.execute(request, body);
                })
                .build();
        this.services = services;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("domainCall");
    }

    private static void copyIfPresent(jakarta.servlet.http.HttpServletRequest from,
                                      org.springframework.http.HttpRequest to, String name) {
        String v = from.getHeader(name);
        if (v != null && !v.isBlank()) to.getHeaders().set(name, v);
    }

    public <T> T get(String service, String path, Class<T> type) {
        return withBreaker(service, path, () -> restClient.get()
                .uri(base(service) + path)
                .retrieve()
                .body(type));
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String service, String path, ParameterizedTypeReference<T> type) {
        return withBreaker(service, path, () -> restClient.get()
                .uri(base(service) + path)
                .retrieve()
                .body(type));
    }

    public <T> T post(String service, String path, Object body, Class<T> type) {
        return withBreaker(service, path, () -> restClient.post()
                .uri(base(service) + path)
                .body(body)
                .retrieve()
                .body(type));
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getMap(String service, String path) {
        return get(service, path, Map.class);
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getList(String service, String path) {
        return withBreaker(service, path, () -> {
            List<Map<String, Object>> body = restClient.get()
                    .uri(base(service) + path)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() { });
            return body == null ? List.of() : body;
        });
    }

    /**
     * 熔断包裹: OPEN 态快速失败, 4xx 由 ignore-exceptions 不计入熔断,
     * 其余 RestClientException 经 map() 转为 GraphQL 错误。
     */
    private <T> T withBreaker(String service, String path, Supplier<T> call) {
        try {
            return circuitBreaker.executeSupplier(call);
        } catch (CallNotPermittedException e) {
            log.warn("[gateway] circuit OPEN, fail-fast {}{}", service, path);
            throw new DomainException(ErrorType.INTERNAL_ERROR,
                    service + " 服务熔断保护中(快速失败), 请稍后重试: " + path, e);
        } catch (RestClientException e) {
            throw map(service, path, e);
        }
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
