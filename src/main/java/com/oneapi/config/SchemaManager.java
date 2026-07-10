package com.oneapi.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Schema migration utility.
 * Ensures `instances`, `virtual_models` and `vendors` use database-generated auto-increment ids.
 * Runs once on startup after the DataSource is initialized.
 */
public class SchemaManager {
    private static final Logger log = LoggerFactory.getLogger(SchemaManager.class);

    private final DataSource dataSource;
    private final boolean isPostgres;

    public SchemaManager(DataSource dataSource) {
        this.dataSource = dataSource;
        this.isPostgres = isPostgreSQL(dataSource);
    }

    public void migrate() {
        try {
            if (isPostgres) {
                migratePostgres();
            } else {
                migrateSqlite();
            }
        } catch (SQLException e) {
            log.error("Schema migration failed", e);
        }
    }

    private void migrateSqlite() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            ensureAutoIncrementSqlite(stmt, "instances",
                "CREATE TABLE instances (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "model_name TEXT NOT NULL, " +
                "status INTEGER DEFAULT 1, " +
                "upstream_model TEXT, " +
                "vendor_id INTEGER REFERENCES vendors(id), " +
                "created_time INTEGER, " +
                "meta TEXT, " +
                "pref REAL DEFAULT 0, " +
                "layer TEXT DEFAULT 'payg'" +
                ")");
            resetSqliteSequence(stmt, "instances");

            ensureAutoIncrementSqlite(stmt, "virtual_models",
                "CREATE TABLE virtual_models (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT UNIQUE, " +
                "match TEXT" +
                ")");
            resetSqliteSequence(stmt, "virtual_models");

            ensureAutoIncrementSqlite(stmt, "vendors",
                "CREATE TABLE vendors (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT NOT NULL, " +
                "description TEXT, " +
                "status INTEGER DEFAULT 1, " +
                "\"group\" TEXT, " +
                "priority INTEGER DEFAULT 0, " +
                "created_time INTEGER, " +
                "base_url TEXT, " +
                "api_key TEXT, " +
                "meta TEXT" +
                ")");
            resetSqliteSequence(stmt, "vendors");

            log.info("SQLite schema migration completed");
        }
    }

    private void migratePostgres() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            ensurePostgresSerial(stmt, "instances", "id");
            ensurePostgresSerial(stmt, "virtual_models", "id");
            ensurePostgresSerial(stmt, "vendors", "id");

            resetPostgresSequence(stmt, "instances", "id");
            resetPostgresSequence(stmt, "virtual_models", "id");
            resetPostgresSequence(stmt, "vendors", "id");

            log.info("PostgreSQL schema migration completed");
        }
    }

    private boolean isPostgreSQL(DataSource ds) {
        try (Connection conn = ds.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            return meta.getDatabaseProductName().toLowerCase().contains("postgres");
        } catch (SQLException e) {
            log.error("Failed to detect database type", e);
            return false;
        }
    }

    private void ensureAutoIncrementSqlite(Statement stmt, String table, String createDdl) throws SQLException {
        if (!tableExistsSqlite(stmt, table)) {
            stmt.execute(createDdl);
            log.info("Created table {} with AUTOINCREMENT", table);
            return;
        }

        if (hasAutoIncrementSqlite(stmt, table)) {
            return;
        }

        log.warn("Migrating table {} to AUTOINCREMENT id", table);
        String tempTable = table + "_temp";
        stmt.execute("DROP TABLE IF EXISTS " + tempTable);
        stmt.execute(createDdl.replaceFirst("CREATE TABLE " + table + " ", "CREATE TABLE " + tempTable + " "));

        List<String> columns = getColumnsSqlite(stmt, table);
        if (!columns.isEmpty()) {
            String cols = String.join(", ", columns);
            stmt.execute("INSERT INTO " + tempTable + " (" + cols + ") SELECT " + cols + " FROM " + table);
        }

        stmt.execute("DROP TABLE " + table);
        stmt.execute("ALTER TABLE " + tempTable + " RENAME TO " + table);
        log.info("Recreated table {} with AUTOINCREMENT", table);
    }

    private void resetSqliteSequence(Statement stmt, String table) throws SQLException {
        // sqlite_sequence may not exist if AUTOINCREMENT was never used, but it is created
        // automatically by SQLite once an AUTOINCREMENT table exists. We can safely insert/update.
        stmt.execute("DELETE FROM sqlite_sequence WHERE name = '" + table + "'");
        stmt.execute("INSERT INTO sqlite_sequence (name, seq) SELECT '" + table + "', COALESCE(MAX(id), 0) FROM " + table);
        log.info("Reset SQLite sequence for {} to max(id)", table);
    }

    private void resetPostgresSequence(Statement stmt, String table, String column) throws SQLException {
        String seqName = table + "_" + column + "_seq";
        stmt.execute("SELECT setval('" + seqName + "', COALESCE((SELECT MAX(" + column + ") FROM " + table + "), 0))");
        log.info("Reset PostgreSQL sequence {} for {} to max({})", seqName, table, column);
    }

    private boolean tableExistsSqlite(Statement stmt, String table) throws SQLException {
        try (ResultSet rs = stmt.executeQuery(
                "SELECT 1 FROM sqlite_master WHERE type='table' AND name='" + table + "'")) {
            return rs.next();
        }
    }

    private boolean hasAutoIncrementSqlite(Statement stmt, String table) throws SQLException {
        try (ResultSet rs = stmt.executeQuery(
                "SELECT sql FROM sqlite_master WHERE type='table' AND name='" + table + "'")) {
            if (rs.next()) {
                String ddl = rs.getString("sql");
                return ddl != null && ddl.toUpperCase().contains("AUTOINCREMENT");
            }
        }
        return false;
    }

    private List<String> getColumnsSqlite(Statement stmt, String table) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                String name = rs.getString("name");
                if (!"id".equalsIgnoreCase(name)) {
                    columns.add(name);
                }
            }
        }
        return columns;
    }

    private void ensurePostgresSerial(Statement stmt, String table, String column) throws SQLException {
        // Check if column already has a default (serial/identity)
        String sql = "SELECT column_default FROM information_schema.columns " +
                     "WHERE table_name = '" + table + "' AND column_name = '" + column + "'";
        try (ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                String def = rs.getString("column_default");
                if (def != null && (def.contains("nextval") || def.contains("serial"))) {
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
