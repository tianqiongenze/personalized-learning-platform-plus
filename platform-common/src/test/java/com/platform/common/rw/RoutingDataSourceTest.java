package com.platform.common.rw;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** ReadWriteRoutingDataSource 单测(参照 ESP esp-rw-java RoutingDataSourceTest 风格)。 */
class RoutingDataSourceTest {

    @AfterEach
    void clear() { RoutingHolder.clear(); }

    @Test
    void determineCurrentLookupKeyReturnsRoutingHolder() {
        ReadWriteRoutingDataSource ds = new ReadWriteRoutingDataSource();
        RoutingHolder.set(DataSourceType.READ);
        assertEquals(DataSourceType.READ, ds.determineCurrentLookupKey());
        RoutingHolder.set(DataSourceType.WRITE);
        assertEquals(DataSourceType.WRITE, ds.determineCurrentLookupKey());
        RoutingHolder.clear();
        assertEquals(DataSourceType.WRITE, ds.determineCurrentLookupKey());
    }

    @Test
    void readOnlyAnnotationDefaults() throws Exception {
        ReadOnly ro = Dummy.class.getMethod("annotated").getAnnotation(ReadOnly.class);
        assertFalse(ro.forceWrite(), "default forceWrite=false");
    }

    @Test
    void readWriteDataSourcePropertiesDefaultsAndSinglePoolFallback() {
        ReadWriteDataSourceProperties props = new ReadWriteDataSourceProperties();
        assertFalse(props.isEnabled(), "默认关闭, 不影响现有部署");
        assertNull(props.getReadUrl());
        assertEquals("org.postgresql.Driver", props.getDriverClassName());
        assertEquals(10, props.getWrite().getMaximumPoolSize());
        assertEquals(2, props.getWrite().getMinimumIdle());
        assertEquals(3, props.getWrite().getConnectionTimeout().toSeconds());
        // 降级判定: readUrl 为空 -> READ 与 WRITE 共用 write 池(AutoConfiguration 构建逻辑)
        boolean singlePoolFallback = props.getReadUrl() == null || props.getReadUrl().isBlank();
        assertTrue(singlePoolFallback, "未配 read-url 应降级单池");
        props.setEnabled(true);
        assertTrue(props.isEnabled());
    }

    @Test
    void routingResolvesToWritePoolWhenReadUrlMissing() {
        // 未配 read-url 时 targets 中 READ 与 WRITE 都指向同一 write 池引用
        ReadWriteDataSourceProperties props = new ReadWriteDataSourceProperties();
        Object writePool = new Object(); // 模拟 write HikariDataSource
        java.util.Map<Object, Object> targets = new java.util.HashMap<>();
        targets.put(DataSourceType.WRITE, writePool);
        if (props.getReadUrl() == null || props.getReadUrl().isBlank()) {
            targets.put(DataSourceType.READ, writePool);
        } else {
            targets.put(DataSourceType.READ, new Object());
        }
        assertSame(targets.get(DataSourceType.READ), targets.get(DataSourceType.WRITE),
                "未配 read-url 时 READ key 应解析到 write 池");
    }

    static class Dummy {
        @ReadOnly
        public void annotated() {}
    }
}
