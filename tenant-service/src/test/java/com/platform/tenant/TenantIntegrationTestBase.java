package com.platform.tenant;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 集成测试基类: 以 Testcontainers 拉起 PostgreSQL + Redis。
 *
 * 修复记录(本轮):
 *  原实现依赖 {@code org.springframework.boot.testcontainers.service.connection.ServiceConnection}
 *  的 @ServiceConnection, 但 {@code spring-boot-starter-test} 3.5.6 的 POM 并不传递
 *  {@code spring-boot-testcontainers}, 本地仓库亦无该构件 —— 该文件无法编译,
 *  造成 tenant-service 整个测试模块编译失败, 95 个用例从未真正执行过。
 *  现改为标准 {@code @DynamicPropertySource} 注入容器连接信息, 仅依赖已在测试依赖树中的
 *  {@code testcontainers} core, 离线可编译。
 *
 * 运行条件: 需要本机 Docker daemon。离线构建环境无 Docker 时该用例会在容器启动阶段失败,
 * 故 {@code mvn test} 默认按 surefire 配置跳过 *IntegrationTest, 需本机有 Docker 时执行
 * {@code mvn verify}。
 */
@Testcontainers
public abstract class TenantIntegrationTestBase {

    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("tenant_db")
            .withUsername("postgres")
            .withPassword("postgres");

    protected static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        // 集成测试不走 Flyway(社区版不支持 CockroachDB, 见 platform-common 自定义迁移器)
        registry.add("spring.flyway.enabled", () -> false);
    }
}
