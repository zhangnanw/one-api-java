package com.oneapi.repo;

import com.oneapi.model.ModelSpec;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;

public class ModelCatalogRepo extends BaseRepo {
    private static final Logger log = LoggerFactory.getLogger(ModelCatalogRepo.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * 初始化表（如果不存在）。在启动时调用一次。
     */
    public void init() {
        String ddl = "CREATE TABLE IF NOT EXISTS model_catalog (" +
            "name TEXT PRIMARY KEY, " +
            "capabilities TEXT NOT NULL DEFAULT '[]', " +
            "context_window INTEGER NOT NULL DEFAULT 0, " +
            "input_rmb_per_m REAL NOT NULL DEFAULT 0, " +
            "output_rmb_per_m REAL NOT NULL DEFAULT 0)";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(ddl);
        } catch (SQLException e) {
            log.error("model_catalog init: {}", e.getMessage());
        }
    }

    public Optional<ModelSpec> findByName(String name) {
        String sql = "SELECT * FROM model_catalog WHERE name = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            log.error("model_catalog findByName: {}", e.getMessage());
        }
        return Optional.empty();
    }

    public List<ModelSpec> findAll() {
        List<ModelSpec> list = new ArrayList<>();
        String sql = "SELECT * FROM model_catalog ORDER BY name";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) {
            log.error("model_catalog findAll: {}", e.getMessage());
        }
        return list;
    }

    public void upsert(ModelSpec spec) {
        String sql = "INSERT INTO model_catalog(name, capabilities, context_window, input_rmb_per_m, output_rmb_per_m) " +
            "VALUES(?, ?, ?, ?, ?) " +
            "ON CONFLICT(name) DO UPDATE SET " +
            "capabilities=excluded.capabilities, " +
            "context_window=excluded.context_window, " +
            "input_rmb_per_m=excluded.input_rmb_per_m, " +
            "output_rmb_per_m=excluded.output_rmb_per_m";
        try {
            String capsJson = mapper.writeValueAsString(spec.capabilities());
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, spec.name());
                ps.setString(2, capsJson);
                ps.setInt(3, spec.contextWindow());
                ps.setDouble(4, spec.inputRmbPerM());
                ps.setDouble(5, spec.outputRmbPerM());
                ps.executeUpdate();
            }
        } catch (Exception e) {
            log.error("model_catalog upsert: {}", e.getMessage());
        }
    }

    private ModelSpec map(ResultSet rs) throws SQLException {
        try {
            Set<String> caps = mapper.readValue(
                rs.getString("capabilities"),
                new TypeReference<Set<String>>() {}
            );
            return new ModelSpec(
                rs.getString("name"),
                caps,
                rs.getInt("context_window"),
                rs.getDouble("input_rmb_per_m"),
                rs.getDouble("output_rmb_per_m")
            );
        } catch (Exception e) {
            throw new SQLException("map ModelSpec", e);
        }
    }
}
