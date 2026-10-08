/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.mycompany.remoteservercore.database;

import com.mycompany.remoteservercore.model.LogEntry;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class LogDAO {

    private static Connection writeConn = null;

    private static Connection getWriteConnection() throws SQLException {
        if (writeConn == null || writeConn.isClosed()) {
            writeConn = DatabaseManager.getConnection();
        }
        return writeConn;
    }

    public static synchronized void saveLog(LogEntry log) {
        String sql = "INSERT INTO logs (client_ip, event_type, description) VALUES (?, ?, ?)";
        try {
            Connection conn = getWriteConnection();
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, log.getClientIp());
                pstmt.setString(2, log.getEventType());
                pstmt.setString(3, log.getDescription());
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("[LogDAO] Error saving log: " + e.getMessage());
            try {
                writeConn = null;
                Connection conn = getWriteConnection();
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, log.getClientIp());
                    pstmt.setString(2, log.getEventType());
                    pstmt.setString(3, log.getDescription());
                    pstmt.executeUpdate();
                }
            } catch (SQLException retryEx) {
                System.err.println("[LogDAO] Retry failed: " + retryEx.getMessage());
            }
        }
    }

    public static List<LogEntry> getAllLogs() {
        return getFilteredLogs("", "");
    }

    public static List<LogEntry> getFilteredLogs(String filterClient, String filterEvent) {
        List<LogEntry> logs = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM logs WHERE 1=1");
        
        boolean hasClient = filterClient != null && !filterClient.trim().isEmpty();
        boolean hasEvent = filterEvent != null && !filterEvent.trim().isEmpty() && !filterEvent.equalsIgnoreCase("All");

        if (hasClient) {
            sql.append(" AND client_ip LIKE ?");
        }
        if (hasEvent) {
            sql.append(" AND event_type = ?");
        }
        sql.append(" ORDER BY id DESC");

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
            
            int paramIndex = 1;
            if (hasClient) {
                pstmt.setString(paramIndex++, "%" + filterClient.trim() + "%");
            }
            if (hasEvent) {
                pstmt.setString(paramIndex++, filterEvent.trim());
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    LogEntry entry = new LogEntry(
                            rs.getInt("id"),
                            rs.getString("timestamp"),
                            rs.getString("client_ip"),
                            rs.getString("event_type"),
                            rs.getString("description")
                    );
                    logs.add(entry);
                }
            }
        } catch (SQLException e) {
            System.err.println("[LogDAO] Error querying logs: " + e.getMessage());
        }
        return logs;
    }
}
