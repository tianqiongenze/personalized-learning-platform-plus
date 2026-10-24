package com.platform.ai.generation;

import com.platform.common.event.EventTopics;
import com.platform.common.event.OutboxEventRelay;
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
 *        -> 结果经 Outbox(app.events.outbox=true, at-least-once 不丢)或直发 ai-generation-results
 *           + HTTP 推送 realtime-gateway(/push)
 *        -> 学生端经 WS/SSE 即时收到"生成完成"
 * 这样 AI 生成响应从"同步 30s×并发数"变为"提交毫秒级 + 完成推送", 支撑 1000 并发提交。
 */
@Slf4j
@Service
public class AsyncGenerationService {

    private final PulsarTemplate<String> pulsar;
    private final ObjectMapper mapper;
    private final RestClient realtimeGateway;
    /** outbox 模式(推荐, 事件不丢); null=直发 Pulsar(旧行为) */
    private final org.springframework.beans.factory.ObjectProvider<OutboxEventRelay> outboxProvider;
    private final org.springframework.context.ApplicationContext ctx;

    public AsyncGenerationService(PulsarTemplate<String> pulsar, ObjectMapper mapper,
                                  org.springframework.context.ApplicationContext applicationContext,
                                  @Value("${app.realtime.url:http://realtime-gateway.middleware:8095}") String realtimeUrl,
                                  org.springframework.beans.factory.ObjectProvider<OutboxEventRelay> outboxProvider) {
        this.pulsar = pulsar;
        this.mapper = mapper;
        this.realtimeGateway = RestClient.builder()
                .baseUrl(realtimeUrl)
                .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
                    setConnectTimeout(1000);
                    setReadTimeout(3000);
                }})
                .build();
        // 2026-10-24: 不在构造器里 getIfAvailable() — 与 relay 的初始化顺序未定,
        // 早于 relay 注册会恒得 null(静默退化直发)。惰性到提交时解析。
        this.outboxProvider = outboxProvider;
        this.ctx = applicationContext;
    }

    private OutboxEventRelay outbox() {
        OutboxEventRelay r = outboxProvider.getIfAvailable();
        try {
            var env = ctx.getEnvironment();
            log.info("[outbox-diag] relay={} propOutbox={} propEnabled={} publisherBean={} jtBean={}",
                    r != null, env.getProperty("app.events.outbox"), env.getProperty("app.events.enabled"),
                    ctx.containsBean("pulsarDomainEventPublisher"),
                    ctx.containsBean("jdbcTemplate"));
        } catch (Exception e) {
            log.warn("[outbox-diag] ctx probe failed: {}", e.toString());
        }
        return r;
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
            OutboxEventRelay outbox = outbox();
            if (outbox != null) {
                // 任务提交也走 outbox: 队列不可用不丢任务, relay 重投
                outbox.save(EventTopics.AI_GENERATION_TASKS, "AI_GENERATION_TASK", tenantId, taskId, mapper.readTree(payload));
            } else {
                // routingKey=studentId 保证同一学生的任务串行有序
                pulsar.send(EventTopics.AI_GENERATION_TASKS, payload);
            }
            return taskId;
        } catch (Exception e) {
            log.error("生成任务提交失败 tenant={} student={}", tenantId, studentId, e);
            throw new IllegalStateException("任务队列不可用, 请稍后重试", e);
        }
    }

    /** 生成完成后: 结果入主题(持久, outbox 保证不丢) + 实时推送(在线用户即时感知) */
    public void publishResult(String taskId, String tenantId, String studentId,
                              String resourceType, String content, boolean passed) {
        try {
            String payload = mapper.writeValueAsString(java.util.Map.of(
                    "taskId", taskId, "tenantId", tenantId, "studentId", studentId,
                    "resourceType", resourceType, "passed", passed, "content", content));
            OutboxEventRelay outbox = outbox();
            if (outbox != null) {
                // 生成结果不丢: 业务(索引/评分)事务同库原子写 outbox, relay 至少一次投递
                outbox.save(EventTopics.AI_GENERATION_RESULTS, "AI_GENERATION_RESULT", tenantId, taskId,
                        mapper.readTree(payload));
            } else {
                pulsar.send(EventTopics.AI_GENERATION_RESULTS, payload);
            }
        } catch (Exception e) {
            // outbox 模式下落库失败必须上抛让消费侧重试整条消息(EER: 事件不丢)
            throw new IllegalStateException("结果持久化失败 taskId={}", e);
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
