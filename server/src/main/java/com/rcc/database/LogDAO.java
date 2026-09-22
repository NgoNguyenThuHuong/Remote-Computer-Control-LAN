package com.rcc.database;

import com.rcc.model.Log;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class LogDAO {
    private Connection conn;

    public LogDAO() {
        this.conn = DatabaseConnection.getConnection();
    }

    public boolean insertLog(Log log) {
        String sql = "INSERT INTO Logs(client_id, action) VALUES(?,?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, log.getClientId());
            pstmt.setString(2, log.getAction());
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public List<Log> getLogsByClientId(int clientId) {
        List<Log> logs = new ArrayList<>();
        String sql = "SELECT * FROM Logs WHERE client_id = ? ORDER BY created_at DESC";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, clientId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                logs.add(new Log(rs.getInt("id"), rs.getInt("client_id"), 
                                 rs.getString("action"), rs.getString("created_at")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return logs;
    }
    
    public List<Log> getAllLogs() {
        List<Log> logs = new ArrayList<>();
        String sql = "SELECT * FROM Logs ORDER BY created_at DESC";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                logs.add(new Log(rs.getInt("id"), rs.getInt("client_id"), 
                                 rs.getString("action"), rs.getString("created_at")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return logs;
    }
}
