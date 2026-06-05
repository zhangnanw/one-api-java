package com.oneapi.service;

import com.oneapi.model.RelayLog;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.nio.file.Paths;
import java.sql.*;

/**
 * RelayLogger — 独立 relay-log.db（与主 DB 分开，不抢锁）。
 * 完全对齐 Go 版 relay/chain/logger/db.go。
 */
public class RelayLogger {
    private static final Logger log = LoggerFactory.getLogger(RelayLogger.class);
    private static DataSource ds;

    public static void init() {
        String home = System.getProperty("user.home");
        String dbPath = Paths.get(home, ".one-api", "relay-log.db").toString();

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + dbPath);
        config.setMaximumPoolSize(3);
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
            stmt.execute("CREATE TABLE IF NOT EXISTS relay_logs (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "ts INTEGER, channel_id INTEGER, base_url TEXT, " +
                "token_name TEXT, user_id INTEGER, " +
                "model_orig TEXT, model_real TEXT, " +
                "stream INTEGER, body_size INTEGER, " +
                "code INTEGER, resp_size INTEGER, " +
                "tokens INTEGER, latency_ms INTEGER, " +
                "err TEXT)");
            log.info("relay-log.db ready at {}", dbPath);
        } catch (SQLException e) {
            log.error("relay-log.db init failed: {}", e.getMessage());
        }
    }

    /** 插入记录，返回自增 ID。失败返回 -1（静默）。 */
    public static long insert(RelayLog rlog) {
        if (ds == null) return -1;
        String sql = "INSERT INTO relay_logs (ts, channel_id, base_url, token_name, user_id, " +
            "model_orig, model_real, stream, body_size, code, resp_size, tokens, latency_ms, err) " +
            "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, rlog.ts);
            ps.setInt(2, rlog.instanceId);
            ps.setString(3, rlog.baseUrl);
            ps.setString(4, rlog.tokenName);
            ps.setInt(5, rlog.userId);
            ps.setString(6, rlog.modelOrig);
            ps.setString(7, rlog.modelReal);
            ps.setInt(8, rlog.stream ? 1 : 0);
            ps.setInt(9, rlog.bodySize);
            ps.setInt(10, rlog.code);
            ps.setInt(11, rlog.respSize);
            ps.setInt(12, rlog.tokens);
            ps.setLong(13, rlog.latencyMs);
            ps.setString(14, rlog.err);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
            }
        } catch (SQLException e) {
            log.debug("relay log insert failed: {}", e.getMessage());
        }
        return -1;
    }

    /** 流式结束后回填 tokens。静默失败。 */
    public static void updateTokens(long id, int tokens) {
        if (ds == null) return;
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE relay_logs SET tokens = ? WHERE id = ?")) {
            ps.setInt(1, tokens);
            ps.setLong(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.debug("relay log updateTokens failed: {}", e.getMessage());
        }
    }

    /** 流式结束后回填 code、tokens、latency。静默失败。 */
    public static void updateStreamResult(long id, int code, int tokens, long latencyMs) {
        if (ds == null) return;
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE relay_logs SET code = ?, tokens = ?, latency_ms = ?, err = NULL WHERE id = ?")) {
            ps.setInt(1, code);
            ps.setInt(2, tokens);
            ps.setLong(3, latencyMs);
            ps.setLong(4, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.debug("relay log updateStreamResult failed: {}", e.getMessage());
        }
    }
}
