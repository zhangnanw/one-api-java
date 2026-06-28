package com.oneapi.service;

import com.oneapi.model.HolographicRecord;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Vertx;
import javax.sql.DataSource;
import java.nio.file.Paths;
import java.sql.*;

/**
 * 全息调试日志 — 独立 SQLite 数据库，环形缓冲区 50 条。
 * <p>
 * 与 relay-log.db 和主 DB 完全隔离，独立连接池。
 * 写入失败静默丢弃，不影响主链路。
 */
public class HolographicLogger {
    private static final Logger log = LoggerFactory.getLogger(HolographicLogger.class);
    private static final int MAX_RECORDS = 50;
    private static DataSource ds;

    public static void init() {
        String home = System.getProperty("user.home");
        String dbPath = Paths.get(home, ".one-api", "holographic-debug.db").toString();

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + dbPath);
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(5000);
        config.setIdleTimeout(300000);
        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("busy_timeout", "5000");
        config.addDataSourceProperty("synchronous", "NORMAL");

        ds = new HikariDataSource(config);

        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL");
            stmt.execute("CREATE TABLE IF NOT EXISTS holographic_logs (" +
                "id              INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "request_id      TEXT UNIQUE NOT NULL, " +
                "timestamp_ms    INTEGER NOT NULL, " +
                "requested_model TEXT, " +
                "final_status    TEXT, " +
                "final_http_code INTEGER, " +
                "total_latency_ms INTEGER, " +
                "total_tokens    INTEGER, " +
                "data            TEXT NOT NULL" +
                ")");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_hl_ts " +
                "ON holographic_logs(timestamp_ms)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_hl_model " +
                "ON holographic_logs(requested_model)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_hl_status " +
                "ON holographic_logs(final_status)");
            log.info("holographic-debug.db ready at {}", dbPath);
        } catch (SQLException e) {
            log.error("holographic-debug.db init failed: {}", e.getMessage());
        }
    }

    public static void write(HolographicRecord record) {
        if (ds == null || record == null) return;
        try {
            insert(record);
            enforceRingBuffer();
        } catch (Exception e) {
            log.warn("HolographicLogger write failed: {}", e.getMessage());
        }
    }

    /**
     * 异步写入全息日志，避免阻塞事件循环。
     * 在 worker 线程执行同步写入逻辑。
     */
    public static void writeAsync(Vertx vertx, HolographicRecord record) {
        if (vertx == null || record == null) return;
        vertx.executeBlocking(promise -> {
            write(record);
            promise.complete();
        }, false, null);  // false = 不在事件循环线程执行
    }

    private static void insert(HolographicRecord record) throws SQLException {
        String sql = "INSERT OR REPLACE INTO holographic_logs " +
            "(request_id, timestamp_ms, requested_model, final_status, " +
            "final_http_code, total_latency_ms, total_tokens, data) " +
            "VALUES (?,?,?,?,?,?,?,?)";
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, record.requestId());
            ps.setLong(2, record.timestampMs());
            ps.setString(3, record.requestedModel());
            ps.setString(4, record.finalStatus());
            ps.setInt(5, record.finalHttpCode());
            ps.setLong(6, record.totalLatencyMs());
            ps.setInt(7, record.totalTokens());
            ps.setString(8, record.toJson());
            ps.executeUpdate();
        }
    }

    private static void enforceRingBuffer() throws SQLException {
        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement()) {
            var rs = stmt.executeQuery("SELECT COUNT(*) FROM holographic_logs");
            if (rs.next()) {
                int count = rs.getInt(1);
                if (count > MAX_RECORDS) {
                    int toDelete = count - MAX_RECORDS;
                    stmt.executeUpdate(
                        "DELETE FROM holographic_logs WHERE id IN " +
                        "(SELECT id FROM holographic_logs ORDER BY id ASC LIMIT " + toDelete + ")"
                    );
                }
            }
        }
    }

    public static DataSource getDataSource() {
        return ds;
    }
}
