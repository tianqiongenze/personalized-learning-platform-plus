package com.platform.gateway.resolver;

import com.platform.gateway.client.DomainClient;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Map;

/**
 * Teaching 域 Resolver(学习路径/作业)。
 */
@Controller
public class TeachingResolver {

    private final DomainClient client;

    public TeachingResolver(DomainClient client) {
        this.client = client;
    }

    @QueryMapping
    public Map<String, Object> learningPath(@Argument String id) {
        return client.getMap("teaching", "/learning-paths/" + id);
    }

    @QueryMapping
    public List<Map<String, Object>> learningPaths(@Argument String courseId) {
        if (courseId == null || courseId.isBlank() || !courseId.matches("[0-9a-fA-F-]{36}")) {
            return client.getMap("teaching", "/learning-paths") instanceof Map<?,?> am
                    && am.get("data") instanceof Map<?,?> dm && dm.get("content") instanceof List<?> cl
                    ? (List<Map<String, Object>>) cl : List.of();
        }
        return client.getMap("teaching", "/learning-paths?courseId=" + courseId) instanceof Map<?,?> am
                ? (am.get("data") instanceof Map<?,?> dm && dm.get("content") instanceof List<?> cl ? (List<Map<String, Object>>) cl : List.of())
                : List.of();
    }

    @QueryMapping
    public List<Map<String, Object>> assignments(@Argument String courseId) {
        String q = (courseId != null && courseId.matches("[0-9a-fA-F-]{36}")) ? "?courseId=" + courseId : "";
        return client.getMap("teaching", "/teaching/assignments" + q) instanceof Map<?,?> am
                ? (am.get("data") instanceof Map<?,?> dm && dm.get("content") instanceof List<?> cl ? (List<Map<String, Object>>) cl : List.of())
                : List.of();
    }
}
