package com.platform.common.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * RedisCacheInvalidationPublisher 单测: 广播消息格式(name|key 单条 / name 全清)/
 * 路由到对应缓存 evictLocal/发布失败不抛。
 *
 * mock factory 无真实连接: RedisMessageListenerContainer.start() → lazyListen 会调
 * connection.isSubscribed()/subscribe()，不 stub 会 NPE，故在 BeforeEach 给一条全 stub 连接。
 */
class RedisCacheInvalidationPublisherTest {

    private final RedisConnectionFactory factory = mock(RedisConnectionFactory.class);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void stubConnection() {
        RedisConnection conn = mock(RedisConnection.class);
        when(factory.getConnection()).thenReturn(conn);
        when(conn.isSubscribed()).thenReturn(false);
        // 容器的注册等待依赖连接的订阅回调(SubscriptionListener.onChannelSubscribed)触发:
        // mock 连接直接同步回调, 让 start() 里的 "Subscription registration" 立即完成。
        // Mockito 会把 varargs 摊平: 单通道时 getArgument(1) 是 byte[], 多通道才是 byte[][]。
        org.mockito.Mockito.doAnswer(inv -> {
            var lst = (org.springframework.data.redis.connection.SubscriptionListener) inv.getArgument(0);
            Object raw = inv.getArgument(1);
            byte[][] channels = raw instanceof byte[][] arr ? arr : new byte[][]{(byte[]) raw};
            for (byte[] ch : channels) {
                lst.onChannelSubscribed(ch, 1L);
            }
            return null;
        }).when(conn).subscribe(any(), org.mockito.ArgumentMatchers.<byte[]>any());
        org.mockito.Mockito.doAnswer(inv -> {
            var lst = (org.springframework.data.redis.connection.SubscriptionListener) inv.getArgument(0);
            Object raw = inv.getArgument(1);
            byte[][] patterns = raw instanceof byte[][] arr ? arr : new byte[][]{(byte[]) raw};
            for (byte[] pat : patterns) {
                lst.onPatternSubscribed(pat, 1L);
            }
            return null;
        }).when(conn).pSubscribe(any(), org.mockito.ArgumentMatchers.<byte[]>any());
    }

    @SuppressWarnings("unchecked")
    private TwoLevelCache cache(String name) {
        PlatformCacheProperties props = new PlatformCacheProperties();
        props.getL2().setKeyPrefix("plp:cache:");
        props.setMetrics(false);
        return new TwoLevelCache(name, 10, Duration.ofMinutes(1), Duration.ofMinutes(1),
                (org.springframework.data.redis.core.RedisTemplate<String, Object>) mock(org.springframework.data.redis.core.RedisTemplate.class),
                props, null, mock(CacheInvalidationPublisher.class));
    }

    private RedisCacheInvalidationPublisher publisher(TwoLevelCache... caches) {
        RedisCacheInvalidationPublisher p = new RedisCacheInvalidationPublisher(
                factory, "plp:cache:invalidate", List.of(caches));
        // start() 的订阅登记等不到 mock 连接的订阅确认而超时;
        // 把订阅任务改成同步空跑(此时 channelMapping 已由 addMessageListener 写入,
        // extractListener 仍能取到 listener)。
        try {
            var f = RedisCacheInvalidationPublisher.class.getDeclaredField("container");
            f.setAccessible(true);
            RedisMessageListenerContainer container = (RedisMessageListenerContainer) f.get(p);
            java.util.concurrent.Executor noop = Runnable::run;
            container.setTaskExecutor(noop);
            container.setSubscriptionExecutor(noop);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return p;
    }

    @Test
    void publishDoesNotThrowEvenWhenRedisDown() {
        RedisCacheInvalidationPublisher p = publisher(cache("courses"));
        assertDoesNotThrow(() -> p.publish("courses", "k1"));
        assertDoesNotThrow(() -> p.publish("courses", null));
        p.destroy();
    }

    @Test
    void broadcastSingleKeyEvictsLocalL1() throws Exception {
        TwoLevelCache c = cache("courses");
        RedisCacheInvalidationPublisher p = publisher(c);
        var listener = extractListener(p);
        c.put("k1", "v1");
        listener.onMessage(new DefaultMessage("plp:cache:invalidate".getBytes(StandardCharsets.UTF_8),
                "courses|k1".getBytes(StandardCharsets.UTF_8)), null);
        assertNull(c.get("k1"), "广播单条失效后 L1 应被剔除");
        p.destroy();
    }

    @Test
    void broadcastWholeNameClearsCache() throws Exception {
        TwoLevelCache c = cache("users");
        RedisCacheInvalidationPublisher p = publisher(c);
        var listener = extractListener(p);
        c.put("k1", "v1");
        c.put("k2", "v2");
        listener.onMessage(new DefaultMessage("plp:cache:invalidate".getBytes(StandardCharsets.UTF_8),
                "users".getBytes(StandardCharsets.UTF_8)), null);
        assertNull(c.get("k1"));
        assertNull(c.get("k2"), "无 key 的广播 = 全清");
        p.destroy();
    }

    @Test
    void broadcastForOtherCacheIsIgnored() throws Exception {
        TwoLevelCache c = cache("courses");
        RedisCacheInvalidationPublisher p = publisher(c);
        var listener = extractListener(p);
        c.put("k1", "v1");
        listener.onMessage(new DefaultMessage("plp:cache:invalidate".getBytes(StandardCharsets.UTF_8),
                "other-cache|k1".getBytes(StandardCharsets.UTF_8)), null);
        assertNotNull(c.get("k1"), "其他缓存名的广播不影响本缓存");
        p.destroy();
    }

    private org.springframework.data.redis.connection.MessageListener extractListener(
            RedisCacheInvalidationPublisher p) throws Exception {
        var f = RedisCacheInvalidationPublisher.class.getDeclaredField("container");
        f.setAccessible(true);
        RedisMessageListenerContainer container = (RedisMessageListenerContainer) f.get(p);
        var fm = RedisMessageListenerContainer.class.getDeclaredField("channelMapping");
        fm.setAccessible(true);
        @SuppressWarnings("unchecked")
        var mappings = (java.util.Map<?, java.util.Collection<org.springframework.data.redis.connection.MessageListener>>) fm.get(container);
        return mappings.values().iterator().next().iterator().next();
    }
}
