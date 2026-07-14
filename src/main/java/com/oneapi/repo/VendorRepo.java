package com.oneapi.repo;

import com.oneapi.model.Vendor;
import com.oneapi.model.VendorWithCount;
import lombok.extern.slf4j.Slf4j;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Vendor 数据仓库。
 * <p>
 * 所有方法均使用 try-with-resources 管理 Connection/Statement，SQL 异常统一包装为 RuntimeException。
 * - {@link #findAll} 分页查询全部供应商（含禁用），按 id ASC 排序，支持 offset/limit。
 * - {@link #findAllActive} 仅查询 status=1 的启用供应商。
 * - {@link #findAllWithCounts} 关联 instances 表 LEFT JOIN，统计每个供应商的活跃实例数（status NOT IN 0,3,4,5）。
 * <p>
 * 所有查询返回空 list 而非 null；{@link #findById} 未找到时返回 null。
 */
@Slf4j
public class VendorRepo extends BaseRepo {

    public VendorRepo() {
        super();
    }

    /** For testing — inject custom DataSource. */
    public VendorRepo(javax.sql.DataSource ds) {
        super(ds);
    }

    /**
     * 分页查询全部供应商，按 id ASC 排序。
     * @param offset 跳过条数（0-based）
     * @param limit  最大返回条数
     * @return 匹配的供应商列表，不会返回 null（无结果时返回空 list）
     */
    public List<Vendor> findAll(int offset, int limit) {
        List<Vendor> list = new ArrayList<>();
        String sql = "SELECT id, name, description, status, " +
                     "\"group\", priority, created_time, base_url, api_key, balance_credential, meta " +
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
            throw new RuntimeException("DB read failed", e);
        }
        return list;
    }

    public List<VendorWithCount> findAllWithCounts(int offset, int limit) {
        List<VendorWithCount> list = new ArrayList<>();
        String sql = "SELECT v.id, v.name, v.description, v.status, " +
                     "v.\"group\", v.priority, v.created_time, v.base_url, v.api_key, v.balance_credential, v.meta, " +
                     "COUNT(i.id) AS instance_count " +
                     "FROM vendors v LEFT JOIN instances i ON v.id = i.vendor_id AND i.status NOT IN (0, 3, 4, 5) " +
                     "GROUP BY v.id, v.name, v.description, v.status, " +
                     "v.\"group\", v.priority, v.created_time, v.base_url, v.api_key, v.balance_credential, v.meta " +
                     "ORDER BY v.id LIMIT ? OFFSET ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ps.setInt(2, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Vendor vendor = map(rs);
                    int count = rs.getInt("instance_count");
                    list.add(new VendorWithCount(vendor, count));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("DB read failed", e);
        }
        return list;
    }

    public List<Vendor> findAllActive() {
        List<Vendor> list = new ArrayList<>();
        String sql = "SELECT id, name, description, status, " +
                     "\"group\", priority, created_time, base_url, api_key, balance_credential, meta " +
                     "FROM vendors WHERE status = 1 ORDER BY id";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("DB read failed", e);
        }
        return list;
    }

    /**
     * 按 ID 查询单个供应商。
     * @param id 供应商 ID
     * @return 匹配的 Vendor，未找到时返回 null
     */
    public Vendor findById(int id) {
        String sql = "SELECT id, name, description, status, " +
                     "\"group\", priority, created_time, base_url, api_key, balance_credential, meta " +
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
            throw new RuntimeException("DB read failed", e);
        }
        return null;
    }

    public void insert(Vendor vendor) {
        String sql = "INSERT INTO vendors (name, description, status, \"group\", " +
                     "priority, created_time, base_url, api_key, balance_credential, meta) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, vendor.getName());
            ps.setString(2, vendor.getDescription());
            ps.setInt(3, vendor.getStatus());
            ps.setString(4, vendor.getGroup());
            ps.setInt(5, vendor.getPriority());
            ps.setLong(6, System.currentTimeMillis() / 1000);
            ps.setString(7, vendor.getBaseUrl());
            ps.setString(8, vendor.getApiKey());
            ps.setString(9, vendor.getBalanceCredential());
            ps.setString(10, vendor.getMeta());
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("insert vendor: {}", e.getMessage(), e);
            throw new RuntimeException("DB write failed", e);
        }
    }

    public void update(int id, Vendor vendor) {
        String sql = "UPDATE vendors SET name=?, description=?, status=?, \"group\"=?, " +
                     "priority=?, base_url=?, balance_credential=?, meta=? WHERE id=?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, vendor.getName());
            ps.setString(2, vendor.getDescription());
            ps.setInt(3, vendor.getStatus());
            ps.setString(4, vendor.getGroup());
            ps.setInt(5, vendor.getPriority());
            ps.setString(6, vendor.getBaseUrl());
            ps.setString(7, vendor.getBalanceCredential());
            ps.setString(8, vendor.getMeta());
            ps.setInt(9, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("update vendor {}: {}", id, e.getMessage(), e);
            throw new RuntimeException("DB write failed", e);
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
            log.error("updateApiKey vendor {}: {}", id, e.getMessage(), e);
            throw new RuntimeException("DB write failed", e);
        }
    }

    public void delete(int id) {
        String sql = "DELETE FROM vendors WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("delete vendor {}: {}", id, e.getMessage(), e);
            throw new RuntimeException("DB write failed", e);
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
            throw new RuntimeException("DB read failed", e);
        }
        return 0;
    }

    private Vendor map(ResultSet rs) throws SQLException {
        Vendor vendor = new Vendor();
        vendor.setId(rs.getInt("id"));
        vendor.setName(rs.getString("name"));
        vendor.setDescription(rs.getString("description"));
        vendor.setStatus(rs.getInt("status"));
        vendor.setGroup(rs.getString("group"));
        vendor.setPriority(rs.getInt("priority"));
        vendor.setCreatedTime(rs.getLong("created_time"));
        vendor.setBaseUrl(rs.getString("base_url"));
        vendor.setApiKey(rs.getString("api_key"));
        vendor.setBalanceCredential(rs.getString("balance_credential"));
        vendor.setMeta(rs.getString("meta"));
        return vendor;
    }
}
