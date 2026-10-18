package com.platform.ai.generation;

import com.platform.common.event.EventTopics;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.pulsar.core.PulsarTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * 异步生成入口(1000 并发削峰核心链路):
 *   提交 -> Pulsar ai-generation-tasks(队列削峰, 并发上限=消费者副本数×并发度)
 *        -> agent-service(LangGraph 工作流) / ai-service(单轮) 消费
 *        -> 结果写 ai-generation-results + HTTP 推送 realtime-gateway(/push)
 *        -> 学生端经 WS/SSE 即时收到"生成完成"
 * 这样 AI 生成响应从"同步 30s×并发数"变为"提交毫秒级 + 完成推送", 支撑 1000 并发提交。
 */
@Slf4j
@Service
public class AsyncGenerationService {

    private final PulsarTemplate<String> pulsar;
    private final ObjectMapper mapper;
    private final RestClient realtimeGateway;

    public AsyncGenerationService(PulsarTemplate<String> pulsar, ObjectMapper mapper,
                                  @Value("${app.realtime.url:http://realtime-gateway.middleware:8095}") String realtimeUrl) {
        this.pulsar = pulsar;
        this.mapper = mapper;
        this.realtimeGateway = RestClient.builder()
                .baseUrl(realtimeUrl)
                .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
                    setConnectTimeout(1000);
                    setReadTimeout(3000);
                }})
                .build();
    }

    /** 提交任务, 立即返回 taskId(前端可先渲染骨架屏) */
    public String submit(String tenantId, String studentId, String resourceType,
                         String knowledgePoint, String industryTags, String requirements) {
        String taskId = UUID.randomUUID().toString();
        try {
            String payload = mapper.writeValueAsString(java.util.Map.of(
                    "taskId", taskId, "tenantId", tenantId, "studentId", studentId,
                    "resourceType", resourceType, "knowledgePoint", knowledgePoint,
                    "industryTags", industryTags == null ? "" : industryTags,
                    "requirements", requirements == null ? "" : requirements));
            // routingKey=studentId 保证同一学生的任务串行有序
            pulsar.send(EventTopics.AI_GENERATION_TASKS, payload);
            return taskId;
        } catch (Exception e) {
            log.error("生成任务提交失败 tenant={} student={}", tenantId, studentId, e);
            throw new IllegalStateException("任务队列不可用, 请稍后重试", e);
        }
    }

    /** 生成完成后: 结果入主题(持久) + 实时推送(在线用户即时感知) */
    public void publishResult(String taskId, String tenantId, String studentId,
                              String resourceType, String content, boolean passed) {
        try {
            String payload = mapper.writeValueAsString(java.util.Map.of(
                    "taskId", taskId, "tenantId", tenantId, "studentId", studentId,
                    "resourceType", resourceType, "passed", passed, "content", content));
            pulsar.send(EventTopics.AI_GENERATION_RESULTS, payload);
        } catch (Exception e) {
            log.error("结果持久化失败 taskId={}", taskId, e);
        }
        try {
            realtimeGateway.post().uri("/push")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(java.util.Map.of(
                            "userId", studentId,
                            "type", "generation-done",
                            "payload", java.util.Map.of(
                                    "taskId", taskId, "resourceType", resourceType, "passed", passed)))
                    .retrieve().toBodilessEntity();
        } catch (Exception e) {
            // 学生离线: 结果仍在 results 主题, 登录后可拉取
            log.warn("实时推送失败(离线?) taskId={}", taskId);
        }
    }
}
