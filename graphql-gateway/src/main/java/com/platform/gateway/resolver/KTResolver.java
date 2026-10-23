package com.platform.gateway.resolver;

import com.platform.gateway.client.DomainClient;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * KT 域 Resolver — 知识追踪/记忆金字塔/间隔重复/ZPD/Gap(kt-service)。
 */
@Controller
public class KTResolver {

    private final DomainClient client;

    public KTResolver(DomainClient client) {
        this.client = client;
    }

    @QueryMapping
    public Map<String, Object> mastery(@Argument String studentId, @Argument String skillId) {
        return client.getMap("kt", "/kt/mastery?studentId=" + studentId + "&skillId=" + skillId);
    }

    @QueryMapping
    public List<Map<String, Object>> masteryProfile(@Argument String studentId,
                                                    @Argument List<String> skillIds) {
        // kt-service 无 /kt/mastery/batch; 用 /kt/mastery 的 weak/strong 列表合成批量画像
        Map<String, Object> resp = client.getMap("kt", "/kt/mastery?studentId=" + studentId);
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (String key : new String[]{"weakKps", "strongKps"}) {
            Object arr = resp.get(key);
            if (arr instanceof List<?> l) {
                for (Object o : l) {
                    if (o instanceof Map<?,?> m) {
                        Map<String, Object> row = new java.util.HashMap<>();
                        row.put("skillId", String.valueOf(m.get("kp")));
                        row.put("mastery", m.get("mastery"));
                        row.put("level", "weakKps".equals(key) ? "WEAK" : "STRONG");
                        out.add(row);
                    }
                }
            }
        }
        return out;
    }

    @QueryMapping
    public List<Map<String, Object>> recommendations(@Argument String studentId,
                                                     @Argument Integer limit) {
        Map<String, Object> resp = client.getMap("kt",
                "/kt/recommendations/" + studentId + "?limit=" + (limit == null ? 5 : limit));
        Object items = resp.get("recommendations");
        return items instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
    }

    @QueryMapping
    public Map<String, Object> memoryProfile(@Argument String studentId) {
        return client.getMap("kt", "/kt/memory/" + studentId);
    }

    @QueryMapping
    public List<Map<String, Object>> reviewPlan(@Argument String studentId) {
        Map<String, Object> resp = client.getMap("kt", "/kt/review/" + studentId);
        Object items = resp.get("plan");
        return items instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
    }

    @QueryMapping
    public List<Map<String, Object>> skillGaps(@Argument String studentId, @Argument String courseId) {
        Map<String, Object> resp = client.getMap("kt",
                "/kt/gaps/" + studentId + (courseId == null ? "" : "?courseId=" + courseId));
        Object items = resp.get("gaps");
        return items instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
    }

    @MutationMapping
    public Map<String, Object> recordLearningEvent(@Argument String studentId, @Argument String skillId,
                                                   @Argument Boolean correct, @Argument String bloomLevel) {
        Map<String, Object> body = new HashMap<>();
        body.put("studentId", studentId);
        body.put("skillId", skillId);
        body.put("correct", correct);
        if (bloomLevel != null) body.put("bloomLevel", bloomLevel);
        return client.post("kt", "/kt/events", body, Map.class);
    }
}
