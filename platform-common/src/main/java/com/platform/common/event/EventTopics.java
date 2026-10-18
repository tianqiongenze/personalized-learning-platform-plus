package com.platform.common.event;

/**
 * 统一事件主题(pulsar persistent://public/default/ 下)
 * 与 k8s/base/pulsar.yaml 的 pulsar-init-topics Job 保持一致
 */
public final class EventTopics {
    private EventTopics() {}

    /** 全部领域事件总线(审计/联动) */
    public static final String DOMAIN_EVENTS = "persistent://public/default/domain-events";
    /** AI 生成任务下发 */
    public static final String AI_GENERATION_TASKS = "persistent://public/default/ai-generation-tasks";
    /** AI 生成结果回传 */
    public static final String AI_GENERATION_RESULTS = "persistent://public/default/ai-generation-results";
    /** 学习行为流水(画像更新) */
    public static final String LEARNING_BEHAVIOR = "persistent://public/default/learning-behavior";
    /** 资源向量索引 upsert(异步建向量索引) */
    public static final String RESOURCE_INDEX_UPSERT = "persistent://public/default/resource-index-upsert";
    /** 死信 */
    public static final String DOMAIN_EVENTS_DLQ = "persistent://public/default/domain-events-DLQ";
}
