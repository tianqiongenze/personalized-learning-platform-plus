package com.platform.gateway.resolver;

import com.platform.gateway.client.DomainClient;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.Map;

/**
 * AI 域 Resolver(资源生成/质量评测)。
 */
@Controller
public class AIResolver {

    private final DomainClient client;

    public AIResolver(DomainClient client) {
        this.client = client;
    }

    @QueryMapping
    public Map<String, Object> generatedResource(@Argument String id) {
        return client.getMap("ai", "/generation/tasks/" + id);
    }

    @MutationMapping
    public Map<String, Object> generateResource(@Argument String courseId, @Argument String type,
                                                @Argument String topic, @Argument String studentId) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("courseId", courseId);
        body.put("type", type);
        body.put("topic", topic);
        if (studentId != null) body.put("studentId", studentId);
        return client.post("ai", "/generation/generate", body, Map.class);
    }
}
