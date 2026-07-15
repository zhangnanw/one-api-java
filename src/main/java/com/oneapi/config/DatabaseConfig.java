package com.oneapi.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * 数据库连接配置。
 * <p>
 * Step 5 起：DataSource 优先由 Spring Boot 自动配置创建，再通过
 * {@link #setDataSource(DataSource)} 注入。旧的 {@link #init} 方法在检测到
 * 已存在 DataSource 时会跳过，保证启动流程可以平滑过渡到 Spring 管理。
 */
@Slf4j
public class DatabaseConfig {
    private static DataSource dataSource;

    // ── helpers ────────────────────────────────────────────────

    private static HikariDataSource createPostgresDataSource(AppConfig.DatabaseYamlConfig db) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(String.format("jdbc:postgresql://%s:%d/%s", db.getHost(), db.getPort(), db.getDatabase()));
        config.setUsername(db.getUser());
        config.setPassword(db.getPassword());
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(10000);   // 10s
        config.setIdleTimeout(600000);       // 10min
        config.setMaxLifetime(1800000);      // 30min
        config.setConnectionTestQuery("SELECT 1");
        config.setKeepaliveTime(30000);       // 30s keepalive
        config.setValidationTimeout(5000);

        return new HikariDataSource(config);
    }

    private static void closeDataSource(DataSource ds) {
        if (ds instanceof HikariDataSource hds && !hds.isClosed()) {
            try { hds.close(); } catch (Exception ignored) { }
        }
    }

    // ── init ──────────────────────────────────────────────

    public static void init(AppConfig.DatabaseYamlConfig dbConfig) {
        if (dataSource != null) {
            log.info("DataSource already provided by Spring, skip legacy DatabaseConfig.init");
            return;
        }
        if (dbConfig == null) {
            dbConfig = new AppConfig.DatabaseYamlConfig();
        }
        initPostgres(dbConfig);
    }

    /** Backward-compatible overload for tests using an in-memory H2 database in PostgreSQL mode. */
    public static void init(String jdbcUrl) {
        if (dataSource != null) {
            log.info("DataSource already provided by Spring, skip legacy test DatabaseConfig.init");
            return;
        }
        String dbName = "legacy_test_" + Math.abs((long) (jdbcUrl != null ? jdbcUrl : "memory").hashCode());
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:" + dbName +
            ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;NON_KEYWORDS=MATCH");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(5);
        config.setConnectionTimeout(10000);
        closeDataSource(dataSource);
        dataSource = new HikariDataSource(config);
    }

    private static void initPostgres(AppConfig.DatabaseYamlConfig dbConfig) {
        try {
            log.info("Connecting to PostgreSQL: {}:{}/{} (user={})",
                dbConfig.getHost(), dbConfig.getPort(), dbConfig.getDatabase(), dbConfig.getUser());
            dataSource = createPostgresDataSource(dbConfig);
            try (Connection conn = dataSource.getConnection()) {
                log.info("PostgreSQL connected: {}:{}/{}",
                    dbConfig.getHost(), dbConfig.getPort(), dbConfig.getDatabase());
            }
        } catch (SQLException e) {
            log.error("PostgreSQL connection failed: {}", e.getMessage(), e);
            closeDataSource(dataSource);
            dataSource = null;
            throw new RuntimeException("PostgreSQL connection failed: " + e.getMessage(), e);
        }
    }

    /**
     * Step 5 新增：由 Spring Boot 注入已自动配置的 DataSource。
     */
    public static void setDataSource(DataSource ds) {
        if (ds != null) {
            log.info("DataSource provided by Spring: {}", ds.getClass().getName());
            dataSource = ds;
        }
    }

    public static DataSource getDataSource() {
        return dataSource;
    }
}
