package com.platform.common.rw;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 读写分离数据源配置项(app.rw.*, 默认关闭)。
 * - app.rw.enabled=true 才装配双 Hikari 池 + 路由数据源; 默认 false 不影响现有部署。
 * - write-url 指向 CRDB 主 svc(k8s/base/cockroachdb.yaml, NodePort 26257);
 *   read-url 指向 cockroachdb-read svc(NodePort 26267)或 CRDB follower/replica 节点。
 * - 未配 read-url 时自动降级为单池: READ 与 WRITE 共用主库连接(安全降级)。
 */
@ConfigurationProperties(prefix = "app.rw")
public class ReadWriteDataSourceProperties {

    /** 是否启用读写分离(默认 false)。 */
    private boolean enabled = false;

    /** 主库 JDBC URL。 */
    private String writeUrl;

    /** 副本 JDBC URL; 为空时降级为单池(READ 也走主库)。 */
    private String readUrl;

    private String username;
    private String password;
    private String driverClassName = "org.postgresql.Driver";

    private final Pool write = new Pool();
    private final Pool read = new Pool();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getWriteUrl() { return writeUrl; }
    public void setWriteUrl(String writeUrl) { this.writeUrl = writeUrl; }
    public String getReadUrl() { return readUrl; }
    public void setReadUrl(String readUrl) { this.readUrl = readUrl; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getDriverClassName() { return driverClassName; }
    public void setDriverClassName(String driverClassName) { this.driverClassName = driverClassName; }
    public Pool getWrite() { return write; }
    public Pool getRead() { return read; }

    /** 连接池参数。 */
    public static class Pool {
        /** 最大连接数 */
        private int maximumPoolSize = 10;
        /** 最小空闲连接数 */
        private int minimumIdle = 2;
        /** 连接获取超时 */
        private Duration connectionTimeout = Duration.ofSeconds(3);
        /** 空闲连接存活时间 */
        private Duration idleTimeout = Duration.ofMinutes(10);
        /** 连接最大寿命 */
        private Duration maxLifetime = Duration.ofMinutes(30);

        public int getMaximumPoolSize() { return maximumPoolSize; }
        public void setMaximumPoolSize(int maximumPoolSize) { this.maximumPoolSize = maximumPoolSize; }
        public int getMinimumIdle() { return minimumIdle; }
        public void setMinimumIdle(int minimumIdle) { this.minimumIdle = minimumIdle; }
        public Duration getConnectionTimeout() { return connectionTimeout; }
        public void setConnectionTimeout(Duration connectionTimeout) { this.connectionTimeout = connectionTimeout; }
        public Duration getIdleTimeout() { return idleTimeout; }
        public void setIdleTimeout(Duration idleTimeout) { this.idleTimeout = idleTimeout; }
        public Duration getMaxLifetime() { return maxLifetime; }
        public void setMaxLifetime(Duration maxLifetime) { this.maxLifetime = maxLifetime; }
    }
}
