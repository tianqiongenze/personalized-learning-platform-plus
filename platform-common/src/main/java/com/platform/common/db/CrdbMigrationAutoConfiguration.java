package com.platform.common.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CRDB 迁移自动装配(替代 Flyway) — 生产级版本管理。
 *
 * 根因: Flyway 11.7.2 社区版仅内置 36 个数据库支持类, CockroachDB 属 Teams 付费版,
 * 启动即抛 "Unsupported Database: CockroachDB"。CRDB 兼容 PostgreSQL wire 协议且支持
 * 事务性 DDL, 自研 runner 比 ddl-auto=update 更可控(可审计/可回滚/可加 seed 数据)。
 *
 * 特性:
 *  - 版本表 flyway_schema_history, 幂等跟踪 + checksum 防篡改告警
 *  - classpath:db/migration/V{n}__{desc}.sql 按版本号排序执行
 *  - 单脚本单事务(CRDB 支持事务性 DDL, 失败整体回滚)
 *  - app.migration.enabled=false 一键关闭(如 CI 环境)
 */
@AutoConfiguration
@ConditionalOnClass(DataSource.class)
@ConditionalOnBean(JdbcTemplate.class)
@ConditionalOnProperty(prefix = "app.migration", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CrdbMigrationAutoConfiguration {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public CommandLineRunner crdbMigrationRunner(JdbcTemplate jdbcTemplate,
                                                 @Value("${app.migration.locations:classpath:db/migration}") String locations) {
        return new CrdbMigrationRunner(jdbcTemplate, locations);
    }

    /** 单脚本单事务执行器 */
    static class CrdbMigrationRunner implements CommandLineRunner {

        private static final Logger log = LoggerFactory.getLogger(CrdbMigrationRunner.class);
        private static final Pattern VERSION_PATTERN = Pattern.compile("V(\\d+)__([\\w-]+)\\.sql");

        private final JdbcTemplate jdbcTemplate;
        private final String locations;

        CrdbMigrationRunner(JdbcTemplate jdbcTemplate, String locations) {
            this.jdbcTemplate = jdbcTemplate;
            this.locations = locations;
        }

        @Override
        public void run(String... args) throws Exception {
            ensureHistoryTable();
            List<MigrationScript> scripts = discoverScripts();
            int applied = 0;
            for (MigrationScript script : scripts) {
                if (isApplied(script)) {
                    verifyChecksum(script);
                    continue;
                }
                long t0 = System.currentTimeMillis();
                executeInTransaction(script);
                long elapsed = System.currentTimeMillis() - t0;
                recordApplied(script, elapsed);
                applied++;
                log.info("[migration] applied V{}__{} ({}ms)", script.version(), script.description(), elapsed);
            }
            log.info("[migration] done: {} scripts total, {} newly applied", scripts.size(), applied);
        }

        private void ensureHistoryTable() {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS flyway_schema_history (
                        installed_rank INT PRIMARY KEY,
                        version VARCHAR(50) NOT NULL,
                        description VARCHAR(200) NOT NULL,
                        type VARCHAR(20) NOT NULL DEFAULT 'SQL',
                        script VARCHAR(200) NOT NULL,
                        checksum VARCHAR(64),
                        installed_by VARCHAR(100) NOT NULL DEFAULT 'migration-runner',
                        installed_on TIMESTAMPTZ NOT NULL DEFAULT now(),
                        execution_time INT NOT NULL DEFAULT 0,
                        success BOOLEAN NOT NULL DEFAULT TRUE
                    )""");
        }

        private List<MigrationScript> discoverScripts() throws Exception {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            String location = locations.replace("classpath:", "classpath*:");
            Resource[] resources = resolver.getResources(location + "/*.sql");
            List<MigrationScript> scripts = new ArrayList<>();
            for (Resource r : resources) {
                String filename = r.getFilename();
                if (filename == null) continue;
                Matcher m = VERSION_PATTERN.matcher(filename);
                if (!m.matches()) {
                    log.warn("[migration] skip non-versioned file: {}", filename);
                    continue;
                }
                String sql = new String(r.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                scripts.add(new MigrationScript(Integer.parseInt(m.group(1)), m.group(2), filename, sql, sha256(sql)));
            }
            scripts.sort((a, b) -> Integer.compare(a.version(), b.version()));
            return scripts;
        }

        private boolean isApplied(MigrationScript script) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE version = ? AND success = TRUE",
                    Integer.class, String.valueOf(script.version()));
            return count != null && count > 0;
        }

        private void verifyChecksum(MigrationScript script) {
            String stored = jdbcTemplate.queryForObject(
                    "SELECT checksum FROM flyway_schema_history WHERE version = ?",
                    String.class, String.valueOf(script.version()));
            if (stored != null && !stored.equals(script.checksum())) {
                log.warn("[migration] checksum mismatch for V{}__{}: 脚本已修改, 保留历史不重放",
                        script.version(), script.description());
            }
        }

        private void executeInTransaction(MigrationScript script) {
            jdbcTemplate.execute("BEGIN");
            try {
                for (String statement : splitStatements(script.sql())) {
                    jdbcTemplate.execute(statement);
                }
                jdbcTemplate.execute("COMMIT");
            } catch (Exception e) {
                try {
                    jdbcTemplate.execute("ROLLBACK");
                } catch (Exception ignored) { }
                throw new IllegalStateException(
                        "migration V" + script.version() + "__" + script.description() + " failed: " + e.getMessage(), e);
            }
        }

        private void recordApplied(MigrationScript script, long elapsedMs) {
            jdbcTemplate.update(
                    "INSERT INTO flyway_schema_history (installed_rank, version, description, script, checksum, execution_time, success) " +
                            "VALUES ((SELECT COALESCE(MAX(installed_rank),0)+1 FROM flyway_schema_history), ?, ?, ?, ?, ?, TRUE)",
                    String.valueOf(script.version()), script.description(), script.filename(), script.checksum(), (int) elapsedMs);
        }

        /** 按分号切分语句, 跳过纯注释行; 不处理函数体内分号(迁移脚本约定避免) */
        private List<String> splitStatements(String sql) {
            List<String> statements = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            for (String line : sql.split("\n")) {
                String trimmed = line.trim();
                if (trimmed.startsWith("--")) continue;
                current.append(line).append("\n");
                if (trimmed.endsWith(";")) {
                    String stmt = current.toString().trim();
                    if (stmt.endsWith(";")) stmt = stmt.substring(0, stmt.length() - 1);
                    if (!stmt.isBlank()) statements.add(stmt);
                    current.setLength(0);
                }
            }
            String rest = current.toString().trim();
            if (!rest.isBlank()) statements.add(rest);
            return statements;
        }

        private String sha256(String content) {
            try {
                java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
                byte[] hash = md.digest(content.getBytes(StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                for (byte b : hash) sb.append(String.format("%02x", b));
                return sb.toString();
            } catch (Exception e) {
                return "unknown";
            }
        }
    }

    record MigrationScript(int version, String description, String filename, String sql, String checksum) {}
}
