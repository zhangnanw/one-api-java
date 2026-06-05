package com.oneapi.config;

import java.nio.file.Path;
import java.nio.file.Paths;

public record AppConfig(
    int port,
    String sqlitePath
) {
    private static final int DEFAULT_PORT = 13000;
    private static final String HOME = System.getProperty("user.home");

    public static AppConfig load() {
        int port = getEnvInt("PORT", DEFAULT_PORT);

        // SQLite: same path as Go version → ~/.one-api/one-api.db
        String sqlitePath = System.getenv("SQLITE_PATH");
        if (sqlitePath == null || sqlitePath.isEmpty()) {
            sqlitePath = Paths.get(HOME, ".one-api", "one-api.db").toString();
        }

        // Override with SQL_DSN if set (for MySQL/PostgreSQL in the future)
        String sqlDsn = System.getenv("SQL_DSN");
        if (sqlDsn != null && !sqlDsn.isEmpty()) {
            sqlitePath = sqlDsn;
        }

        return new AppConfig(port, sqlitePath);
    }

    private static int getEnvInt(String name, int defaultVal) {
        String val = System.getenv(name);
        if (val != null && !val.isEmpty()) {
            try { return Integer.parseInt(val); }
            catch (NumberFormatException ignored) {}
        }
        return defaultVal;
    }
}
