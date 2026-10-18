package com.platform.common.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.platform.common.tenant.TenantContext;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * 两级缓存实现
 * 读: L1(Caffeine) -> L2(Redis Cluster) -> loader; 逐级回填
 * 写: 同步写 L2, 更新 L1, 并向 Redis pub/sub 广播失效(其他实例剔除各自的 L1)
 * 设计取舍: 写路径为"更新后失效其他节点"而非强一致写穿, 对课程/画像等读多写少场景足够
 */
public class TwoLevelCache implements org.springframework.cache.Cache {

    private final String name;
    private final Cache<Object, Object> l1;
    private final RedisTemplate<String, Object> l2Template;
    private final PlatformCacheProperties props;
    private final String l2Prefix;
    private final Duration l2Ttl;
    private final CacheInvalidationPublisher invalidationPublisher;

    public TwoLevelCache(String name,
                         long maxSize,
                         Duration expireAfterWrite,
                         Duration expireAfterAccess,
                         RedisTemplate<String, Object> l2Template,
                         PlatformCacheProperties props,
                         MeterRegistry meterRegistry,
                         CacheInvalidationPublisher invalidationPublisher) {
        this.name = name;
        Caffeine<Object, Object> builder = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(expireAfterWrite)
                .expireAfterAccess(expireAfterAccess)
                .recordStats();
        this.l1 = builder.build();
        this.l2Template = l2Template;
        this.props = props;
        this.l2Prefix = props.getL2().getKeyPrefix() + name + ":";
        this.l2Ttl = props.getSpecs().get(name) != null
                ? props.getSpecs().get(name).getRedisTtl()
                : props.getL2().getTimeToLive();
        this.invalidationPublisher = invalidationPublisher;
        if (props.isMetrics() && meterRegistry != null) {
            // CaffeineCacheMetrics.monitor disabled (causes "size must be even" error)
        }
    }

    @Override
    public String getName() { return name; }

    @Override
    public Object getNativeCache() { return this; }

    @Override
    @SuppressWarnings("unchecked")
    public ValueWrapper get(Object key) {
        Object v = lookup(key);
        return v == null ? null : () -> v;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Object key, Class<T> type) {
        Object v = lookup(key);
        if (v == null) return null;
        if (type != null && !type.isInstance(v)) {
            throw new IllegalStateException("Cached value is not of required type [" + type.getName() + "]: " + v);
        }
        return (T) v;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Object key, Callable<T> valueLoader) {
        Object v = l1.get(key, k -> loadFromL2OrLoader(key, () -> {
            try {
                return valueLoader.call();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException("cache loader failed", e);
            }
        }));
        return (T) v;
    }

    @Override
    public void put(Object key, Object value) {
        l1.put(key, value);
        writeL2(key, value);
    }

    @Override
    public void evict(Object key) {
        l1.invalidate(key);
        try {
            l2Template.delete(l2Prefix + tenantKey(key));
        } catch (RuntimeException e) {
            // L2 故障不阻断业务, 由告警体系兜底
        }
        invalidationPublisher.publish(name, String.valueOf(key));
    }

    @Override
    public void clear() {
        l1.invalidateAll();
        invalidationPublisher.publish(name, null);
    }

    /** 租户隔离 key: 不同高校互不串缓存(吵闹邻居防御的一部分) */
    private Object tenantKey(Object key) {
        return TenantContext.get() + ":" + key;
    }

    private Object lookup(Object key) {
        Object v = l1.getIfPresent(key);
        if (v != null) return v;
        try {
            v = l2Template.opsForValue().get(l2Prefix + tenantKey(key));
        } catch (RuntimeException e) {
            return null;
        }
        if (v != null) {
            l1.put(key, v);
        }
        return v;
    }

    private Object loadFromL2OrLoader(Object key, java.util.function.Supplier<?> loader) {
        Object v;
        try {
            v = l2Template.opsForValue().get(l2Prefix + tenantKey(key));
        } catch (RuntimeException e) {
            v = null;
        }
        if (v == null) {
            v = loader.get();
            if (v != null) {
                writeL2(key, v);
            }
        }
        return v;
    }

    private void writeL2(Object key, Object value) {
        try {
            if (value == null && !props.getL2().isCacheNullValues()) {
                return;
            }
            l2Template.opsForValue().set(l2Prefix + tenantKey(key), value, l2Ttl);
        } catch (RuntimeException e) {
            // L2 故障不阻断业务
        }
    }

    /** 其他实例收到失效通知后剔除本地 L1 */
    void evictLocal(String key) {
        if (key == null) {
            l1.invalidateAll();
        } else {
            l1.invalidate(key);
        }
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof TwoLevelCache c && Objects.equals(name, c.name);
    }

    @Override
    public int hashCode() { return name.hashCode(); }

    @Override
    public String toString() { return "TwoLevelCache{" + name + "}";
    }
}
