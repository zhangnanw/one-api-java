package com.oneapi.repo;

import com.oneapi.model.VirtualModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 虚拟模型仓库 — 负责 virtual_models 表的 CRUD。
 * <p>
 * 虚拟模型是 OpenAI 模型名称到实际供应商模型的映射配置。
 * match 字段存储 JSON 格式的匹配规则，由 {@link com.oneapi.model.MatchRuleParser} 解析为类型化的 MatchRule。
 * <p>
 * 主要方法：
 * - {@link #findByName} — 按名称查找虚拟模型，用于 /v1/chat/completions 路由
 * - {@link #findAll} — 查找所有虚拟模型，用于 /v1/models 端点
 * - 未找到时返回 null
 * <p>
 * SQL 异常会被捕获并记录日志，返回 NOT_FOUND 或空列表（不会向上抛出异常）。
 */
public class VirtualModelRepo extends BaseRepo {
    private static final Logger log = LoggerFactory.getLogger(VirtualModelRepo.class);

    public VirtualModelRepo() {
        super();
    }

    /** For testing — inject custom DataSource. */
    public VirtualModelRepo(javax.sql.DataSource ds) {
        super(ds);
    }

    public List<VirtualModel> findAll() {
        List<VirtualModel> list = new ArrayList<>();
        String sql = "SELECT id, name, match FROM virtual_models ORDER BY id";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                VirtualModel virtualModel = new VirtualModel();
                virtualModel.setId(rs.getInt("id"));
                virtualModel.setName(rs.getString("name"));
                virtualModel.setMatch(rs.getString("match"));
                list.add(virtualModel);
            }
        } catch (SQLException e) {
            log.error("findAll virtual_models: {}", e.getMessage());
        }
        return list;
    }

    public VirtualModel findByName(String name) {
        String sql = "SELECT id, name, match FROM virtual_models WHERE name = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    VirtualModel virtualModel = new VirtualModel();
                    virtualModel.setId(rs.getInt("id"));
                    virtualModel.setName(rs.getString("name"));
                    virtualModel.setMatch(rs.getString("match"));
                    return virtualModel;
                }
            }
        } catch (SQLException e) {
            log.error("findByName {}: {}", name, e.getMessage());
        }
        return null;
    }

    public VirtualModel findById(int id) {
        String sql = "SELECT id, name, match FROM virtual_models WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    VirtualModel virtualModel = new VirtualModel();
                    virtualModel.setId(rs.getInt("id"));
                    virtualModel.setName(rs.getString("name"));
                    virtualModel.setMatch(rs.getString("match"));
                    return virtualModel;
                }
            }
        } catch (SQLException e) {
            log.error("findById {}: {}", id, e.getMessage());
        }
        return null;
    }

    public void insert(VirtualModel virtualModel) {
        String sql = "INSERT INTO virtual_models (name, match) VALUES (?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, virtualModel.getName());
            ps.setString(2, virtualModel.getMatch());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    virtualModel.setId(rs.getInt(1));
                }
            }
        } catch (SQLException e) {
            log.error("insert virtual_model: {}", e.getMessage(), e);
            throw new RuntimeException("DB write failed", e);
        }
    }

    public void updateMatch(int id, String matchJson) {
        String sql = "UPDATE virtual_models SET match = ? WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, matchJson);
            ps.setInt(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("update virtual_model {}: {}", id, e.getMessage());
            throw new RuntimeException("DB write failed", e);
        }
    }

    public void delete(int id) {
        String sql = "DELETE FROM virtual_models WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("delete virtual_model {}: {}", id, e.getMessage());
        }
    }
}
