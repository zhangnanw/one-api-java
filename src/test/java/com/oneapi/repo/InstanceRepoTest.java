package com.oneapi.repo;

import com.oneapi.config.DatabaseConfig;
import com.oneapi.model.Instance;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InstanceRepoTest {

    private static InstanceRepo repo;

    @BeforeAll
    static void setupAll() throws Exception {
        DatabaseConfig.init(":memory:");
        DataSource ds = DatabaseConfig.getDataSource();

        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE vendors (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT, description TEXT, status INTEGER," +
                "\"group\" TEXT, priority INTEGER, created_time INTEGER," +
                "base_url TEXT, api_key TEXT, meta TEXT)");

            stmt.execute("CREATE TABLE instances (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "model_name TEXT, status INTEGER, upstream_model TEXT," +
                "vendor_id INTEGER, created_time INTEGER, meta TEXT)");

            // 2 vendors
            stmt.execute("INSERT INTO vendors (id,name,status,priority,base_url,api_key) VALUES " +
                "(1,'deepseek',2,10,'https://api.deepseek.com','sk-ds')");
            stmt.execute("INSERT INTO vendors (id,name,status,priority,base_url,api_key) VALUES " +
                "(2,'volcengine',2,5,'https://ark.cn-beijing.volces.com','sk-ve')");

            // 3 instances
            stmt.execute("INSERT INTO instances (id,model_name,status,upstream_model,vendor_id,created_time) VALUES " +
                "(1,'deepseek-v4-flash',2,'deepseek-v4-flash',1,1700000000)");
            stmt.execute("INSERT INTO instances (id,model_name,status,upstream_model,vendor_id,created_time) VALUES " +
                "(2,'deepseek-v4-pro',2,'deepseek-v4-pro',2,1700000001)");
            stmt.execute("INSERT INTO instances (id,model_name,status,upstream_model,vendor_id,created_time) VALUES " +
                "(3,'deepseek-v4-disabled',3,null,1,1700000002)");
            stmt.execute("INSERT INTO instances (id,model_name,status,upstream_model,vendor_id,created_time) VALUES " +
                "(4,'deepseek-raw-variant',1,'deepseek-raw-variant',1,1700000003)");
        }

        repo = new InstanceRepo();
    }

    @AfterAll
    static void teardown() {
        var ds = DatabaseConfig.getDataSource();
        if (ds instanceof HikariDataSource hds && !hds.isClosed()) {
            hds.close();
        }
    }

    @Test
    void findAllWithVendor_returnsAllWithVendorFilled() {
        List<Instance> list = repo.findAllWithVendor();
        // findAllWithVendor does NOT filter by status — returns all joined
        // At minimum, the 3 seed instances must be present
        assertTrue(list.size() >= 3);

        Instance first = list.get(0);
        assertEquals(1, first.getId());
        assertEquals("deepseek-v4-flash", first.getModelName());
        assertNotNull(first.getVendor());
        assertEquals("deepseek", first.getVendor().getName());
    }

    @Test
    void findAll_excludesDisabledAndDeprecated() {
        // Status 3 (DISABLED) excluded — seeds: 1,2,4 active, 3 disabled
        List<Instance> list = repo.findAll();
        assertEquals(3, list.size());
        assertTrue(list.stream().noneMatch(i -> i.getId() == 3));
    }

    @Test
    void findById_returnsInstance() {
        Instance inst = repo.findById(1);
        assertNotNull(inst);
        assertEquals("deepseek-v4-flash", inst.getModelName());
        assertEquals(2, inst.getStatus());
        assertEquals(1, inst.getVendorId());
    }

    @Test
    void findById_nonexistent_returnsNull() {
        assertNull(repo.findById(999));
    }

    @Test
    void insert_thenFindAll_addsNew() {
        Instance newInst = new Instance();
        newInst.setModelName("deepseek-v4-new");
        newInst.setUpstreamModel("deepseek-v4-new");
        newInst.setStatus(2);
        newInst.setVendorId(1);

        repo.insert(newInst);

        List<Instance> list = repo.findAll();
        assertTrue(list.stream().anyMatch(i -> "deepseek-v4-new".equals(i.getModelName())));
    }

    @Test
    void updateStatus_thenFindById_statusChanged() {
        Instance inst = repo.findById(1);
        assertEquals(2, inst.getStatus());

        inst.setStatus(3); // DISABLED
        repo.update(inst);

        Instance updated = repo.findById(1);
        assertEquals(3, updated.getStatus());

        // restore
        inst.setStatus(2);
        repo.update(inst);
    }

    @Test
    void delete_thenFindById_returnsNull() {
        // Insert temp instance to delete
        Instance temp = new Instance();
        temp.setModelName("temp-to-delete");
        temp.setStatus(2);
        temp.setVendorId(1);
        repo.insert(temp);

        // Find by model name using findAll
        List<Instance> before = repo.findAll();
        Instance found = before.stream()
            .filter(i -> "temp-to-delete".equals(i.getModelName()))
            .findFirst().orElseThrow();
        int tempId = found.getId();

        repo.delete(tempId);
        assertNull(repo.findById(tempId));
    }

    @Test
    void toggleStatus_flipsActiveToDisabled() {
        repo.toggleStatus(1); // 2 → 3
        Instance inst = repo.findById(1);
        assertEquals(3, inst.getStatus());

        repo.toggleStatus(1); // 3 → 2
        inst = repo.findById(1);
        assertEquals(2, inst.getStatus());
    }

    @Test
    void existsByModelName_rawInstance_returnsTrue() {
        assertTrue(repo.existsByModelName("deepseek-raw-variant"));
    }

    @Test
    void existsByModelName_disabledInstance_returnsFalse() {
        assertFalse(repo.existsByModelName("deepseek-v4-disabled"));
    }

    @Test
    void existsByModelName_nonexistent_returnsFalse() {
        assertFalse(repo.existsByModelName("nonexistent-model"));
    }
}
