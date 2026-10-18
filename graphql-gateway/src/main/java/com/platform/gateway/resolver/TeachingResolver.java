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
        return client.getList("teaching", "/learning-paths?courseId=" + courseId);
    }

    @QueryMapping
    public List<Map<String, Object>> assignments(@Argument String courseId) {
        return client.getList("teaching", "/assignments?courseId=" + courseId);
    }
}
