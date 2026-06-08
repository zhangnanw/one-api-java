package com.oneapi.repo;

import com.oneapi.config.DatabaseConfig;
import com.oneapi.model.VirtualModel;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VirtualModelRepoTest {

    private static VirtualModelRepo repo;

    @BeforeAll
    static void setupAll() throws Exception {
        DatabaseConfig.init(":memory:");
        DataSource ds = DatabaseConfig.getDataSource();

        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE virtual_models (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT," +
                "match TEXT)");

            stmt.execute("INSERT INTO virtual_models (id,name,match) VALUES " +
                "(1,'deepseek','{\"models\":[\"deepseek-v4-flash\",\"deepseek-v4-pro\"]}')");
            stmt.execute("INSERT INTO virtual_models (id,name,match) VALUES " +
                "(2,'coding','{\"models\":[\"mimo-v2.5\",\"deepseek-v4-pro\"]}')");
            stmt.execute("INSERT INTO virtual_models (id,name,match) VALUES " +
                "(3,'empty-json','{}')");
        }

        repo = new VirtualModelRepo();
    }

    @AfterAll
    static void teardown() {
        var ds = DatabaseConfig.getDataSource();
        if (ds instanceof HikariDataSource hds && !hds.isClosed()) {
            hds.close();
        }
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
    void findByName_nonexistent_returnsNull() {
        assertNull(repo.findByName("nonexistent"));
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
    void delete_thenFindByName_returnsNull() {
        VirtualModel temp = new VirtualModel();
        temp.setName("to-delete");
        temp.setMatch("{}");
        repo.insert(temp);

        VirtualModel found = repo.findByName("to-delete");
        assertNotNull(found);

        repo.delete(found.getId());
        assertNull(repo.findByName("to-delete"));
    }
}
