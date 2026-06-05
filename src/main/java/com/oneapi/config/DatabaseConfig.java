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

    private static HikariDataSource createDataSource(String jdbcUrl) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setMaximumPoolSize(5);          // SQLite: keep it small
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

    private static void closeDataSource(HikariDataSource ds) {
        if (ds != null && !ds.isClosed()) {
            try { ds.close(); } catch (Exception ignored) { }
        }
    }

    // ── init with 3-tier fallback ──────────────────────────────

    public static void init(String jdbcUrl) {
        if (!jdbcUrl.startsWith("jdbc:")) {
            // SQLite path → JDBC URL
            jdbcUrl = "jdbc:sqlite:" + jdbcUrl;
        }

        // Tier 1: try existing database
        try {
            log.info("Connecting to database: {}", jdbcUrl);
            dataSource = createDataSource(jdbcUrl);
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL");
                stmt.execute("PRAGMA busy_timeout=5000");
                stmt.execute("PRAGMA foreign_keys=ON");
            }
            log.info("Database connected: {}", jdbcUrl);
            return;
        } catch (SQLException e) {
            log.error("Database connection failed: {}", e.getMessage());
            closeDataSource(dataSource);
            dataSource = null;
        }

        // Tier 2: delete corrupt DB file and retry (file-based SQLite only)
        if (jdbcUrl.startsWith("jdbc:sqlite:")
                && !jdbcUrl.startsWith("jdbc:sqlite::memory:")) {
            String dbPath = jdbcUrl.substring("jdbc:sqlite:".length());
            // strip query params if present
            int q = dbPath.indexOf('?');
            if (q >= 0) dbPath = dbPath.substring(0, q);

            try {
                boolean deleted = Files.deleteIfExists(Paths.get(dbPath));
                if (deleted) {
                    log.warn("DB corrupt, deleting and rebuilding: {}", dbPath);
                }
                dataSource = createDataSource(jdbcUrl);
                try (Connection conn = dataSource.getConnection();
                     Statement stmt = conn.createStatement()) {
                    stmt.execute("PRAGMA journal_mode=WAL");
                    stmt.execute("PRAGMA busy_timeout=5000");
                    stmt.execute("PRAGMA foreign_keys=ON");
                }
                log.info("Database rebuilt: {}", jdbcUrl);
                return;
            } catch (Exception e2) {
                log.error("DB rebuild failed: {}", e2.getMessage());
                closeDataSource(dataSource);
                dataSource = null;
            }
        }

        // Tier 3: in-memory fallback
        log.error("DB unrecoverable, falling back to in-memory database");
        dataSource = createDataSource("jdbc:sqlite::memory:");
        log.info("Database connected: jdbc:sqlite::memory: (fallback - original DB unrecoverable)");
    }

    public static DataSource getDataSource() {
        return dataSource;
    }
}
