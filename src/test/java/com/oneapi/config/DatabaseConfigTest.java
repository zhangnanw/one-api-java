package com.oneapi.config;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DatabaseConfigTest {

    @Test
    void createTestDataSource_returnsDataSource() {
        DataSource ds = DatabaseConfig.createTestDataSource("unit_test");
        assertNotNull(ds, "DataSource should not be null");
    }

    @Test
    void createTestDataSource_returnsConnection() throws SQLException {
        DataSource ds = DatabaseConfig.createTestDataSource("unit_test_conn");
        try (Connection conn = ds.getConnection()) {
            assertThat(conn.isValid(1)).isTrue();
        }
    }
}
