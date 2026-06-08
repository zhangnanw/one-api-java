package com.oneapi.repo;

import com.oneapi.model.Instance;
import com.oneapi.model.Vendor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 实例仓库。
 * <p>
 * 查找语义：
 * - {@link #findByName}：按 model_name 精确匹配，可能返回多个（一个模型可被多个供应商供应）
 * - {@link #findAll}、{@link #findAllWithVendor}：全表按 id 升序
 * - {@link #findAllWithVendor}：LEFT JOIN vendors 用于候选排序时获取 vendor 信息
 * <p>
 * 状态字段（{@code status}）语义：1=RAW（自动发现）、2=TAGGED（手动配置）、
 * 3=DISABLED（管理员停用）、4=DEPRECATED（上游不再提供）。
 * <p>
 * 错误处理：SQLException 一律 log 后返回空集合或 null。
 */
public class InstanceRepo extends BaseRepo {
    private static final Logger log = LoggerFactory.getLogger(InstanceRepo.class);

    public InstanceRepo() {
        super();
    }

    /** For testing — inject custom DataSource. */
    InstanceRepo(javax.sql.DataSource ds) {
        super(ds);
    }

    public static final int STATUS_RAW = 1;
    public static final int STATUS_TAGGED = 2;
    public static final int STATUS_DISABLED = 3;
    public static final int STATUS_DEPRECATED = 4;

    /**
     * Load all instances with vendor preloaded (used by router).
     */
    public List<Instance> findAllWithVendor() {
        List<Instance> list = new ArrayList<>();
        String sql = """
            SELECT i.id, i.model_name, i.status, i.upstream_model,
                   i.vendor_id, i.created_time, i.meta,
                   v.id as v_id, v.name as v_name, v.description as v_desc,
                   v.status as v_status, v."group" as v_group, v.priority as v_priority,
                   v.created_time as v_created_time, v.base_url as v_base_url,
                   v.api_key as v_api_key, v.meta as v_meta
            FROM instances i
            JOIN vendors v ON i.vendor_id = v.id
            ORDER BY i.id
            """;

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Instance inst = map(rs);
                Vendor vendor = new Vendor();
                vendor.setId(rs.getInt("v_id"));
                vendor.setName(rs.getString("v_name"));
                vendor.setDescription(rs.getString("v_desc"));
                vendor.setStatus(rs.getInt("v_status"));
                vendor.setGroup(rs.getString("v_group"));
                vendor.setPriority(rs.getInt("v_priority"));
                vendor.setCreatedTime(rs.getLong("v_created_time"));
                vendor.setBaseUrl(rs.getString("v_base_url"));
                vendor.setApiKey(rs.getString("v_api_key"));
                vendor.setMeta(rs.getString("v_meta"));
                inst.setVendor(vendor);
                list.add(inst);
            }
        } catch (SQLException e) {
            log.error("findAllWithVendor: {}", e.getMessage());
        }
        return list;
    }

    public List<Instance> findAll() {
        List<Instance> list = new ArrayList<>();
        String sql = "SELECT id, model_name, status, upstream_model, " +
                     "vendor_id, created_time, meta " +
                     "FROM instances WHERE status NOT IN (?, ?) ORDER BY id";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, STATUS_DISABLED);
            ps.setInt(2, STATUS_DEPRECATED);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        } catch (SQLException e) {
            log.error("findAll: {}", e.getMessage());
        }
        return list;
    }

    public Instance findById(int id) {
        String sql = "SELECT id, model_name, status, upstream_model, " +
                     "vendor_id, created_time, meta FROM instances WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return map(rs);
            }
        } catch (SQLException e) {
            log.error("findById {}: {}", id, e.getMessage());
        }
        return null;
    }

    public void update(Instance inst) {
        String sql = "UPDATE instances SET status=?, meta=? WHERE id=?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, inst.getStatus());
            ps.setString(2, inst.getMeta());
            ps.setInt(3, inst.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("update instance {}: {}", inst.getId(), e.getMessage());
        }
    }

    public void toggleStatus(int id) {
        Instance inst = findById(id);
        if (inst == null) return;
        int newStatus = (inst.getStatus() == 1 || inst.getStatus() == 2) ? 3 : 2;
        String sql = "UPDATE instances SET status=? WHERE id=?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, newStatus);
            ps.setInt(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("toggle instance {}: {}", id, e.getMessage());
        }
    }

    public void delete(int id) {
        String sql = "DELETE FROM instances WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("delete instance {}: {}", id, e.getMessage());
        }
    }

    public void insert(Instance inst) {
        String sql = "INSERT INTO instances (vendor_id, model_name, upstream_model, status, created_time, meta) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, inst.getVendorId());
            ps.setString(2, inst.getModelName());
            ps.setString(3, inst.getUpstreamModel());
            ps.setInt(4, inst.getStatus());
            ps.setLong(5, System.currentTimeMillis() / 1000);
            ps.setString(6, inst.getMeta() != null ? inst.getMeta() : "{}");
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("insert instance: {}", e.getMessage());
        }
    }

    private Instance map(ResultSet rs) throws SQLException {
        Instance inst = new Instance();
        inst.setId(rs.getInt("id"));
        inst.setModelName(rs.getString("model_name"));
        inst.setStatus(rs.getInt("status"));
        inst.setUpstreamModel(rs.getString("upstream_model"));
        inst.setVendorId(rs.getInt("vendor_id"));
        inst.setCreatedTime(rs.getLong("created_time"));
        inst.setMeta(rs.getString("meta"));
        return inst;
    }

    /**
     * Lightweight check: does any active instance have this model_name?
     * Used by NameMatcher in Stage 2.
     */
    public boolean existsByModelName(String modelName) {
        String sql = "SELECT 1 FROM instances WHERE model_name = ? AND status = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, modelName);
            ps.setInt(2, STATUS_RAW);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            log.error("existsByModelName {}: {}", modelName, e.getMessage());
            return false;
        }
    }
}
