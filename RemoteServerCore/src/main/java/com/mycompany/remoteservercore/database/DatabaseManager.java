package com.mycompany.remoteservercore.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {
    private static final String URL = "jdbc:sqlite:server_logs.db";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL);
    }

    public static void initializeDatabase() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            
            String createLogsTable = "CREATE TABLE IF NOT EXISTS logs (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                    "client_ip TEXT NOT NULL, " +
                    "event_type TEXT NOT NULL, " +
                    "description TEXT NOT NULL" +
                    ");";
            stmt.execute(createLogsTable);
            System.out.println("[DB] Initialized logs table.");
            
        } catch (SQLException e) {
            System.err.println("[DB] Error initializing database: " + e.getMessage());
        }
    }
}
