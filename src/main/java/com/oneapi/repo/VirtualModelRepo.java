package com.oneapi.repo;

import com.oneapi.model.VirtualModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class VirtualModelRepo extends BaseRepo {
    private static final Logger log = LoggerFactory.getLogger(VirtualModelRepo.class);

    public List<VirtualModel> findAll() {
        List<VirtualModel> list = new ArrayList<>();
        String sql = "SELECT id, name, match FROM virtual_models ORDER BY id";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                VirtualModel vm = new VirtualModel();
                vm.setId(rs.getInt("id"));
                vm.setName(rs.getString("name"));
                vm.setMatch(rs.getString("match"));
                list.add(vm);
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
                    VirtualModel vm = new VirtualModel();
                    vm.setId(rs.getInt("id"));
                    vm.setName(rs.getString("name"));
                    vm.setMatch(rs.getString("match"));
                    return vm;
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
                    VirtualModel vm = new VirtualModel();
                    vm.setId(rs.getInt("id"));
                    vm.setName(rs.getString("name"));
                    vm.setMatch(rs.getString("match"));
                    return vm;
                }
            }
        } catch (SQLException e) {
            log.error("findById {}: {}", id, e.getMessage());
        }
        return null;
    }

    public void insert(VirtualModel vm) {
        String sql = "INSERT INTO virtual_models (name, match) VALUES (?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, vm.getName());
            ps.setString(2, vm.getMatch());
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
