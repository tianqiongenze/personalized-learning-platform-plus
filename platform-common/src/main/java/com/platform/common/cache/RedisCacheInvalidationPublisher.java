package com.platform.common.cache;

import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 基于 Redis pub/sub 的缓存失效广播实现
 * 消息体: "cacheName"(全清) 或 "cacheName|key"(单条失效)
 */
public class RedisCacheInvalidationPublisher implements CacheInvalidationPublisher {

    private final org.springframework.data.redis.core.StringRedisTemplate template;
    private final String channel;
    private final List<TwoLevelCache> managedCaches;
    private final RedisMessageListenerContainer container;

    public RedisCacheInvalidationPublisher(RedisConnectionFactory factory,
                                           String channel,
                                           List<TwoLevelCache> managedCaches) {
        this.template = new org.springframework.data.redis.core.StringRedisTemplate();
        this.template.setConnectionFactory(factory);
        this.template.afterPropertiesSet();
        this.channel = channel;
        this.managedCaches = managedCaches;

        this.container = new RedisMessageListenerContainer();
        this.container.setConnectionFactory(factory);
        this.container.addMessageListener((message, pattern) -> {
            String payload = new String(message.getBody(), StandardCharsets.UTF_8);
            int idx = payload.indexOf('|');
            String cacheName = idx < 0 ? payload : payload.substring(0, idx);
            String key = idx < 0 ? null : payload.substring(idx + 1);
            for (TwoLevelCache c : managedCaches) {
                if (c.getName().equals(cacheName)) {
                    c.evictLocal(key);
                }
            }
        }, new ChannelTopic(channel));
        this.container.afterPropertiesSet();
        this.container.start();
    }

    @Override
    public void publish(String cacheName, String key) {
        try {
            template.convertAndSend(channel, key == null ? cacheName : cacheName + "|" + key);
        } catch (RuntimeException ignored) {
            // Redis 不可用时本地缓存仍可工作
        }
    }

    public void destroy() {
        if (container != null) {
            try {
                container.destroy();
            } catch (Exception ignored) {
                // 关闭失败不影响主流程
            }
        }
    }
}
