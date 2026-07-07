package com.oneapi.repo;

import com.oneapi.model.VirtualModel;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VirtualModelRepoTest {

    private HikariDataSource ds;
    private VirtualModelRepo repo;

    @BeforeEach
    void setup() throws Exception {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:sqlite:file::memory:?cache=shared");
        cfg.setMaximumPoolSize(1);
        cfg.setConnectionTimeout(5000);
        ds = new HikariDataSource(cfg);

        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE virtual_models (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT UNIQUE," +
                "match TEXT)");
            stmt.execute("INSERT INTO virtual_models (id,name,match) VALUES " +
                "(1,'deepseek','{\"models\":[\"deepseek-v4-flash\",\"deepseek-v4-pro\"]}')");
            stmt.execute("INSERT INTO virtual_models (id,name,match) VALUES " +
                "(2,'coding','{\"models\":[\"mimo-v2.5\",\"deepseek-v4-pro\"]}')");
            stmt.execute("INSERT INTO virtual_models (id,name,match) VALUES " +
                "(3,'empty-json','{}')");
        }

        repo = new VirtualModelRepo(ds);
    }

    @AfterEach
    void teardown() {
        if (ds != null && !ds.isClosed()) ds.close();
    }

    @Test
    void findAll_returnsAllThree() {
        List<VirtualModel> list = repo.findAll();
        assertEquals(3, list.size());
    }

    @Test
    void findByName_existing_returnsModel() {
        VirtualModel vm = repo.findByName("deepseek");
        assertNotNull(vm);
        assertEquals(1, vm.getId());
        assertTrue(vm.getMatch().contains("deepseek-v4-flash"));
    }

    @Test
    void findByName_nonexistent_returnsNotFoundSentinel() {
        assertEquals(VirtualModel.NOT_FOUND, repo.findByName("nonexistent"));
    }

    @Test
    void findById_existing_returnsModel() {
        VirtualModel vm = repo.findById(1);
        assertNotNull(vm);
        assertEquals("deepseek", vm.getName());
    }

    @Test
    void findById_nonexistent_returnsNull() {
        assertNull(repo.findById(999));
    }

    @Test
    void insert_thenFindByName_found() {
        VirtualModel newVm = new VirtualModel();
        newVm.setName("test-entry");
        newVm.setMatch("{\"models\":[\"test-model\"]}");
        repo.insert(newVm);

        VirtualModel found = repo.findByName("test-entry");
        assertNotNull(found);
        assertEquals("{\"models\":[\"test-model\"]}", found.getMatch());
    }

    @Test
    void updateMatch_thenFindByName_matchUpdated() {
        repo.updateMatch(1, "{\"models\":[\"updated-model\"]}");

        VirtualModel vm = repo.findByName("deepseek");
        assertEquals("{\"models\":[\"updated-model\"]}", vm.getMatch());

        // restore
        repo.updateMatch(1, "{\"models\":[\"deepseek-v4-flash\",\"deepseek-v4-pro\"]}");
    }

    @Test
    void delete_thenFindByName_returnsNotFoundSentinel() {
        VirtualModel temp = new VirtualModel();
        temp.setName("to-delete");
        temp.setMatch("{}");
        repo.insert(temp);

        VirtualModel found = repo.findByName("to-delete");
        assertNotNull(found);

        repo.delete(found.getId());
        assertEquals(VirtualModel.NOT_FOUND, repo.findByName("to-delete"));
    }
}
