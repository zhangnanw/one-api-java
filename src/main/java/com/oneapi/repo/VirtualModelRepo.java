package com.oneapi.repo;

import com.oneapi.model.VirtualModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 閾忔碍瀚欏Ο鈥崇€锋禒鎾崇氨閵?
 * <p>
 * 閾忔碍瀚欏Ο鈥崇€烽弰顖滄暏閹寸兘娼伴崥鎴犳畱 API 閸氬秶袨閿涘牆顩?"kimi-k2.6"閿涘绱濋崠褰掑帳鐟欏嫬鍨?JSON閿涘澖@code match} 閸掓绱氱憴锝嗙€介崥?
 * 閻?{@link com.oneapi.model.MatchRuleParser} 鏉烆兛璐熺猾璇茬€烽崠?MatchRule閵?
 * <p>
 * 閺屻儲澹樼拠顓濈疅閿?
 * - {@link #findByName}閿涙碍瀵?name 缁墽鈥橀崠褰掑帳閿涘牏鏁ら幋?API 鐠囬攱鐪?閳?閾忔碍瀚欏Ο鈥崇€烽敍?
 * - {@link #findAll}閿涙艾鍙忕悰銊﹀瘻 id 閸楀洤绨敍娑楃返 /v1/models 缁旑垳鍋ｆ担璺ㄦ暏
 * - NOT_FOUND 閸濄劌鍙洪敍姘弓濞夈劌鍞介弮鎯扮箲閸?{@link VirtualModel#NOT_FOUND}閿涘潟d=0 閻ㄥ嫬宕版担宥忕礆閿?
 *   鐠嬪啰鏁ら弬鐟扮安濡偓閺?{@code virtualModel == VirtualModel.NOT_FOUND}閵?
 * <p>
 * 闁挎瑨顕ゆ径鍕倞閿涙瓔QLException 娑撯偓瀵?log 閸氬氦绻戦崶鐐碘敄闂嗗棗鎮庨幋?NOT_FOUND閵?
 */
public class VirtualModelRepo extends BaseRepo {
    private static final Logger log = LoggerFactory.getLogger(VirtualModelRepo.class);

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
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, virtualModel.getName());
            ps.setString(2, virtualModel.getMatch());
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("insert virtual_model: {}", e.getMessage());
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
