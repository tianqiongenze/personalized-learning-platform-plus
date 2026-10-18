package com.platform.common.cache;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.cache.support.SimpleCacheManager;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 多级缓存自动装配
 * - 无 Redis 时自动退化为纯 Caffeine(本地开发零依赖)
 * - 有 Redis 时构建 TwoLevelCache 并启用失效广播
 * 需要缓存的业务类上使用 @Cacheable/@CacheEvict 即可, cacheManager 名为 "multiLevelCacheManager"
 */
@AutoConfiguration
@EnableConfigurationProperties(PlatformCacheProperties.class)
@ConditionalOnProperty(prefix = "app.cache", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MultiLevelCacheAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(RedisTemplate.class)
    public RedisTemplate<String, Object> cacheRedisTemplate(org.springframework.data.redis.connection.RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new Jackson2JsonRedisSerializer<>(Object.class));
        template.setHashValueSerializer(new Jackson2JsonRedisSerializer<>(Object.class));
        template.afterPropertiesSet();
        return template;
    }

    @Bean("caffeineCacheManager")
    @ConditionalOnMissingBean(CacheManager.class)
    @ConditionalOnProperty(prefix = "app.cache.l2", name = "enabled", havingValue = "false", matchIfMissing = false)
    public CacheManager caffeineCacheManager(PlatformCacheProperties props) {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(java.util.List.of(
            new CaffeineCache("default",
                Caffeine.newBuilder()
                    .maximumSize(props.getL1().getMaxSize())
                    .expireAfterWrite(props.getL1().getExpireAfterWrite())
                    .recordStats().build())));
        return manager;
    }

    @Bean("multiLevelCacheManager")
    @ConditionalOnMissingBean(CacheManager.class)
    public CacheManager multiLevelCacheManager(PlatformCacheProperties props,
                                               ObjectProvider<RedisTemplate<String, Object>> redisTemplate,
                                               ObjectProvider<org.springframework.data.redis.connection.RedisConnectionFactory> connectionFactory,
                                               ObjectProvider<MeterRegistry> meterRegistry) {
        try {
        SimpleCacheManager manager = new SimpleCacheManager();
        List<org.springframework.cache.Cache> caches = new ArrayList<>();
        List<TwoLevelCache> twoLevelCaches = new ArrayList<>();

        boolean redisAvailable = connectionFactory.getIfAvailable() != null && redisTemplate.getIfAvailable() != null;
        RedisTemplate<String, Object> l2 = redisTemplate.getIfAvailable();
        MeterRegistry registry = meterRegistry.getIfAvailable();
        CacheInvalidationPublisher publisher = (cacheName, key) -> { };
        if (redisAvailable) {
            // 延迟绑定: 装配完成后才知道全部 cache 列表
            publisher = new DeferredPublisher(connectionFactory.getIfAvailable(), props);
        }

        CacheInvalidationPublisher finalPublisher = publisher;
        if (props.getSpecs().isEmpty()) {
            // 默认至少提供 "default" 缓存, 业务可按需在配置中增加
            caches.add(createCache("default", props, l2, registry, finalPublisher, redisAvailable, twoLevelCaches));
        } else {
            for (Map.Entry<String, PlatformCacheProperties.CacheSpec> e : props.getSpecs().entrySet()) {
                caches.add(createCache(e.getKey(), props, l2, registry, finalPublisher, redisAvailable, twoLevelCaches));
            }
        }
        manager.setCaches(caches);
        if (finalPublisher instanceof DeferredPublisher dp) {
            dp.bind(twoLevelCaches);
        }
        return manager;
        } catch (Exception e) {
            // Redis/Caffeine 初始化失败时降级为纯 Caffeine
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("MultiLevelCache init failed, fallback to pure Caffeine", e);
            SimpleCacheManager fallback = new SimpleCacheManager();
            fallback.setCaches(java.util.List.of(
                new CaffeineCache("default",
                    com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
                        .maximumSize(10000)
                        .expireAfterWrite(java.time.Duration.ofMinutes(5))
                        .recordStats().build())));
            return fallback;
        }
    }

    private org.springframework.cache.Cache createCache(String name,
                                                        PlatformCacheProperties props,
                                                        RedisTemplate<String, Object> l2,
                                                        MeterRegistry registry,
                                                        CacheInvalidationPublisher publisher,
                                                        boolean redisAvailable,
                                                        List<TwoLevelCache> twoLevelCaches) {
        PlatformCacheProperties.CacheSpec spec = props.getSpecs().getOrDefault(name, new PlatformCacheProperties.CacheSpec());
        long maxSize = spec.getMaxSize() > 0 ? spec.getMaxSize() : props.getL1().getMaxSize();
        Duration writeTtl = spec.getExpireAfterWrite() != null ? spec.getExpireAfterWrite() : props.getL1().getExpireAfterWrite();
        Duration accessTtl = props.getL1().getExpireAfterAccess();
        if (redisAvailable && l2 != null) {
            TwoLevelCache c = new TwoLevelCache(name, maxSize, writeTtl, accessTtl, l2, props, registry, publisher);
            twoLevelCaches.add(c);
            return c;
        }
        return new CaffeineCache(name,
                com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
                        .maximumSize(maxSize)
                        .expireAfterWrite(writeTtl)
                        .expireAfterAccess(accessTtl)
                        .recordStats()
                        .build());
    }

    /** 延迟绑定: 装配完成后才知道全部 cache 列表 */
    static final class DeferredPublisher implements CacheInvalidationPublisher {
        private final org.springframework.data.redis.connection.RedisConnectionFactory factory;
        private final PlatformCacheProperties props;
        private volatile CacheInvalidationPublisher delegate;

        DeferredPublisher(org.springframework.data.redis.connection.RedisConnectionFactory factory, PlatformCacheProperties props) {
            this.factory = factory;
            this.props = props;
        }

        void bind(List<TwoLevelCache> caches) {
            this.delegate = new RedisCacheInvalidationPublisher(factory, props.getL2().getInvalidationChannel(), caches);
        }

        @Override public void publish(String cacheName, String key) {
            CacheInvalidationPublisher d = delegate;
            if (d != null) d.publish(cacheName, key);
        }
    }
}
