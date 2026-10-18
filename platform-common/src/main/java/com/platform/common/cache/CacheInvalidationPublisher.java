package com.platform.common.cache;

/**
 * 缓存失效广播器接口(基于 Redis pub/sub, Cluster 模式自动路由), 保证多实例 L1 一致性
 */
public interface CacheInvalidationPublisher {

    void publish(String cacheName, String key);
}
