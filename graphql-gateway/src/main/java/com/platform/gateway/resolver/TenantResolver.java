package com.platform.gateway.resolver;

import com.platform.gateway.client.DomainClient;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Map;

/**
 * Tenant 域 Resolver — 委托 tenant-service REST。
 */
@Controller
public class TenantResolver {

    private final DomainClient client;

    public TenantResolver(DomainClient client) {
        this.client = client;
    }

    @QueryMapping
    public Map<String, Object> tenant(@Argument String id) {
        return client.getMap("tenant", "/tenants/" + id);
    }

    @QueryMapping
    public List<Map<String, Object>> tenants(@Argument String status,
                                             @Argument Integer page,
                                             @Argument Integer size) {
        // tenant-service 返回 ApiResponse{ success, data:{ content:[...] } } — 解包
        Map<String, Object> resp = client.getMap("tenant", "/tenants?page=" + or(page, 0) + "&size=" + or(size, 20)
                + (status == null ? "" : "&status=" + status));
        return unwrapContent(resp);
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> unwrapContent(Map<String, Object> resp) {
        if (resp == null) return List.of();
        Object data = resp.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            Object content = ((Map<String, Object>) dataMap).get("content");
            if (content instanceof List<?> list) {
                return (List<Map<String, Object>>) list;
            }
        }
        return List.of();
    }

    @MutationMapping
    public Map<String, Object> createTenant(@Argument String code, @Argument String name,
                                            @Argument String description) {
        return client.post("tenant", "/tenants",
                Map.of("code", code, "name", name, "description", description == null ? "" : description),
                Map.class);
    }

    private int or(Integer v, int d) {
        return v == null ? d : v;
    }
}
