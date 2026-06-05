package com.oneapi.repo;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import com.oneapi.config.DatabaseConfig;

/**
 * Base class for all repositories.
 * Gets Connection from HikariCP pool.
 */
public abstract class BaseRepo {
    protected Connection getConnection() throws SQLException {
        return DatabaseConfig.getDataSource().getConnection();
    }

    protected DataSource getDataSource() {
        return DatabaseConfig.getDataSource();
    }
}
