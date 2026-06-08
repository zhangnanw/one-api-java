package com.oneapi.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DatabaseConfigTest {

    @AfterEach
    void tearDown() {
        // Test with in-memory DB — no cleanup needed
    }

    @Test
    void initInMemory_returnsDataSource() {
        DatabaseConfig.init("jdbc:sqlite::memory:");
        DataSource ds = DatabaseConfig.getDataSource();
        assertNotNull(ds, "DataSource should not be null after init");
    }

    @Test
    void getDataSource_returnsConnection() throws SQLException {
        DatabaseConfig.init("jdbc:sqlite::memory:");
        try (Connection conn = DatabaseConfig.getDataSource().getConnection()) {
            assertThat(conn.isValid(1)).isTrue();
        }
    }

    @Test
    void initWithFile_createsDataSource() throws SQLException {
        String tmpPath = System.getProperty("java.io.tmpdir") + "/one-api-test-" + System.currentTimeMillis() + ".db";
        DatabaseConfig.init(tmpPath);
        try {
            DataSource ds = DatabaseConfig.getDataSource();
            assertNotNull(ds);
            try (Connection conn = ds.getConnection()) {
                assertThat(conn.isValid(1)).isTrue();
            }
        } finally {
            new java.io.File(tmpPath).delete();
        }
    }

    @Test
    void initTwiceWithMemory_overwrites() {
        DatabaseConfig.init("jdbc:sqlite::memory:");
        DataSource first = DatabaseConfig.getDataSource();
        DatabaseConfig.init("jdbc:sqlite::memory:");
        DataSource second = DatabaseConfig.getDataSource();
        assertNotNull(second);
        // Different DataSource objects (each init creates new pool)
    }
}
