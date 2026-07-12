package com.oneapi.repo;

import com.oneapi.model.ModelCatalogEntry;
import io.vertx.core.json.JsonArray;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache of model_catalog capabilities and context windows,
 * plus CRUD access to the model_catalog table.
 * <p>
 * Loads all (name, capabilities, context_window) rows on construction from the database.
 * Used by {@code CapabilityInstanceFilter} and {@code BodyLimitFilter}.
 * <p>
 * 错误处理：写操作抛出 RuntimeException；读操作也统一抛出 RuntimeException。
 */
@Slf4j
public class ModelCatalogRepo extends BaseRepo implements CapabilityCatalog, WindowCatalog {

    private final ConcurrentHashMap<String, List<String>> catalog = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> contextWindows = new ConcurrentHashMap<>();

    /** Creates empty cache — for testing or when DB is not available. */
    public ModelCatalogRepo() {
        super();
        load();
    }

    public ModelCatalogRepo(DataSource dataSource) {
        super(dataSource);
        load();
    }

    private void load() {
        String sql = "SELECT name, capabilities, context_window, reference_notes FROM model_catalog";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            int loaded = 0;
            while (rs.next()) {
                String name = rs.getString("name");
                String capsJson = rs.getString("capabilities");
                int ctxWindow = rs.getInt("context_window");
                if (name == null) continue;

                putCapabilities(name, capsJson);
                if (ctxWindow > 0) {
                    contextWindows.put(name, ctxWindow);
                }
                loaded++;
            }
            log.info("ModelCatalogRepo loaded {} models ({} with context_window)",
                loaded, contextWindows.size());
        } catch (SQLException e) {
            log.error("Failed to load model catalog from database", e);
            throw new RuntimeException("Failed to load model catalog", e);
        }
    }

    private void putCapabilities(String name, String capsJson) {
        if (capsJson == null) return;
        try {
            JsonArray arr = new JsonArray(capsJson);
            List<String> caps = new ArrayList<>(arr.size());
            for (int i = 0; i < arr.size(); i++) {
                caps.add(arr.getString(i));
            }
            catalog.put(name, Collections.unmodifiableList(caps));
        } catch (Exception e) {
            log.warn("Failed to parse capabilities for model {}: {}", name, e.getMessage(), e);
        }
    }

    private void removeFromCache(String name) {
        catalog.remove(name);
        contextWindows.remove(name);
    }

    private List<ModelCatalogEntry> findAllInternal() {
        List<ModelCatalogEntry> list = new ArrayList<>();
        String sql = "SELECT name, capabilities, context_window, input_price, output_price, reference_notes FROM model_catalog ORDER BY name";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapEntry(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("DB read failed", e);
        }
        return list;
    }

    public List<ModelCatalogEntry> findAll() {
        return findAllInternal();
    }

    public ModelCatalogEntry findByName(String name) {
        String sql = "SELECT name, capabilities, context_window, input_price, output_price, reference_notes FROM model_catalog WHERE name = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapEntry(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("DB read failed", e);
        }
        return null;
    }

    public void insert(ModelCatalogEntry entry) {
        String sql = "INSERT INTO model_catalog (name, capabilities, context_window, input_price, output_price, reference_notes) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entry.getName());
            ps.setString(2, entry.getCapabilities());
            ps.setObject(3, entry.getContextWindow());
            ps.setObject(4, entry.getInputPrice());
            ps.setObject(5, entry.getOutputPrice());
            ps.setString(6, entry.getReferenceNotes());
            ps.executeUpdate();
            putCapabilities(entry.getName(), entry.getCapabilities());
            if (entry.getContextWindow() != null && entry.getContextWindow() > 0) {
                contextWindows.put(entry.getName(), entry.getContextWindow());
            }
        } catch (SQLException e) {
            log.error("insert model_catalog {}: {}", entry.getName(), e.getMessage(), e);
            throw new RuntimeException("DB write failed", e);
        }
    }

    public void update(String name, ModelCatalogEntry entry) {
        String sql = "UPDATE model_catalog SET name = ?, capabilities = ?, context_window = ?, input_price = ?, output_price = ?, reference_notes = ? " +
                     "WHERE name = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entry.getName());
            ps.setString(2, entry.getCapabilities());
            ps.setObject(3, entry.getContextWindow());
            ps.setObject(4, entry.getInputPrice());
            ps.setObject(5, entry.getOutputPrice());
            ps.setString(6, entry.getReferenceNotes());
            ps.setString(7, name);
            ps.executeUpdate();
            if (!name.equals(entry.getName())) {
                removeFromCache(name);
            }
            putCapabilities(entry.getName(), entry.getCapabilities());
            if (entry.getContextWindow() != null && entry.getContextWindow() > 0) {
                contextWindows.put(entry.getName(), entry.getContextWindow());
            } else {
                contextWindows.remove(entry.getName());
            }
        } catch (SQLException e) {
            log.error("update model_catalog {}: {}", name, e.getMessage(), e);
            throw new RuntimeException("DB write failed", e);
        }
    }

    public void delete(String name) {
        String sql = "DELETE FROM model_catalog WHERE name = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.executeUpdate();
            removeFromCache(name);
        } catch (SQLException e) {
            log.error("delete model_catalog {}: {}", name, e.getMessage(), e);
            throw new RuntimeException("DB write failed", e);
        }
    }

    private ModelCatalogEntry mapEntry(ResultSet rs) throws SQLException {
        ModelCatalogEntry entry = new ModelCatalogEntry();
        entry.setName(rs.getString("name"));
        entry.setCapabilities(rs.getString("capabilities"));
        entry.setContextWindow(rs.getInt("context_window"));
        entry.setInputPrice(rs.getDouble("input_price"));
        entry.setOutputPrice(rs.getDouble("output_price"));
        entry.setReferenceNotes(rs.getString("reference_notes"));
        return entry;
    }

    /**
     * Returns true if the given model has the required capability.
     * Capability comparison is case-insensitive.
     */
    public boolean hasCapability(String modelName, String capability) {
        if (modelName == null || capability == null) return false;
        List<String> caps = catalog.get(modelName);
        if (caps == null) return false;
        return caps.stream().anyMatch(c -> c.equalsIgnoreCase(capability));
    }

    /**
     * Returns the capabilities list for the given model,
     * or an empty list if the model is not in the catalog.
     */
    public List<String> getCapabilities(String modelName) {
        if (modelName == null) return Collections.emptyList();
        return catalog.getOrDefault(modelName, Collections.emptyList());
    }

    /**
     * Returns the context window in tokens for the given model,
     * or 0 if the model is not in the catalog or has no context_window.
     */
    public int getContextWindow(String modelName) {
        if (modelName == null) return 0;
        return contextWindows.getOrDefault(modelName, 0);
    }

    /** For testing / introspection only. */
    ConcurrentHashMap<String, List<String>> raw() {
        return catalog;
    }
    ConcurrentHashMap<String, Integer> rawWindows() {
        return contextWindows;
    }
}
