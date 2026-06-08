package com.oneapi.repo;

import io.vertx.core.json.JsonArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory cache of model_catalog capabilities and context windows.
 * <p>
 * Loads all (name, capabilities, context_window) rows on construction from the database.
 * Used by {@code CapabilityInstanceFilter} and {@code BodyLimitFilter}.
 */
public class ModelCatalogRepo {
    private static final Logger log = LoggerFactory.getLogger(ModelCatalogRepo.class);

    private final ConcurrentHashMap<String, List<String>> catalog = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> contextWindows = new ConcurrentHashMap<>();

    /** For testing — creates empty cache. */
    public ModelCatalogRepo() {}

    public ModelCatalogRepo(DataSource dataSource) {
        String sql = "SELECT name, capabilities, context_window FROM model_catalog";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            int loaded = 0;
            while (rs.next()) {
                String name = rs.getString("name");
                String capsJson = rs.getString("capabilities");
                int ctxWindow = rs.getInt("context_window");
                if (name == null) continue;

                if (capsJson != null) {
                    List<String> caps = new ArrayList<>();
                    try {
                        JsonArray arr = new JsonArray(capsJson);
                        for (int i = 0; i < arr.size(); i++) {
                            caps.add(arr.getString(i));
                        }
                        catalog.put(name, Collections.unmodifiableList(caps));
                    } catch (Exception e) {
                        log.warn("Failed to parse capabilities for model {}: {}", name, e.getMessage());
                    }
                }
                if (ctxWindow > 0) {
                    contextWindows.put(name, ctxWindow);
                }
                loaded++;
            }
            log.info("ModelCatalogRepo loaded {} models ({} with context_window)",
                loaded, contextWindows.size());
        } catch (SQLException e) {
            log.error("Failed to load model catalog from database", e);
        }
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
