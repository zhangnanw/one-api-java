package com.oneapi.config;

import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Schema migration utility.
 * Ensures `instances`, `virtual_models` and `vendors` use database-generated auto-increment ids.
 * Runs once on startup after the DataSource is initialized.
 */
@Slf4j
public class SchemaManager {

    private final DataSource dataSource;

    public SchemaManager(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void migrate() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            ensurePostgresSerial(stmt, "instances", "id");
            ensurePostgresSerial(stmt, "virtual_models", "id");
            ensurePostgresSerial(stmt, "vendors", "id");

            resetPostgresSequence(stmt, "instances", "id");
            resetPostgresSequence(stmt, "virtual_models", "id");
            resetPostgresSequence(stmt, "vendors", "id");

            log.info("PostgreSQL schema migration completed");
        } catch (SQLException e) {
            log.error("Schema migration failed", e);
            throw new RuntimeException("Schema migration failed", e);
        }
    }

    private void resetPostgresSequence(Statement stmt, String table, String column) throws SQLException {
        String seqName = table + "_" + column + "_seq";
        stmt.execute("SELECT setval('" + seqName + "', COALESCE((SELECT MAX(" + column + ") FROM " + table + "), 0))");
        log.info("Reset PostgreSQL sequence {} for {} to max({})", seqName, table, column);
    }

    private void ensurePostgresSerial(Statement stmt, String table, String column) throws SQLException {
        // Check if column already has a default (serial/identity)
        String sql = "SELECT column_default, is_identity FROM information_schema.columns " +
                     "WHERE table_name = '" + table + "' AND column_name = '" + column + "'";
        try (ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                String def = rs.getString("column_default");
                String isIdentity = rs.getString("is_identity");
                if ("YES".equalsIgnoreCase(isIdentity)
                    || (def != null && (def.contains("nextval") || def.contains("serial")))) {
                    return;
                }
            }
        }

        String seqName = table + "_" + column + "_seq";
        stmt.execute("CREATE SEQUENCE IF NOT EXISTS " + seqName);
        stmt.execute("ALTER TABLE " + table + " ALTER COLUMN " + column + " SET DEFAULT nextval('" + seqName + "')");
        log.warn("Set serial default for {}.{}", table, column);
    }
}
