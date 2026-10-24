package com.platform.teaching.infrastructure.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.event.EventTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.pulsar.annotation.PulsarListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * 学习行为消费者(2026-10-24 事件消费侧联动落地):
 * 订阅 learning-behavior → 调 kt-service /kt/events 更新掌握度(记忆金字塔/BKT)。
 * 补齐数据中台①"事件→画像"的下游联动; 失败仅告警不阻断(消费幂等由 eventId 去重, 后续加强)。
 */
@Component
public class LearningBehaviorListener {

    private static final Logger log = LoggerFactory.getLogger(LearningBehaviorListener.class);
    private final RestClient kt;
    private final ObjectMapper mapper = new ObjectMapper();

    public LearningBehaviorListener(@Value("${app.kt.base:http://kt-service.infra.svc.cluster.local:8086}") String ktBase) {
        this.kt = RestClient.builder()
                .baseUrl(ktBase)
                .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
                    setConnectTimeout(2000);
                    setReadTimeout(10000);
                }})
                .build();
    }

    @PulsarListener(
            topics = EventTopics.LEARNING_BEHAVIOR,
            subscriptionName = "teaching-mastery-sub",
            subscriptionType = org.apache.pulsar.client.api.SubscriptionType.Shared)
    public void onBehavior(String payload) {
        try {
            JsonNode envelope = mapper.readTree(payload);
            JsonNode p = envelope.path("payload");
            String studentId = p.path("studentId").asText("");
            String skillId = p.path("skillId").asText("");
            if (studentId.isEmpty() || skillId.isEmpty()) {
                return;
            }
            kt.post()
                    .uri("/kt/events")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(java.util.Map.of(
                            "studentId", studentId,
                            "skillId", skillId,
                            "correct", p.path("correct").asBoolean(false),
                            "bloomLevel", p.path("bloomLevel").asText("apply"),
                            "courseId", p.path("courseId").asText("")))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("[learning-behavior] 掌握度更新失败(不阻断): {}", e.getMessage());
        }
    }
}
