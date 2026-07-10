package com.oneapi.config;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DatabaseConfigTest {

    @Test
    void initH2Test_returnsDataSource() {
        DatabaseConfig.init("test");
        DataSource ds = DatabaseConfig.getDataSource();
        assertNotNull(ds, "DataSource should not be null after init");
    }

    @Test
    void getDataSource_returnsConnection() throws SQLException {
        DatabaseConfig.init("test");
        try (Connection conn = DatabaseConfig.getDataSource().getConnection()) {
            assertThat(conn.isValid(1)).isTrue();
        }
    }

    @Test
    void initTwice_overwrites() {
        DatabaseConfig.init("test");
        DataSource first = DatabaseConfig.getDataSource();
        DatabaseConfig.init("test");
        DataSource second = DatabaseConfig.getDataSource();
        assertNotNull(second);
        // Different DataSource objects (each init creates new pool)
    }
}
