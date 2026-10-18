package com.platform.common.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 多级缓存配置项 (app.cache.*)
 * L1: Caffeine 进程内缓存; L2: Redis Cluster(集群既有)
 */
@ConfigurationProperties(prefix = "app.cache")
public class PlatformCacheProperties {

    /** 是否启用多级缓存 */
    private boolean enabled = true;

    /** L1 Caffeine 全局默认设置 */
    private final L1 l1 = new L1();

    /** L2 Redis 设置 */
    private final L2 l2 = new L2();

    /**
     * 按 cacheName 覆盖: name -> {maxSize, ttl}
     * 未配置的 cache 使用 l1 默认值
     */
    private final Map<String, CacheSpec> specs = new LinkedHashMap<>();

    /** 是否向 Micrometer 注册 Caffeine 指标(统一运维平台 L1 视图数据源) */
    private boolean metrics = true;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public L1 getL1() { return l1; }
    public L2 getL2() { return l2; }
    public Map<String, CacheSpec> getSpecs() { return specs; }
    public boolean isMetrics() { return metrics; }
    public void setMetrics(boolean metrics) { this.metrics = metrics; }

    public static class L1 {
        /** 默认最大条目数 */
        private long maxSize = 10_000;
        /** 默认写后过期 */
        private Duration expireAfterWrite = Duration.ofMinutes(5);
        /** 默认读后过期 */
        private Duration expireAfterAccess = Duration.ofMinutes(10);

        public long getMaxSize() { return maxSize; }
        public void setMaxSize(long maxSize) { this.maxSize = maxSize; }
        public Duration getExpireAfterWrite() { return expireAfterWrite; }
        public void setExpireAfterWrite(Duration expireAfterWrite) { this.expireAfterWrite = expireAfterWrite; }
        public Duration getExpireAfterAccess() { return expireAfterAccess; }
        public void setExpireAfterAccess(Duration expireAfterAccess) { this.expireAfterAccess = expireAfterAccess; }
    }

    public static class L2 {
        /** Redis TTL */
        private Duration timeToLive = Duration.ofMinutes(30);
        /** 是否缓存 null(防穿透) */
        private boolean cacheNullValues = false;
        /** key 前缀 */
        private String keyPrefix = "plp:";
        /** 跨实例失效通知的 pub/sub 频道 */
        private String invalidationChannel = "plp:cache:invalidate";

        public Duration getTimeToLive() { return timeToLive; }
        public void setTimeToLive(Duration timeToLive) { this.timeToLive = timeToLive; }
        public boolean isCacheNullValues() { return cacheNullValues; }
        public void setCacheNullValues(boolean cacheNullValues) { this.cacheNullValues = cacheNullValues; }
        public String getKeyPrefix() { return keyPrefix; }
        public void setKeyPrefix(String keyPrefix) { this.keyPrefix = keyPrefix; }
        public String getInvalidationChannel() { return invalidationChannel; }
        public void setInvalidationChannel(String invalidationChannel) { this.invalidationChannel = invalidationChannel; }
    }

    public static class CacheSpec {
        private long maxSize = 10_000;
        private Duration expireAfterWrite = Duration.ofMinutes(5);
        private Duration redisTtl = Duration.ofMinutes(30);

        public long getMaxSize() { return maxSize; }
        public void setMaxSize(long maxSize) { this.maxSize = maxSize; }
        public Duration getExpireAfterWrite() { return expireAfterWrite; }
        public void setExpireAfterWrite(Duration expireAfterWrite) { this.expireAfterWrite = expireAfterWrite; }
        public Duration getRedisTtl() { return redisTtl; }
        public void setRedisTtl(Duration redisTtl) { this.redisTtl = redisTtl; }
    }
}
