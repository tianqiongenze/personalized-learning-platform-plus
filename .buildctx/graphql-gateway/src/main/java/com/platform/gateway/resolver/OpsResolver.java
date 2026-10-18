package com.platform.gateway.resolver;

import com.platform.gateway.client.DomainClient;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Map;

/**
 * Ops 域 Resolver(服务健康/缓存统计 — 统一运维监测平台数据面)。
 */
@Controller
public class OpsResolver {

    private final DomainClient client;

    public OpsResolver(DomainClient client) {
        this.client = client;
    }

    @QueryMapping
    public List<Map<String, Object>> serviceHealth(@Argument String name) {
        List<Map<String, Object>> all = client.getList("ops", "/services/health");
        if (name == null) return all;
        return all.stream().filter(s -> name.equals(s.get("name"))).toList();
    }

    @QueryMapping
    public List<Map<String, Object>> cacheStats() {
        return client.getList("ops", "/cache/stats");
    }
}
