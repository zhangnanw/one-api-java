package com.oneapi.repo;

import com.oneapi.model.Vendor;
import com.oneapi.model.VendorWithCount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 濞撴碍绋戠花鏌ュ疮閸℃洜娉㈤幖瀛樻尪閳?
 * <p>
 * 闁哄被鍎叉竟妯兼嫚椤撴繄鐤呴柨娑欑婢у秹寮?find 闁哄倽顫夌涵鍫曞锤閸ャ劌鐦?id 闁告娲ょ花顓熸交閺傛寧绀€闁挎稑濂旂粭澶愬绩椤栨稑鐦俊顖ょ磿绾箓寮婚妷锕€顥濋柕?
 * - {@link #findAll}闁靛棔绨濦link #findAllActive}闁挎稒鑹惧畷鐔烘偘閵婏妇鍙€閻犲浂婢佺槐婕stance_count = 0
 * - {@link #findAllWithCounts}闁挎稒鐭粭?instances 閻?LEFT JOIN闁挎稑濂旂划搴ｇ磼閻旀椿鍚€ status=1 闁汇劌瀚璺ㄦ崉閸愩劎鏉藉〒?
 * <p>
 * 闁圭儤甯掔花顓犳喆閸曨偄鐏熼柨娑欑婢у秹寮垫径濠傜仚閻炴稏鍔嶉弻鐔封枖?ORDER BY id ASC闁挎稒绋戦崹搴亜闂堟稑妫橀柡?offset/limit 闁汇垼绮鹃惃鐔兼偨閵婏附鐓欓柛鎰暱閻ｉ箖濡?
 * <p>
 * 闂佹寧鐟ㄩ銈嗗緞閸曨厽鍊為柨娑欑摂QLException 濞戞挴鍋撶€?log 闁告艾姘︾换鎴﹀炊閻愮鏁勯梻鍡楁閹酣鏁嶉崸妾宻t 闁哄倽顫夌涵鍫曟晬婢跺鐏?null闁挎稑娼歩ndById闁挎稑顦埀?
 */
public class VendorRepo extends BaseRepo {
    private static final Logger log = LoggerFactory.getLogger(VendorRepo.class);

    /**
     * 闁告帒妫濋妴澶愬礆濡も偓閸ゎ參骞嶉埀顒勫嫉婢跺杩旈幖瀛樻煥閺呫垽鏁嶉崼婵囧創鐎规瓕灏欓々锕傛偨椤帞绀嗛柨娑樻湰鐎?id 闁告娲ょ花顓㈠Υ?
     * @param offset 閻犙冨槻椤劗鎮板畝瀣0-based
     * @param limit  闁哄牃鍋撳鍫嗗棭鏀介柡?
     * @return 闁告帗顨夐妴鍐晬濞戞埃鏁勯柡鍐╂构鐠愮喖姊归崹顔碱唺濞?0 闁?list
     */
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

    public List<VendorWithCount> findAllWithCounts(int offset, int limit) {
        List<VendorWithCount> list = new ArrayList<>();
        String sql = "SELECT v.id, v.name, v.description, v.status, " +
                     "v.\"group\", v.priority, v.created_time, v.base_url, v.api_key, v.meta, " +
                     "COUNT(i.id) AS instance_count " +
                     "FROM vendors v LEFT JOIN instances i ON v.id = i.vendor_id AND i.status NOT IN (0, 3, 4, 5) " +
                     "GROUP BY v.id, v.name, v.description, v.status, " +
                     "v.\"group\", v.priority, v.created_time, v.base_url, v.api_key, v.meta " +
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
            log.error("findAllWithCounts: {}", e.getMessage());
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

    /**
     * 闁?id 缂侇喖澧介垾姗€寮婚妷锕€顥濋柛妤佹磻闁叉粍绗熷☉妯煎畨闁哥喎妫庨埀?
     * @param id 濞撴碍绋戠花鏌ュ疮閸℃洖鐦滈梺?
     * @return 闁瑰灚鍎抽崺灞炬交閺傛寧绀€ Vendor闁挎稑鏈竟妯荤▔瀹ュ懎鐓傞柟?SQL 濠㈡儼绮剧憴锔芥交閺傛寧绀€ null
     */
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

    public void insert(Vendor vendor) {
        String sql = "INSERT INTO vendors (name, description, status, \"group\", " +
                     "priority, created_time, base_url, api_key, meta) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

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
            ps.setString(9, vendor.getMeta());
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("insert vendor: {}", e.getMessage());
            throw new RuntimeException("DB write failed", e);
        }
    }

    public void update(int id, Vendor vendor) {
        String sql = "UPDATE vendors SET name=?, description=?, status=?, \"group\"=?, " +
                     "priority=?, base_url=?, meta=? WHERE id=?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, vendor.getName());
            ps.setString(2, vendor.getDescription());
            ps.setInt(3, vendor.getStatus());
            ps.setString(4, vendor.getGroup());
            ps.setInt(5, vendor.getPriority());
            ps.setString(6, vendor.getBaseUrl());
            ps.setString(7, vendor.getMeta());
            ps.setInt(8, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("update vendor {}: {}", id, e.getMessage());
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
            log.error("updateApiKey vendor {}: {}", id, e.getMessage());
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
            log.error("delete vendor {}: {}", id, e.getMessage());
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
            log.error("countInstances vendor {}: {}", vendorId, e.getMessage());
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
        vendor.setMeta(rs.getString("meta"));
        return vendor;
    }
}
