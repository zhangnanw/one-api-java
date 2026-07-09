package com.oneapi.repo;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModelCatalogRepoTest {

    private static HikariDataSource ds;
    private static ModelCatalogRepo repo;

    @BeforeAll
    static void setupAll() throws Exception {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:sqlite::memory:");
        cfg.setMaximumPoolSize(1);
        ds = new HikariDataSource(cfg);

        // create table
        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS model_catalog (" +
                "name TEXT PRIMARY KEY," +
                "capabilities TEXT," +
                "context_window INTEGER," +
                "input_price REAL," +
                "output_price REAL," +
                "reference_notes TEXT" +
                ")");

            // insert test data
            stmt.execute("INSERT INTO model_catalog (name, capabilities) VALUES " +
                "('minimax-m3', '[\"chat\",\"code\",\"vision\"]')");
            stmt.execute("INSERT INTO model_catalog (name, capabilities) VALUES " +
                "('minimax-m2.7', '[\"chat\",\"code\"]')");
            stmt.execute("INSERT INTO model_catalog (name, capabilities) VALUES " +
                "('deepseek-v4-pro', '[\"code\",\"chat\"]')");
            stmt.execute("INSERT INTO model_catalog (name, capabilities) VALUES " +
                "('kimi-k2.6', '[\"code\",\"chat\",\"vision\"]')");
        }

        repo = new ModelCatalogRepo(ds);
    }

    @AfterAll
    static void teardown() {
        if (ds != null && !ds.isClosed()) {
            ds.close();
        }
    }

    @Test
    void hasCapability_returnsTrue_forMatchingCapability() {
        assertTrue(repo.hasCapability("minimax-m3", "vision"));
        assertTrue(repo.hasCapability("minimax-m3", "chat"));
        assertTrue(repo.hasCapability("minimax-m3", "code"));
        assertTrue(repo.hasCapability("kimi-k2.6", "vision"));
    }

    @Test
    void hasCapability_returnsFalse_forMissingCapability() {
        assertFalse(repo.hasCapability("minimax-m2.7", "vision"));
        assertFalse(repo.hasCapability("deepseek-v4-pro", "vision"));
        assertFalse(repo.hasCapability("deepseek-v4-pro", "reasoning"));
    }

    @Test
    void hasCapability_isCaseInsensitive() {
        assertTrue(repo.hasCapability("minimax-m3", "VISION"));
        assertTrue(repo.hasCapability("minimax-m3", "Vision"));
        assertTrue(repo.hasCapability("minimax-m3", "CHAT"));
        assertTrue(repo.hasCapability("kimi-k2.6", "ViSiOn"));
    }

    @Test
    void getCapabilities_returnsCorrectList() {
        List<String> caps = repo.getCapabilities("minimax-m3");
        assertEquals(3, caps.size());
        assertTrue(caps.contains("chat"));
        assertTrue(caps.contains("code"));
        assertTrue(caps.contains("vision"));
    }

    @Test
    void getCapabilities_returnsEmpty_forUnknownModel() {
        List<String> caps = repo.getCapabilities("nonexistent-model");
        assertTrue(caps.isEmpty());
    }

    @Test
    void constructor_loadsMultipleModelsCorrectly() {
        // already loaded in @BeforeAll
        assertTrue(repo.hasCapability("minimax-m3", "vision"));
        assertTrue(repo.hasCapability("kimi-k2.6", "vision"));
        assertFalse(repo.hasCapability("minimax-m2.7", "vision"));
        assertFalse(repo.hasCapability("deepseek-v4-pro", "vision"));
    }

    @Test
    void hasCapability_nullModelName_returnsFalse() {
        assertFalse(repo.hasCapability(null, "vision"));
    }

    @Test
    void hasCapability_nullCapability_returnsFalse() {
        assertFalse(repo.hasCapability("minimax-m3", null));
    }

    @Test
    void raw_returnsMutableMap() {
        var map = repo.raw();
        assertNotNull(map);
        assertTrue(map.size() >= 4);
        assertTrue(map.containsKey("minimax-m3"));
    }
}
