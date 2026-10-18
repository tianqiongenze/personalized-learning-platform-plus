package com.platform.ai.generation;

import com.platform.common.event.EventTopics;
import org.springframework.pulsar.annotation.PulsarListener;
import org.springframework.stereotype.Component;

/**
 * Pulsar 消费者: 监听 AI 生成任务消息(异步生成链路), 执行结果经资源索引主题回写向量库。
 * Topic 与 k8s/base/pulsar.yaml 的初始化 Job 保持一致。
 */
@Component
public class GenerationTaskListener {

    private final GenerationService generationService;
    private final AsyncGenerationService asyncGenerationService;

    public GenerationTaskListener(GenerationService generationService,
                                  AsyncGenerationService asyncGenerationService) {
        this.generationService = generationService;
        this.asyncGenerationService = asyncGenerationService;
    }

    @PulsarListener(
            topics = EventTopics.AI_GENERATION_TASKS,
            subscriptionName = "ai-service-sub",
            subscriptionType = org.apache.pulsar.client.api.SubscriptionType.Shared)
    public void onTask(String payload) {
        // payload: {"resourceType":"..","knowledgePoint":"..","industryTags":"..","requirements":".."}
        try {
            com.fasterxml.jackson.databind.JsonNode node =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(payload);
            String content = generationService.generate(
                    node.path("resourceType").asText("KNOWLEDGE_POINT_TALK"),
                    node.path("knowledgePoint").asText(""),
                    node.path("industryTags").asText(""),
                    node.path("requirements").asText(""));
            // 生成结果建向量索引, 供后续 RAG
            String taskId = node.path("taskId").asText("task-" + System.currentTimeMillis());
            generationService.indexResource(taskId,
                    node.path("knowledgePoint").asText(""),
                    content, java.util.Map.of("source", "ai-generation"));
            // 结果持久化 + 实时推送(WS/SSE 到三端)
            asyncGenerationService.publishResult(taskId,
                    node.path("tenantId").asText("public"),
                    node.path("studentId").asText("guest"),
                    node.path("resourceType").asText(""), content, true);
        } catch (Exception e) {
            // DLQ 由 deadLetterPolicy 兜底; 此处记录重试上下文
            org.slf4j.LoggerFactory.getLogger(getClass())
                    .error("AI 生成任务处理失败, 将进入 DLQ: {}", payload, e);
        }
    }
}
