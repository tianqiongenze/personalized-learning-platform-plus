package com.platform.ai.generation;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;

/**
 * L1 精确缓存(LCO 铁律): SHA256(system+user prompt) → response, Redis String,
 * TTL 按资源类型分级(讲解类 24h / 脚本类 7d / 其他 1h)。命中跳过外部 LLM API。
 * 失败全部吞掉走直穿 —— 缓存层故障不得阻断生成主链(RWT 降级原则)。
 */
@Component
public class LlmExactCache {

    private static final String KEY_PREFIX = "plp:cache:llm:exact:";

    /** TTL 分级: 小时。EXPLANATION/KNOWLEDGE 类重生成成本高且模板稳定, 缓存更久 */
    private static final Map<String, Duration> TTL_BY_TYPE = Map.of(
            "EXPLANATION_DOC", Duration.ofHours(24),
            "KNOWLEDGE_POINT_TALK", Duration.ofHours(24),
            "MICRO_LESSON_SCRIPT", Duration.ofDays(7),
            "MICRO_VIDEO_SCRIPT", Duration.ofDays(7),
            "EXERCISE_SET", Duration.ofHours(1),
            "TRAINING_TASK", Duration.ofHours(1));

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Counter hits;
    private final Counter misses;

    public LlmExactCache(StringRedisTemplate redis, ObjectMapper objectMapper,
                         MeterRegistry registry,
                         @Value("${app.llm-cache.enabled:true}") boolean enabled) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.hits = Counter.builder("llm_cache_requests_total")
                .tag("layer", "exact").tag("result", "hit").register(registry);
        this.misses = Counter.builder("llm_cache_requests_total")
                .tag("layer", "exact").tag("result", "miss").register(registry);
    }

    private final boolean enabled;

    /** 缓存 key: SHA256(systemPrompt + '|' + userPrompt) */
    static String key(String systemPrompt, String userPrompt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest((systemPrompt + "|" + userPrompt)
                    .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** 命中返回缓存响应, 未命中/禁用/Redis 故障返回 null(直穿) */
    public String get(String resourceType, String systemPrompt, String userPrompt) {
        if (!enabled) return null;
        try {
            String v = redis.opsForValue().get(KEY_PREFIX + key(systemPrompt, userPrompt));
            if (v == null) {
                misses.increment();
                return null;
            }
            hits.increment();
            return v;
        } catch (RuntimeException e) {
            return null; // Redis 不可用: 直穿
        }
    }

    /** put 失败不抛(缓存是尽力而为) */
    public void put(String resourceType, String systemPrompt, String userPrompt, String response) {
        if (!enabled || response == null || response.isBlank()) return;
        try {
            redis.opsForValue().set(KEY_PREFIX + key(systemPrompt, userPrompt), response,
                    TTL_BY_TYPE.getOrDefault(resourceType, Duration.ofHours(1)));
        } catch (RuntimeException ignored) {
            // 缓存写入失败不影响主流程
        }
    }

    public boolean isEnabled() { return enabled; }
}
