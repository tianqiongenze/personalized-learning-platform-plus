package com.platform.common.cache;

import com.platform.common.tenant.TenantContext;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TwoLevelCache 单测: 逐级回填/降级不阻断/租户隔离 key/失效广播。
 * 追踪真实执行路径(非零值假对象), Redis/发布器用 mock 隔离外部依赖。
 */
class TwoLevelCacheTest {

    @SuppressWarnings("unchecked")
    private final RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
    private final CacheInvalidationPublisher publisher = mock(CacheInvalidationPublisher.class);
    private final PlatformCacheProperties props = new PlatformCacheProperties();

    private TwoLevelCache cache;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valueOps);
        props.setMetrics(false);
        props.getL2().setKeyPrefix("plp:cache:");
        props.getL2().setTimeToLive(Duration.ofMinutes(5));
        cache = new TwoLevelCache("test-cache", 100, Duration.ofMinutes(5),
                Duration.ofMinutes(5), redis, props, new SimpleMeterRegistry(), publisher);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void getWithLoaderLoadsAndWritesL2() throws Exception {
        AtomicInteger loads = new AtomicInteger();
        Callable<String> loader = () -> {
            loads.incrementAndGet();
            return "v1";
        };
        Object v1 = cache.get("k1", loader);
        Object v2 = cache.get("k1", loader);
        assertEquals("v1", v1);
        assertEquals("v1", v2);
        assertEquals(1, loads.get(), "第二次命中 L1 不应再加载");
        verify(valueOps, times(1)).set(anyString(), eq("v1"), any(Duration.class));
    }

    @Test
    void l2HitBackfillsL1() {
        TenantContext.set("t-1");
        when(valueOps.get("plp:cache:test-cache:t-1:k2")).thenReturn("from-l2");
        Object v = cache.get("k2", String.class);
        assertEquals("from-l2", v);
        // L1 回填后, 第二次读不再访问 Redis
        cache.get("k2", String.class);
        verify(valueOps, times(1)).get(anyString());
    }

    @Test
    void l2FailureFallsBackToLoader() throws Exception {
        when(valueOps.get(anyString())).thenThrow(new IllegalStateException("redis down"));
        Object v = cache.get("k3", () -> "loaded-anyway");
        assertEquals("loaded-anyway", v);
    }

    @Test
    void putWritesBothLevels() {
        cache.put("k4", "val4");
        assertNotNull(cache.get("k4"));
        verify(valueOps, times(1)).set(anyString(), eq("val4"), any(Duration.class));
    }

    @Test
    void putFailureDoesNotThrow() {
        doThrow(new IllegalStateException("redis down")).when(valueOps)
                .set(anyString(), any(), any(Duration.class));
        cache.put("k5", "val5");
        assertNotNull(cache.get("k5"), "L2 写失败不阻断, L1 仍可读");
    }

    @Test
    void evictInvalidatesAllLevelsAndBroadcasts() {
        TenantContext.set("t-2");
        cache.put("k6", "val6");
        cache.evict("k6");
        assertNull(cache.get("k6"));
        verify(redis).delete("plp:cache:test-cache:t-2:k6");
        verify(publisher).publish("test-cache", "k6");
    }

    @Test
    void evictL2FailureStillBroadcasts() {
        when(redis.delete(anyString())).thenThrow(new IllegalStateException("redis down"));
        cache.evict("k7");
        verify(publisher).publish("test-cache", "k7");
    }

    @Test
    void clearInvalidatesL1AndBroadcasts() {
        cache.put("k8", "v8");
        cache.clear();
        assertNull(cache.get("k8"));
        verify(publisher).publish("test-cache", null);
    }

    @Test
    void nullValueSkipsL2WriteWhenDisabled() {
        props.getL2().setCacheNullValues(false);
        // Caffeine 拒绝 null put(NPE) —— 这是真实约束: null 值语义只能走 loader 返回 null 路径
        org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class,
                () -> cache.put("k9", null));
        verify(valueOps, never()).set(anyString(), any(), any(Duration.class));
    }

    @Test
    void loaderExceptionPropagatesAsRuntime() {
        RuntimeException boom = new RuntimeException("db exploded");
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> cache.get("k10", () -> { throw boom; }));
    }

    @Test
    void typedGetRejectsWrongType() {
        cache.put("k11", "text");
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> cache.get("k11", Integer.class));
    }

    @Test
    void sameNameCachesAreEqual() {
        TwoLevelCache other = new TwoLevelCache("test-cache", 10, Duration.ofMinutes(1),
                Duration.ofMinutes(1), redis, props, null, publisher);
        assertSame(cache.getNativeCache(), cache.getNativeCache());
        assertEquals(cache, other);
    }

    @Test
    void tenantIsolatesKeysViaL2Prefix() {
        // L1 是裸 key(不隔离); 租户隔离发生在 L2 key 前缀层:
        // t-a put 后 L2 key = prefix:{t-a}:shared, t-b 读 L2 的 key 不同 → L2 层隔离成立
        TenantContext.set("t-a");
        cache.put("shared", "for-a");
        verify(valueOps).set(eq("plp:cache:test-cache:t-a:shared"), eq("for-a"), any(Duration.class));
        TenantContext.set("t-b");
        cache.evict("shared");
        verify(redis).delete("plp:cache:test-cache:t-b:shared");
    }
}
