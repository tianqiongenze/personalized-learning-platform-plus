package com.platform.ai.generation;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** L1 精确缓存: key 稳定性 / 命中/未命中 / Redis 故障降级直穿 / put 失败不抛 */
class LlmExactCacheTest {

    @SuppressWarnings("unchecked")
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private LlmExactCache cache() {
        when(redis.opsForValue()).thenReturn(valueOps);
        return new LlmExactCache(redis, new ObjectMapper(), registry, true);
    }

    @Test
    void keyIsStableSha256() {
        assertEquals(LlmExactCache.key("s", "u"), LlmExactCache.key("s", "u"));
        assertNotEquals(LlmExactCache.key("s", "u"), LlmExactCache.key("s", "u2"));
        assertEquals(64, LlmExactCache.key("s", "u").length());
    }

    @Test
    void hitReturnsCachedValue() {
        LlmExactCache c = cache();
        when(valueOps.get(startsWith("plp:cache:llm:exact:"))).thenReturn("cached-content");
        assertEquals("cached-content", c.get("EXPLANATION_DOC", "sys", "user"));
        assertEquals(1.0, registry.get("llm_cache_requests_total").tag("result", "hit").counter().count());
    }

    @Test
    void missIncrementsMissCounter() {
        LlmExactCache c = cache();
        when(valueOps.get(anyString())).thenReturn(null);
        assertNull(c.get("EXERCISE_SET", "sys", "user"));
        assertEquals(1.0, registry.get("llm_cache_requests_total").tag("result", "miss").counter().count());
    }

    @Test
    void redisFailureFallsThrough() {
        LlmExactCache c = cache();
        when(valueOps.get(anyString())).thenThrow(new IllegalStateException("redis down"));
        assertNull(c.get("EXPLANATION_DOC", "sys", "user"), "Redis 故障应降级直穿");
    }

    @Test
    void putUsesTtlByType() {
        LlmExactCache c = cache();
        c.put("MICRO_LESSON_SCRIPT", "sys", "user", "content");
        verify(valueOps).set(startsWith("plp:cache:llm:exact:"), eq("content"), eq(Duration.ofDays(7)));
        c.put("EXERCISE_SET", "sys", "user", "content");
        verify(valueOps).set(startsWith("plp:cache:llm:exact:"), eq("content"), eq(Duration.ofHours(1)));
    }

    @Test
    void putFailureDoesNotThrow() {
        LlmExactCache c = cache();
        doThrow(new IllegalStateException("redis down")).when(valueOps)
                .set(anyString(), anyString(), any(Duration.class));
        assertDoesNotThrow(() -> c.put("EXERCISE_SET", "sys", "user", "content"));
    }

    @Test
    void disabledCacheAlwaysMisses() {
        when(redis.opsForValue()).thenReturn(valueOps);
        LlmExactCache c = new LlmExactCache(redis, new ObjectMapper(), registry, false);
        assertNull(c.get("EXPLANATION_DOC", "sys", "user"));
        verify(valueOps, never()).get(anyString());
        assertFalse(c.isEnabled());
    }
}
