package com.oneapi.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseConfig {
    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);
    private static HikariDataSource dataSource;

    // ── helpers ────────────────────────────────────────────────

    private static HikariDataSource createSqliteDataSource(String jdbcUrl) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(5000);
        config.setIdleTimeout(300000);

        // SQLite optimizations
        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("busy_timeout", "5000");
        config.addDataSourceProperty("synchronous", "NORMAL");
        config.addDataSourceProperty("foreign_keys", "ON");

        return new HikariDataSource(config);
    }

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

        String dbType = dbConfig.getType();
        if ("postgresql".equalsIgnoreCase(dbType)) {
            initPostgres(dbConfig);
        } else {
            // default: sqlite
            String path = dbConfig.getPath();
            if (path == null || path.isEmpty()) {
                path = System.getProperty("user.home") + "/.one-api/one-api.db";
            }
            initSqlite(path);
        }
    }

    /** Backward-compatible overload for tests. */
    public static void init(String jdbcUrl) {
        if (!jdbcUrl.startsWith("jdbc:")) {
            jdbcUrl = "jdbc:sqlite:" + jdbcUrl;
        }
        AppConfig.DatabaseYamlConfig cfg = new AppConfig.DatabaseYamlConfig();
        cfg.setType("sqlite");
        cfg.setPath(jdbcUrl.replace("jdbc:sqlite:", ""));
        init(cfg);
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

    private static void initSqlite(String dbPath) {
        String jdbcUrl = "jdbc:sqlite:" + dbPath;

        // Tier 1: try existing database
        try {
            log.info("Connecting to SQLite: {}", jdbcUrl);
            dataSource = createSqliteDataSource(jdbcUrl);
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL");
                stmt.execute("PRAGMA busy_timeout=5000");
                stmt.execute("PRAGMA foreign_keys=ON");
            }
            log.info("SQLite connected: {}", jdbcUrl);
            return;
        } catch (SQLException e) {
            log.error("SQLite connection failed: {}", e.getMessage());
            closeDataSource(dataSource);
            dataSource = null;
        }

        // Tier 2: delete corrupt DB file and retry
        if (!jdbcUrl.startsWith("jdbc:sqlite::memory:")) {
            String filePath = jdbcUrl.substring("jdbc:sqlite:".length());
            int q = filePath.indexOf('?');
            if (q >= 0) filePath = filePath.substring(0, q);

            try {
                boolean deleted = Files.deleteIfExists(Paths.get(filePath));
                if (deleted) {
                    log.warn("SQLite DB corrupt, deleting and rebuilding: {}", filePath);
                }
                dataSource = createSqliteDataSource(jdbcUrl);
                try (Connection conn = dataSource.getConnection();
                     Statement stmt = conn.createStatement()) {
                    stmt.execute("PRAGMA journal_mode=WAL");
                    stmt.execute("PRAGMA busy_timeout=5000");
                    stmt.execute("PRAGMA foreign_keys=ON");
                }
                log.info("SQLite rebuilt: {}", jdbcUrl);
                return;
            } catch (Exception e2) {
                log.error("SQLite rebuild failed: {}", e2.getMessage());
                closeDataSource(dataSource);
                dataSource = null;
            }
        }

        // Tier 3: in-memory fallback
        log.error("SQLite unrecoverable, falling back to in-memory database");
        dataSource = createSqliteDataSource("jdbc:sqlite::memory:");
        log.info("SQLite connected: :memory: (fallback)");
    }

    public static boolean isPostgreSQL() {
        if (dataSource == null) return false;
        String url = dataSource.getJdbcUrl();
        return url != null && url.startsWith("jdbc:postgresql:");
    }

    public static DataSource getDataSource() {
        return dataSource;
    }
}
