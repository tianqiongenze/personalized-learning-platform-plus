package com.platform.common.rw;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

/**
 * 读写分离路由数据源(参照 ESP esp-rw-java RoutingDataSource 落地):
 * 按 RoutingHolder 当前 Key(DataSourceType)选择主库或副本。
 * 继承 Spring AbstractRoutingDataSource, 与 JdbcTemplate/JPA 透明兼容。
 * 通常由 LazyConnectionDataSourceProxy 包裹, 延迟到真正 getConnection 才决定路由。
 */
public class ReadWriteRoutingDataSource extends AbstractRoutingDataSource {
    @Override
    protected Object determineCurrentLookupKey() {
        return RoutingHolder.get();
    }
}
