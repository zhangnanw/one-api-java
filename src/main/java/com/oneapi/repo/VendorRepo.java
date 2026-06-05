package com.oneapi.repo;

import com.oneapi.model.Vendor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class VendorRepo extends BaseRepo {
    private static final Logger log = LoggerFactory.getLogger(VendorRepo.class);

    public List<Vendor> findAll(int offset, int limit) {
        List<Vendor> list = new ArrayList<>();
        String sql = "SELECT id, name, description, status, " +
                     "\"group\", priority, created_time, base_url, api_key, meta " +
                     "FROM vendors ORDER BY id LIMIT ? OFFSET ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ps.setInt(2, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        } catch (SQLException e) {
            log.error("findAll vendors: {}", e.getMessage());
        }
        return list;
    }

    public List<Vendor> findAllActive() {
        List<Vendor> list = new ArrayList<>();
        String sql = "SELECT id, name, description, status, " +
                     "\"group\", priority, created_time, base_url, api_key, meta " +
                     "FROM vendors WHERE status = 1 ORDER BY id";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(map(rs));
            }
        } catch (SQLException e) {
            log.error("findAllActive: {}", e.getMessage());
        }
        return list;
    }

    public Vendor findById(int id) {
        String sql = "SELECT id, name, description, status, " +
                     "\"group\", priority, created_time, base_url, api_key, meta " +
                     "FROM vendors WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return map(rs);
                }
            }
        } catch (SQLException e) {
            log.error("findById vendor {}: {}", id, e.getMessage());
        }
        return null;
    }

    public void insert(Vendor v) {
        String sql = "INSERT INTO vendors (name, description, status, \"group\", " +
                     "priority, created_time, base_url, api_key, meta) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, v.getName());
            ps.setString(2, v.getDescription());
            ps.setInt(3, v.getStatus());
            ps.setString(4, v.getGroupName());
            ps.setInt(5, v.getPriority());
            ps.setLong(6, System.currentTimeMillis() / 1000);
            ps.setString(7, v.getBaseUrl());
            ps.setString(8, v.getApiKey());
            ps.setString(9, v.getMeta());
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("insert vendor: {}", e.getMessage());
        }
    }

    public void update(int id, Vendor v) {
        String sql = "UPDATE vendors SET name=?, description=?, status=?, \"group\"=?, " +
                     "priority=?, base_url=?, meta=? WHERE id=?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, v.getName());
            ps.setString(2, v.getDescription());
            ps.setInt(3, v.getStatus());
            ps.setString(4, v.getGroupName());
            ps.setInt(5, v.getPriority());
            ps.setString(6, v.getBaseUrl());
            ps.setString(7, v.getMeta());
            ps.setInt(8, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("update vendor {}: {}", id, e.getMessage());
        }
    }

    public void updateApiKey(int id, String apiKey) {
        String sql = "UPDATE vendors SET api_key = ? WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, apiKey);
            ps.setInt(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("updateApiKey vendor {}: {}", id, e.getMessage());
        }
    }

    public void delete(int id) {
        String sql = "DELETE FROM vendors WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("delete vendor {}: {}", id, e.getMessage());
        }
    }

    public int countInstances(int vendorId) {
        String sql = "SELECT COUNT(*) FROM instances WHERE vendor_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, vendorId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            log.error("countInstances vendor {}: {}", vendorId, e.getMessage());
        }
        return 0;
    }

    private Vendor map(ResultSet rs) throws SQLException {
        Vendor v = new Vendor();
        v.setId(rs.getInt("id"));
        v.setName(rs.getString("name"));
        v.setDescription(rs.getString("description"));
        v.setStatus(rs.getInt("status"));
        v.setGroupName(rs.getString("group"));
        v.setPriority(rs.getInt("priority"));
        v.setCreatedTime(rs.getLong("created_time"));
        v.setBaseUrl(rs.getString("base_url"));
        v.setApiKey(rs.getString("api_key"));
        v.setMeta(rs.getString("meta"));
        return v;
    }
}
