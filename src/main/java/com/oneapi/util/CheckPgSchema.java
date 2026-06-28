package com.oneapi.util;

import java.sql.*;

public class CheckPgSchema {
    public static void main(String[] args) throws Exception {
        Class.forName("org.postgresql.Driver");
        String url = "jdbc:postgresql://10.0.0.147:5432/oneapi";
        String user = "oneapi";
        String password = "OneApi_PG_2026";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            DatabaseMetaData meta = conn.getMetaData();
            ResultSet cols = meta.getColumns(null, "public", "instances", null);
            System.out.println("=== instances table columns ===");
            while (cols.next()) {
                String colName = cols.getString("COLUMN_NAME");
                String type = cols.getString("TYPE_NAME");
                int size = cols.getInt("COLUMN_SIZE");
                System.out.printf("  %-20s %s%n", colName, type + (size > 0 ? "(" + size + ")" : ""));
            }
            cols.close();

            System.out.println("\n=== all tables ===");
            ResultSet tables = meta.getTables(null, "public", null, new String[]{"TABLE"});
            while (tables.next()) {
                System.out.println("  " + tables.getString("TABLE_NAME"));
            }
            tables.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
