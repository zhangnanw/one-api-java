package com.oneapi.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@Slf4j
public class DatabaseConfig {
    private static HikariDataSource dataSource;

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
        // 连接检测：取连接时测一次 + 后台每 30s 测一次
        config.setConnectionTestQuery("SELECT 1");
        config.setKeepaliveTime(30000);       // 30s keepalive（空闲连接保活）
        // 连接断开后快速重连，不累积死连接
        config.setValidationTimeout(5000);

        return new HikariDataSource(config);
    }

    private static void closeDataSource(HikariDataSource ds) {
        if (ds != null && !ds.isClosed()) {
            try { ds.close(); } catch (Exception ignored) { }
        }
    }

    // ── init ──────────────────────────────────────────────

    public static void init(AppConfig.DatabaseYamlConfig dbConfig) {
        if (dbConfig == null) {
            dbConfig = new AppConfig.DatabaseYamlConfig();
        }
        initPostgres(dbConfig);
    }

    /** Backward-compatible overload for tests using an in-memory H2 database in PostgreSQL mode. */
    public static void init(String jdbcUrl) {
        // SQLite is no longer supported; route old test URLs to a fresh H2 PostgreSQL-compatible DB.
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
            log.error("PostgreSQL connection failed: {}", e.getMessage());
            closeDataSource(dataSource);
            dataSource = null;
            throw new RuntimeException("PostgreSQL connection failed: " + e.getMessage(), e);
        }
    }


    public static DataSource getDataSource() {
        return dataSource;
    }
}
