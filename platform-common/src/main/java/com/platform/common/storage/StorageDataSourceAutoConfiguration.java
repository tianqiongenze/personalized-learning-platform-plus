package com.platform.common.storage;

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

/**
 * 双存储动态切换自动装配(参照 com.platform.common.rw.ReadWriteDataSourceAutoConfiguration 注册方式)。
 *
 * <p>默认关闭(app.storage.enabled=false), 不影响现有部署; 启用后按 app.storage.type 互斥装配:
 * <pre>
 * app:
 *   storage:
 *     enabled: true
 *     type: cockroachdb            # 或 tidb; 运行时 env APP_STORAGE_TYPE 一改即切
 *     cockroachdb:
 *       url: jdbc:postgresql://cockroachdb.infra:26257/plpp?sslmode=disable
 *       driver-class-name: org.postgresql.Driver
 *     tidb:
 *       url: jdbc:mysql://tidb.infra:4000/plpp?useSSL=false
 *       driver-class-name: com.mysql.cj.jdbc.Driver
 * </pre>
 *
 * <p>结构: 单 HikariDataSource(按 type 选配置组)+ LazyConnectionDataSourceProxy 包裹。
 * 与 app.rw 读写路由正交: storage 先选库, rw 再在库内做读写路由。
 * MySQL 驱动在 pom 中 optional — type=tidb 的宿主服务需引入 mysql-connector-j。
 */
@AutoConfiguration
@EnableConfigurationProperties(StorageProperties.class)
@ConditionalOnClass({HikariDataSource.class, JdbcTemplate.class})
@ConditionalOnProperty(prefix = "app.storage", name = "enabled", havingValue = "true")
public class StorageDataSourceAutoConfiguration {

    /** type=cockroachdb(默认): CRDB/PG 驱动 + CRDB 配置组。 */
    @Bean
    @ConditionalOnProperty(prefix = "app.storage", name = "type",
            havingValue = "cockroachdb", matchIfMissing = true)
    public DataSource cockroachdbDataSource(StorageProperties props,
                                            ObjectProvider<DataSource> existingDataSource) {
        StorageProperties.Cockroachdb c = props.getCockroachdb();
        return wrap(c.getUrl(), c.getDriverClassName(), c.getUsername(), c.getPassword(), "plpp-storage-crdb");
    }

    /** type=tidb: MySQL 驱动 + TiDB 配置组。 */
    @Bean
    @ConditionalOnProperty(prefix = "app.storage", name = "type", havingValue = "tidb")
    public DataSource tidbDataSource(StorageProperties props,
                                     ObjectProvider<DataSource> existingDataSource) {
        StorageProperties.TiDB t = props.getTidb();
        return wrap(t.getUrl(), t.getDriverClassName(), t.getUsername(), t.getPassword(), "plpp-storage-tidb");
    }

    private DataSource wrap(String url, String driver, String user, String password, String poolName) {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(url);
        cfg.setDriverClassName(driver);
        cfg.setUsername(user);
        cfg.setPassword(password);
        cfg.setPoolName(poolName);
        cfg.setMaximumPoolSize(10);
        cfg.setMinimumIdle(2);
        cfg.setConnectionTimeout(3_000);
        cfg.setIdleTimeout(600_000);
        cfg.setMaxLifetime(1_800_000);
        cfg.setAutoCommit(true);
        // -1: 池启动不阻塞在首次建连(切换窗口上游未就绪时 Bean 仍可创建, 首次 getConnection 才暴露失败)
        cfg.setInitializationFailTimeout(-1);
        // LazyConnectionDataSourceProxy: 延迟到 getConnection 才建连,
        // 切换期 old pool 停用/新 pool 就绪的窗口不阻塞 Bean 创建。
        return new LazyConnectionDataSourceProxy(new HikariDataSource(cfg));
    }
}
