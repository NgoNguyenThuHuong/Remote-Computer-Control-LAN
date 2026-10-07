package com.rcc.database;

import java.io.BufferedReader;
import java.io.FileReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.io.File;

public class DatabaseConnection {
    private static final String DB_URL = "jdbc:sqlite:database/rcc.db";
    private static Connection connection = null;

    private DatabaseConnection() {
    }

    public static Connection getConnection() {
        if (connection == null) {
            try {
                // Ensure the database directory exists
                File dbDir = new File("database");
                if (!dbDir.exists()) {
                    dbDir.mkdirs();
                }

                connection = DriverManager.getConnection(DB_URL);
                initializeDatabase();
            } catch (SQLException e) {
                System.err.println("Error connecting to SQLite database");
                e.printStackTrace();
            }
        }
        return connection;
    }

    private static void initializeDatabase() {
        try (Statement stmt = connection.createStatement()) {
            File initSql = new File("database/init.sql");
            if (initSql.exists()) {
                BufferedReader reader = new BufferedReader(new FileReader(initSql));
                StringBuilder sql = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sql.append(line).append("\n");
                    if (line.trim().endsWith(";")) {
                        stmt.execute(sql.toString());
                        sql.setLength(0);
                    }
                }
                reader.close();
            } else {
                System.out.println("init.sql not found at " + initSql.getAbsolutePath());
            }
        } catch (Exception e) {
            System.err.println("Error initializing database");
            e.printStackTrace();
        }
    }
}
