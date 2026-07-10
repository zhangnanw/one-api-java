package com.oneapi.service;

import com.oneapi.model.HolographicRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;

/**
 * 全息调试日志 — 写入失败静默丢弃，不影响主链路。
 */
public class HolographicLogger {
    private static final Logger log = LoggerFactory.getLogger(HolographicLogger.class);
    private static final int MAX_RECORDS = 50;
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
            log.warn("HolographicLogger write failed: {}", e.getMessage());
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

    private static void enforceRingBuffer() throws SQLException {
        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement()) {
            var rs = stmt.executeQuery("SELECT COUNT(*) FROM holographic_logs");
            if (rs.next()) {
                int count = rs.getInt(1);
                if (count > MAX_RECORDS) {
                    int toDelete = count - MAX_RECORDS;
                    stmt.executeUpdate(
                        "DELETE FROM holographic_logs WHERE request_id IN " +
                        "(SELECT request_id FROM holographic_logs ORDER BY timestamp_ms ASC LIMIT " + toDelete + ")"
                    );
                }
            }
        }
    }

    public static DataSource getDataSource() {
        return ds;
    }
}
