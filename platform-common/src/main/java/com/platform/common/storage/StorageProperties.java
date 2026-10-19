package com.platform.common.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 双存储(CRDB / TiDB)数据源配置项(app.storage.*, 参照 com.platform.common.rw 注册方式)。
 *
 * <p>动态切换语义: app.storage.type = cockroachdb(默认) | tidb。
 * 切换不改代码, 仅改运行时环境变量(APP_STORAGE_TYPE + SPRING_DATASOURCE_URL 组),
 * k8s env 一改即切。两套配置组显式分离(cockroachdb 组 / tidb 组), 防止"切了 type 忘了切 url"。
 *
 * <p>与读写分离(app.rw)正交: storage 决定"连哪个库", rw 决定"读写怎么路由"。
 * 两者同时启用时, storage 变更会改变 rw 两池的上游(CRDB svc 或 TiDB svc)。
 */
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

    /** 存储类型: cockroachdb(默认) | tidb。 */
    public enum Type { COCKROACHDB, TIDB }

    /** 当前存储类型(默认 cockroachdb, 保持现有部署行为不变)。 */
    private String type = "cockroachdb";

    /** CRDB(Postgres wire)连接组 — type=cockroachdb 时生效。 */
    private final Cockroachdb cockroachdb = new Cockroachdb();

    /** TiDB(MySQL wire)连接组 — type=tidb 时生效。 */
    private final TiDB tidb = new TiDB();

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Cockroachdb getCockroachdb() { return cockroachdb; }
    public TiDB getTidb() { return tidb; }

    /** 解析后的类型枚举; 非法值抛 IllegalArgumentException(装配前快速失败)。 */
    public Type parsedType() {
        String v = type == null ? "" : type.trim().toLowerCase();
        return switch (v) {
            case "cockroachdb", "crdb" -> Type.COCKROACHDB;
            case "tidb" -> Type.TIDB;
            default -> throw new IllegalArgumentException(
                    "app.storage.type 非法: '" + type + "', 允许值: cockroachdb | tidb");
        };
    }

    /** CRDB 连接组。 */
    public static class Cockroachdb {
        /** 默认与现有部署一致的 CRDB JDBC URL(Postgres wire)。 */
        private String url = "jdbc:postgresql://cockroachdb.infra:26257/plpp?sslmode=disable";
        private String username = "root";
        private String password = "";
        private String driverClassName = "org.postgresql.Driver";

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getDriverClassName() { return driverClassName; }
        public void setDriverClassName(String driverClassName) { this.driverClassName = driverClassName; }
    }

    /** TiDB 连接组(MySQL wire, 端口 4000)。 */
    public static class TiDB {
        /** 默认指向 infra ns 的 tidb svc(见 k8s/base/tidb.yaml)。 */
        private String url = "jdbc:mysql://tidb.infra:4000/plpp?useSSL=false&allowPublicKeyRetrieval=true&useUnicode=true&characterEncoding=utf8";
        private String username = "root";
        private String password = "";
        private String driverClassName = "com.mysql.cj.jdbc.Driver";
        /** TiDB 下事务隔离需显式声明(TiDB 默认 REPEATABLE READ; SI 语义与 CRDB SERIALIZABLE 差异见 ADR-004)。 */
        private Duration connectionTimeout = Duration.ofSeconds(3);

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getDriverClassName() { return driverClassName; }
        public void setDriverClassName(String driverClassName) { this.driverClassName = driverClassName; }
        public Duration getConnectionTimeout() { return connectionTimeout; }
        public void setConnectionTimeout(Duration connectionTimeout) { this.connectionTimeout = connectionTimeout; }
    }
}
