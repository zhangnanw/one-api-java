package com.oneapi.repo;

import com.oneapi.config.DatabaseConfig;
import com.oneapi.model.ModelSpec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ModelCatalogRepoTest {

    private static ModelCatalogRepo repo;

    @BeforeAll
    static void setupAll() {
        // 使用临时 sqlite 文件，避免污染主库
        File tmp = new File(System.getProperty("java.io.tmpdir"),
            "model_catalog_test_" + System.currentTimeMillis() + ".db");
        tmp.deleteOnExit();
        DatabaseConfig.init(tmp.getAbsolutePath());
        repo = new ModelCatalogRepo();
        repo.init();
    }

    @BeforeEach
    void cleanTable() {
        // 每个测试用例前清空表，保证用例独立
        try (var conn = DatabaseConfig.getDataSource().getConnection();
             var stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM model_catalog");
        } catch (Exception e) {
            fail("cleanTable failed: " + e.getMessage());
        }
    }

    @Test
    void init_createsTable() {
        // init 已执行过，再调一次应不抛异常
        assertDoesNotThrow(() -> repo.init());

        // 表应存在
        try (var conn = DatabaseConfig.getDataSource().getConnection();
             var rs = conn.createStatement().executeQuery(
                 "SELECT name FROM sqlite_master WHERE type='table' AND name='model_catalog'")) {
            assertTrue(rs.next(), "model_catalog table should exist");
            assertEquals("model_catalog", rs.getString(1));
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    void upsert_thenFindByName_returnsSpec() {
        ModelSpec spec = new ModelSpec(
            "deepseek-v4-pro",
            Set.of("chat", "tools"),
            128000,
            1.0,
            2.0
        );
        repo.upsert(spec);

        Optional<ModelSpec> found = repo.findByName("deepseek-v4-pro");
        assertTrue(found.isPresent());
        assertEquals("deepseek-v4-pro", found.get().name());
        assertEquals(Set.of("chat", "tools"), found.get().capabilities());
        assertEquals(128000, found.get().contextWindow());
        assertEquals(1.0, found.get().inputRmbPerM(), 1e-9);
        assertEquals(2.0, found.get().outputRmbPerM(), 1e-9);
    }

    @Test
    void upsert_existingName_updatesFields() {
        repo.upsert(new ModelSpec("gpt-x", Set.of("chat"), 8000, 1.0, 2.0));
        repo.upsert(new ModelSpec("gpt-x", Set.of("chat", "vision"), 16000, 3.0, 4.0));

        Optional<ModelSpec> found = repo.findByName("gpt-x");
        assertTrue(found.isPresent());
        assertEquals(Set.of("chat", "vision"), found.get().capabilities());
        assertEquals(16000, found.get().contextWindow());
        assertEquals(3.0, found.get().inputRmbPerM(), 1e-9);
        assertEquals(4.0, found.get().outputRmbPerM(), 1e-9);

        // 仍只一条
        List<ModelSpec> all = repo.findAll();
        assertEquals(1, all.stream().filter(s -> s.name().equals("gpt-x")).count());
    }

    @Test
    void findByName_missing_returnsEmpty() {
        assertTrue(repo.findByName("nonexistent").isEmpty());
    }

    @Test
    void findAll_returnsAllOrderedByName() {
        repo.upsert(new ModelSpec("b-model", Set.of("chat"), 100, 0.1, 0.2));
        repo.upsert(new ModelSpec("a-model", Set.of("chat"), 100, 0.1, 0.2));
        repo.upsert(new ModelSpec("c-model", Set.of("chat"), 100, 0.1, 0.2));

        List<ModelSpec> all = repo.findAll();
        assertEquals(3, all.size());
        assertEquals("a-model", all.get(0).name());
        assertEquals("b-model", all.get(1).name());
        assertEquals("c-model", all.get(2).name());
    }

    @Test
    void upsert_emptyCapabilities_isAllowed() {
        repo.upsert(new ModelSpec("empty-caps", Set.of(), 4000, 0.5, 0.5));
        Optional<ModelSpec> found = repo.findByName("empty-caps");
        assertTrue(found.isPresent());
        assertNotNull(found.get().capabilities());
        assertTrue(found.get().capabilities().isEmpty());
    }
}
