package com.platform.common.rw;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * 读写分离自动装配(参照 ESP esp-rw-java CockroachDbDataSourceConfig 落地)。
 *
 * 默认关闭(app.rw.enabled=false), 不影响现有部署; 启用方式:
 * <pre>
 * app:
 *   rw:
 *     enabled: true
 *     write-url: jdbc:postgresql://10.167.2.175:26257/plpp?sslmode=disable
 *     read-url:  jdbc:postgresql://10.167.2.175:26267/plpp?sslmode=disable   # cockroachdb-read svc / follower
 *     username: root
 *     password: ""
 * </pre>
 *
 * 结构: write HikariDataSource + read HikariDataSource -> RoutingDataSource
 * (lookup key = RoutingHolder) -> LazyConnectionDataSourceProxy 包裹为主 DataSource。
 * 未配 read-url 时降级单池: READ 与 WRITE 共用 write 池。
 */
@AutoConfiguration
@EnableConfigurationProperties(ReadWriteDataSourceProperties.class)
@ConditionalOnClass({HikariDataSource.class, JdbcTemplate.class})
@ConditionalOnProperty(prefix = "app.rw", name = "enabled", havingValue = "true")
public class ReadWriteDataSourceAutoConfiguration {

    @Bean
    public DataSource dataSource(ReadWriteDataSourceProperties props,
                                 ObjectProvider<DataSource> existingDataSource) {
        DataSource write = buildPool(props, props.getWriteUrl(), "plpp-rw-write");
        DataSource read = (props.getReadUrl() != null && !props.getReadUrl().isBlank())
                ? buildPool(props, props.getReadUrl(), "plpp-rw-read")
                : write; // 未配 read-url: 降级单池, READ 也走主库

        ReadWriteRoutingDataSource routing = new ReadWriteRoutingDataSource();
        Map<Object, Object> targets = new HashMap<>();
        targets.put(DataSourceType.WRITE, write);
        targets.put(DataSourceType.READ, read);
        routing.setTargetDataSources(targets);
        routing.setDefaultTargetDataSource(write);
        routing.afterPropertiesSet();

        // LazyConnectionDataSourceProxy: 延迟到真正 getConnection 才决定路由,
        // 使事务/Lazy 初始化路径上 @ReadOnly 的 ThreadLocal 标记先于取连接生效。
        return new LazyConnectionDataSourceProxy(routing);
    }

    private HikariDataSource buildPool(ReadWriteDataSourceProperties props, String url, String poolName) {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(url);
        cfg.setDriverClassName(props.getDriverClassName());
        cfg.setUsername(props.getUsername());
        cfg.setPassword(props.getPassword());
        ReadWriteDataSourceProperties.Pool pool = poolName.endsWith("read") ? props.getRead() : props.getWrite();
        cfg.setPoolName(poolName);
        cfg.setMaximumPoolSize(pool.getMaximumPoolSize());
        cfg.setMinimumIdle(pool.getMinimumIdle());
        cfg.setConnectionTimeout(pool.getConnectionTimeout().toMillis());
        cfg.setIdleTimeout(pool.getIdleTimeout().toMillis());
        cfg.setMaxLifetime(pool.getMaxLifetime().toMillis());
        cfg.setAutoCommit(true);
        return new HikariDataSource(cfg);
    }
}
