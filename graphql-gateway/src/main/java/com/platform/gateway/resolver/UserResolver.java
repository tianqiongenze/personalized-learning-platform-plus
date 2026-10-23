package com.platform.gateway.resolver;

import com.platform.gateway.client.DomainClient;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Map;

/**
 * User 域 Resolver — roles 经 BatchMapping 批量装载(防 N+1)。
 */
@Controller
public class UserResolver {

    private final DomainClient client;

    public UserResolver(DomainClient client) {
        this.client = client;
    }

    @QueryMapping
    public Map<String, Object> user(@Argument String id) {
        return client.getMap("iam", "/users/" + id);
    }

    @QueryMapping
    public List<Map<String, Object>> users(@Argument String tenantId,
                                           @Argument Integer page,
                                           @Argument Integer size) {
        // iam 返回 ApiResponse{ success, data:{ content:[...] } } — 解包(同 TenantResolver)
        Map<String, Object> resp = client.getMap("iam", "/users?tenantId=" + tenantId
                + "&page=" + or(page, 0) + "&size=" + or(size, 20));
        return TenantResolver.unwrapContent(resp);
    }

    @MutationMapping
    public Map<String, Object> createUser(@Argument String tenantId, @Argument String username,
                                          @Argument String email, @Argument String password,
                                          @Argument List<String> roleIds) {
        var body = new java.util.HashMap<String, Object>();
        body.put("tenantId", tenantId);
        body.put("username", username);
        body.put("email", email);
        body.put("password", password);
        if (roleIds != null) body.put("roleIds", roleIds);
        return client.post("iam", "/users", body, Map.class);
    }

    /** User.roles 批量解析: 一次调用按 userId 分组, 避免逐用户 N+1 */
    @BatchMapping
    public Map<String, List<Map<String, Object>>> roles(List<Map<String, Object>> users) {
        if (users == null || users.isEmpty()) return Map.of();
        try {
            List<Map<String, Object>> all = client.getList("iam", "/roles");
            Map<String, List<Map<String, Object>>> result = new java.util.HashMap<>();
            for (Map<String, Object> u : users) {
                Object roleIds = u.get("roleIds");
                List<Map<String, Object>> matched = new java.util.ArrayList<>();
                if (roleIds instanceof List<?> ids) {
                    for (Map<String, Object> r : all) {
                        if (ids.contains(r.get("id"))) matched.add(r);
                    }
                }
                result.put(String.valueOf(u.get("id")), matched);
            }
            return result;
        } catch (Exception e) {
            return Map.of();
        }
    }

    @SchemaMapping(typeName = "User", field = "roles")
    public List<Map<String, Object>> rolesSingle(Map<String, Object> user) {
        return roles(List.of(user)).getOrDefault(String.valueOf(user.get("id")), List.of());
    }

    private int or(Integer v, int d) {
        return v == null ? d : v;
    }
}
