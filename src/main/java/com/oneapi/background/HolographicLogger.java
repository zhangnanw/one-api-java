package com.oneapi.background;

import com.oneapi.model.HolographicRecord;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.*;

/**
 * 全息调试日志 — 写入失败静默丢弃，不影响主链路。
 */
@Slf4j
public class HolographicLogger {
    private static DataSource ds;

    public static void init(DataSource dataSource) {
        ds = dataSource;
        log.info("HolographicLogger initialized");
    }

    public static void write(HolographicRecord record) {
        if (ds == null || record == null) return;
        try {
            insert(record);
        } catch (Exception e) {
            log.warn("HolographicLogger write failed: {}", e.getMessage(), e);
        }
    }

    private static void insert(HolographicRecord record) throws SQLException {
        String sql = "INSERT INTO holographic_logs (request_id, timestamp_ms, requested_model, final_status, " +
              "final_http_code, total_latency_ms, total_tokens, data) " +
              "VALUES (?, to_timestamp(?/1000.0), ?, ?, ?, ?, ?, ?::jsonb) " +
              "ON CONFLICT (request_id) DO UPDATE SET " +
              "timestamp_ms = EXCLUDED.timestamp_ms, " +
              "requested_model = EXCLUDED.requested_model, " +
              "final_status = EXCLUDED.final_status, " +
              "final_http_code = EXCLUDED.final_http_code, " +
              "total_latency_ms = EXCLUDED.total_latency_ms, " +
              "total_tokens = EXCLUDED.total_tokens, " +
              "data = EXCLUDED.data";
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

    public static DataSource getDataSource() {
        return ds;
    }
}
