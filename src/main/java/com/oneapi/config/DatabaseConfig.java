package com.oneapi.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * HikariCP 数据源创建工具类。
 * <p>
 * 不再持有任何静态状态，仅提供从 {@link AppConfig.DatabaseYamlConfig}
 * 构建 {@link DataSource} 的静态工厂方法。
 * 数据源实例由 {@link AppConfigConfiguration} 以 Spring Bean 形式管理。
 */
@Slf4j
public final class DatabaseConfig {

    private DatabaseConfig() {
        // utility class
    }

    public static DataSource createPostgresDataSource(AppConfig.DatabaseYamlConfig db) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(String.format("jdbc:postgresql://%s:%d/%s", db.getHost(), db.getPort(), db.getDatabase()));
        config.setUsername(db.getUser());
        config.setPassword(db.getPassword());
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(10000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setConnectionTestQuery("SELECT 1");
        config.setKeepaliveTime(30000);
        config.setValidationTimeout(5000);

        HikariDataSource dataSource = new HikariDataSource(config);
        try (Connection conn = dataSource.getConnection()) {
            log.info("PostgreSQL connected: {}:{}/{}", db.getHost(), db.getPort(), db.getDatabase());
        } catch (SQLException e) {
            closeQuietly(dataSource);
            throw new RuntimeException("PostgreSQL connection failed: " + e.getMessage(), e);
        }
        return dataSource;
    }

    /**
     * 为测试提供 H2 in-memory 数据源（PostgreSQL 兼容模式）。
     */
    public static DataSource createTestDataSource(String name) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:" + name +
            ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;NON_KEYWORDS=MATCH");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(5);
        config.setConnectionTimeout(10000);
        return new HikariDataSource(config);
    }

    private static void closeQuietly(DataSource ds) {
        if (ds instanceof HikariDataSource hds && !hds.isClosed()) {
            try { hds.close(); } catch (Exception ignored) { }
        }
    }
}
