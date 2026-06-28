package com.oneapi.repo;

import com.oneapi.model.Instance;
import com.oneapi.model.Vendor;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InstanceRepoTest {

    private static HikariDataSource ds;
    private InstanceRepo repo;

    @BeforeAll
    static void createDataSource() {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:sqlite::memory:");
        cfg.setMaximumPoolSize(1);
        ds = new HikariDataSource(cfg);
    }

    @AfterAll
    static void closeDataSource() {
        if (ds != null && !ds.isClosed()) ds.close();
    }

    @BeforeEach
    void setup() throws Exception {
        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS instances");
            stmt.execute("DROP TABLE IF EXISTS vendors");
            stmt.execute("CREATE TABLE vendors (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL," +
                "description TEXT," +
                "status INTEGER DEFAULT 1," +
                "\"group\" TEXT," +
                "priority INTEGER DEFAULT 0," +
                "created_time INTEGER," +
                "base_url TEXT," +
                "api_key TEXT," +
                "meta TEXT" +
                ")");
            stmt.execute("CREATE TABLE instances (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "model_name TEXT NOT NULL," +
                "status INTEGER DEFAULT 1," +
                "upstream_model TEXT," +
                "vendor_id INTEGER REFERENCES vendors(id)," +
                "created_time INTEGER," +
                "meta TEXT," +
                "pref REAL DEFAULT 0," +
                "layer TEXT DEFAULT 'payg'" +
                ")");
            stmt.execute("INSERT INTO vendors (id, name, base_url, api_key) VALUES (1, 'deepseek', 'https://api.deepseek.com', 'sk-test')");
            stmt.execute("INSERT INTO vendors (id, name, base_url, api_key) VALUES (2, 'volcengine', 'https://ark.cn-beijing.volces.com', 'sk-test2')");
        }
        repo = new InstanceRepo(ds);
    }

    private void rawInsert(String modelName, int vendorId, int status, String upstream, String meta) throws Exception {
        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(String.format(
                "INSERT INTO instances (model_name, vendor_id, status, upstream_model, meta) " +
                "VALUES ('%s', %d, %d, '%s', '%s')",
                modelName, vendorId, status, upstream == null ? "" : upstream, meta == null ? "" : meta));
        }
    }

    // ── findAll ──

    @Test
    void findAll_returnsAllInstances() throws Exception {
        rawInsert("deepseek-v4-pro", 1, 2, "deepseek-v4-pro", null);
        rawInsert("deepseek-v4-flash", 1, 2, "deepseek-v4-flash", null);

        List<Instance> list = repo.findAll();
        assertEquals(2, list.size());
    }

    @Test
    void findAll_returnsEmptyForNoInstances() {
        List<Instance> list = repo.findAll();
        assertTrue(list.isEmpty());
    }

    // ── findById ──

    @Test
    void findById_returnsInstanceWhenFound() throws Exception {
        rawInsert("kimi-k2.6", 2, 2, "kimi-k2.6", null);
        // Need to get the auto-generated ID
        List<Instance> all = repo.findAll();
        int id = all.get(0).getId();

        Instance found = repo.findById(id);
        assertNotNull(found);
        assertEquals("kimi-k2.6", found.getModelName());
        assertEquals(2, found.getVendorId());
    }

    @Test
    void findById_returnsNullWhenNotFound() {
        Instance found = repo.findById(99999);
        assertNull(found);
    }

    // ── findAllWithVendor ──

    @Test
    void findAllWithVendor_joinsVendorData() throws Exception {
        rawInsert("deepseek-v4-pro", 1, 2, "deepseek-v4-pro", null);

        List<Instance> list = repo.findAllWithVendor();
        assertEquals(1, list.size());
        Instance inst = list.get(0);
        assertEquals("deepseek-v4-pro", inst.getModelName());

        Vendor v = inst.getVendor();
        assertNotNull(v);
        assertEquals("deepseek", v.getName());
        assertEquals("https://api.deepseek.com", v.getBaseUrl());
    }

    // ── insert ──

    @Test
    void insert_createsNewInstance() {
        Instance inst = new Instance();
        inst.setModelName("minimax-m3");
        inst.setVendorId(1);
        inst.setStatus(2);
        inst.setUpstreamModel("minimax-m3");
        inst.setMeta("{\"test\":true}");

        repo.insert(inst);
        // insert() doesn't set generated id — verify via findAll
        List<Instance> all = repo.findAll();
        assertEquals(1, all.size());
        Instance reloaded = all.get(0);
        assertEquals("minimax-m3", reloaded.getModelName());
        assertEquals(2, reloaded.getStatus());
    }

    // ── update ──

    @Test
    void update_modifiesExistingInstance() throws Exception {
        rawInsert("mimo-v2.5", 1, 2, "mimo-v2.5", null);
        List<Instance> all = repo.findAll();
        Instance toUpdate = all.get(0);

        toUpdate.setStatus(3);
        toUpdate.setMeta("{\"updated\":true}");
        repo.update(toUpdate);

        Instance reloaded = repo.findById(toUpdate.getId());
        assertEquals(3, reloaded.getStatus());
        assertEquals("{\"updated\":true}", reloaded.getMeta());
    }

    // ── delete ──

    @Test
    void delete_removesInstance() throws Exception {
        rawInsert("test-model", 1, 2, "test-model", null);
        List<Instance> all = repo.findAll();
        int id = all.get(0).getId();

        repo.delete(id);

        Instance found = repo.findById(id);
        assertNull(found);
    }

    @Test
    void delete_doesNotThrowForUnknownId() {
        // void method — just verify it doesn't throw
        assertDoesNotThrow(() -> repo.delete(99999));
    }

    // ── toggleStatus ──

    @Test
    void toggleStatus_flipsStatus() throws Exception {
        rawInsert("test-model", 1, 2, "test-model", null);
        List<Instance> all = repo.findAll();
        int id = all.get(0).getId();

        repo.toggleStatus(id);
        Instance reloaded = repo.findById(id);
        assertNotEquals(2, reloaded.getStatus());
    }

    // ── existsByModelName ──

    @Test
    void existsByModelName_returnsTrueForExisting() throws Exception {
        rawInsert("kimi-k2.6", 2, 1, "kimi-k2.6", null);  // status=RAW
        assertTrue(repo.existsByModelName("kimi-k2.6"));
    }

    @Test
    void existsByModelName_returnsFalseForNonExisting() {
        assertFalse(repo.existsByModelName("no-such-model"));
    }

    // ── lifecycle ──

    @Test
    void crudLifecycle() {
        // Insert
        Instance inst = new Instance();
        inst.setModelName("lifecycle-test");
        inst.setVendorId(1);
        inst.setStatus(2);
        inst.setUpstreamModel("lifecycle-upstream");
        repo.insert(inst);

        // Get the generated ID via findAll
        List<Instance> all = repo.findAll();
        assertEquals(1, all.size());
        int id = all.get(0).getId();
        assertTrue(id > 0);

        // Find
        Instance found = repo.findById(id);
        assertNotNull(found);
        assertEquals("lifecycle-test", found.getModelName());
        assertEquals(1, found.getVendorId());

        // Update
        found.setStatus(4);
        repo.update(found);

        Instance reloaded = repo.findById(id);
        assertEquals(4, reloaded.getStatus());

        // Delete
        repo.delete(id);
        assertNull(repo.findById(id));
    }
}
