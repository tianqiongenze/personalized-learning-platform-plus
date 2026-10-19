package com.platform.common.storage;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 双存储切换单测(TCG 铁律): 不连真库, 用 H2 内嵌库验证 type 解析 / 默认值 /
 * 互斥装配 / tidb url 组装 / 非法值快速失败 / enabled 开关。
 */
class StorageDataSourceAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(StorageDataSourceAutoConfiguration.class))
            .withUserConfiguration(TestStubConfig.class);

    /** 提供 JdbcTemplate class(装配 @ConditionalOnClass 需要), 内嵌 H2 兜底 DataSource。 */
    @Configuration(proxyBeanMethods = false)
    static class TestStubConfig {
        @Bean
        JdbcTemplate jdbcTemplate() {
            DataSource ds = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2)
                    .generateUniqueName(true).build();
            return new JdbcTemplate(ds);
        }
    }

    @Test
    void disabledByDefault_noDataSourceBeans() {
        runner.run(ctx -> {
            assertThat(ctx).doesNotHaveBean("cockroachdbDataSource");
            assertThat(ctx).doesNotHaveBean("tidbDataSource");
        });
    }

    @Test
    void typeParsing_defaultsToCockroachdb() {
        StorageProperties props = new StorageProperties();
        assertThat(props.getType()).isEqualTo("cockroachdb");
        assertThat(props.parsedType()).isEqualTo(StorageProperties.Type.COCKROACHDB);
    }

    @Test
    void typeParsing_tidbAndAliases() {
        StorageProperties props = new StorageProperties();
        props.setType("tidb");
        assertThat(props.parsedType()).isEqualTo(StorageProperties.Type.TIDB);
        props.setType(" crdb ");
        assertThat(props.parsedType()).isEqualTo(StorageProperties.Type.COCKROACHDB);
    }

    @Test
    void typeParsing_illegalValueFailsFast() {
        StorageProperties props = new StorageProperties();
        props.setType("mysql");
        assertThatThrownBy(props::parsedType)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cockroachdb | tidb");
    }

    @Test
    void enabledWithDefaultType_assemblesCrdbPool() {
        runner.withPropertyValues(
                        "app.storage.enabled=true",
                        // CRDB 组默认 URL 指向真实 CRDB — 单测覆盖为 H2(PG 兼容模式)验证装配与驱动选择, 不连真库
                        "app.storage.cockroachdb.url=jdbc:h2:mem:crdbtest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                        "app.storage.cockroachdb.driver-class-name=org.h2.Driver")
                .run(ctx -> {
                    assertThat(ctx).hasBean("cockroachdbDataSource");
                    assertThat(ctx).doesNotHaveBean("tidbDataSource");
                    DataSource ds = ctx.getBean("cockroachdbDataSource", DataSource.class);
                    // LazyConnectionDataSourceProxy 包裹: 类名验证路由层存在
                    assertThat(ds.getClass().getSimpleName()).isEqualTo("LazyConnectionDataSourceProxy");
                    try (Connection conn = ds.getConnection()) {
                        DatabaseMetaData meta = conn.getMetaData();
                        assertThat(conn.getMetaData().getURL()).contains("crdbtest");
                        assertThat(meta).isNotNull();
                    }
                });
    }

    @Test
    void enabledWithTidbType_assemblesTidbPool_andExclusive() {
        runner.withPropertyValues(
                        "app.storage.enabled=true",
                        "app.storage.type=tidb",
                        // 连真 TiDB 会失败 -> 指向 H2 内存 URL 验证"配置组选择与驱动装配"而非连通性
                        "app.storage.tidb.url=jdbc:h2:mem:tidbtest;MODE=MySQL;DB_CLOSE_DELAY=-1",
                        "app.storage.tidb.driver-class-name=org.h2.Driver")
                .run(ctx -> {
                    assertThat(ctx).hasBean("tidbDataSource");
                    assertThat(ctx).doesNotHaveBean("cockroachdbDataSource");
                    DataSource ds = ctx.getBean("tidbDataSource", DataSource.class);
                    try (Connection conn = ds.getConnection()) {
                        // MODE=MySQL 下 H2 仍报自身 driver name; 关键是 TiDB 组被选中且 URL 来自配置组
                        assertThat(conn.getMetaData().getURL()).contains("tidbtest");
                    }
                });
    }

    @Test
    void tidbDefaultUrl_pointsToInfraSvc() {
        StorageProperties props = new StorageProperties();
        assertThat(props.getTidb().getUrl())
                .startsWith("jdbc:mysql://tidb.infra:4000/")
                .contains("useSSL=false");
        assertThat(props.getTidb().getDriverClassName()).isEqualTo("com.mysql.cj.jdbc.Driver");
        assertThat(props.getCockroachdb().getUrl()).contains("jdbc:postgresql://");
        assertThat(props.getCockroachdb().getDriverClassName()).isEqualTo("org.postgresql.Driver");
    }

    @Test
    void poolIsHikariBeneathProxy() {
        runner.withPropertyValues("app.storage.enabled=true",
                        "app.storage.cockroachdb.url=jdbc:h2:mem:crdbtest2;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                        "app.storage.cockroachdb.driver-class-name=org.h2.Driver")
                .run(ctx -> {
            DataSource ds = ctx.getBean("cockroachdbDataSource", DataSource.class);
            // 反射解包 Lazy proxy 验证底层是 HikariDataSource(CLC: 追踪真实执行路径)
            Object target = unwrap(ds);
            assertThat(target).isInstanceOf(HikariDataSource.class);
            assertThat(((HikariDataSource) target).getPoolName()).isEqualTo("plpp-storage-crdb");
        });
    }

    private Object unwrap(DataSource ds) {
        try {
            Class<?> c = ds.getClass();
            while (c != null) {
                try {
                    java.lang.reflect.Field f = c.getDeclaredField("targetDataSource");
                    f.setAccessible(true);
                    return f.get(ds);
                } catch (NoSuchFieldException e) {
                    c = c.getSuperclass();
                }
            }
            throw new NoSuchFieldException("targetDataSource not found on " + ds.getClass());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
