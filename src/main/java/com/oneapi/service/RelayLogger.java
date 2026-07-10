package com.oneapi.service;

import com.oneapi.model.RelayLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;

/**
 * RelayLogger — 请求日志写入。
 */
public class RelayLogger {
    private static final Logger log = LoggerFactory.getLogger(RelayLogger.class);
    private static DataSource ds;

    public static void init(DataSource dataSource) {
        ds = dataSource;
        log.info("RelayLogger initialized");
    }

    /** 插入记录，返回自增 ID。失败返回 -1（静默）。 */
    public static long insert(RelayLog rlog) {
        if (ds == null) return -1;
        String sql = "INSERT INTO relay_logs (ts, channel_id, base_url, token_name, user_id, " +
              "model_orig, model_real, stream, body_size, code, resp_size, tokens, latency_ms, err) " +
              "VALUES (to_timestamp(?),?,?,?,?,?,?,?,?,?,?,?,?,?) RETURNING id";
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, rlog.getTimestamp());
            setCommonParams(ps, rlog, 2);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
            }
        } catch (SQLException e) {
            log.debug("relay log insert failed: {}", e.getMessage());
        }
        return -1;
    }

    private static void setCommonParams(PreparedStatement ps, RelayLog rlog, int start) throws SQLException {
        ps.setInt(start, rlog.getInstanceId());
        ps.setString(start + 1, rlog.getBaseUrl());
        ps.setString(start + 2, rlog.getTokenName());
        ps.setInt(start + 3, rlog.getUserId());
        ps.setString(start + 4, rlog.getModelOrig());
        ps.setString(start + 5, rlog.getUpstreamModel());
        ps.setInt(start + 6, rlog.isStream() ? 1 : 0);
        ps.setInt(start + 7, rlog.getBodySize());
        ps.setInt(start + 8, rlog.getHttpStatus());
        ps.setInt(start + 9, rlog.getRespSize());
        ps.setInt(start + 10, rlog.getTokens());
        ps.setLong(start + 11, rlog.getLatencyMs());
        ps.setString(start + 12, rlog.getErrorMessage());
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

    /** 流式结束后回填 code、tokens、latency + error。静默失败。 */
    public static void updateStreamResult(long id, int code, int tokens, long latencyMs) {
        updateStreamResult(id, code, tokens, latencyMs, null);
    }

    /** 同上，附带错误消息。 */
    public static void updateStreamResult(long id, int code, int tokens, long latencyMs, String err) {
        if (ds == null) return;
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE relay_logs SET code = ?, tokens = ?, latency_ms = ?, err = ? WHERE id = ?")) {
            ps.setInt(1, code);
            ps.setInt(2, tokens);
            ps.setLong(3, latencyMs);
            ps.setString(4, err);
            ps.setLong(5, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.debug("relay log updateStreamResult failed: {}", e.getMessage());
        }
    }
}
