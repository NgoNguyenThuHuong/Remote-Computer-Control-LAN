package com.rcc.database;

import com.rcc.model.Client;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ClientDAO {
    private Connection conn;

    public ClientDAO() {
        this.conn = DatabaseConnection.getConnection();
    }

    public boolean insertClient(Client client) {
        String sql = "INSERT INTO Clients(ip_address, mac_address, computer_name, status) VALUES(?,?,?,?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, client.getIpAddress());
            pstmt.setString(2, client.getMacAddress());
            pstmt.setString(3, client.getComputerName());
            pstmt.setString(4, client.getStatus());
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public Client getClientById(int id) {
        String sql = "SELECT * FROM Clients WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new Client(rs.getInt("id"), rs.getString("ip_address"), 
                                  rs.getString("mac_address"), rs.getString("computer_name"), 
                                  rs.getString("status"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
    
    public List<Client> getAllClients() {
        List<Client> clients = new ArrayList<>();
        String sql = "SELECT * FROM Clients";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                clients.add(new Client(rs.getInt("id"), rs.getString("ip_address"), 
                                       rs.getString("mac_address"), rs.getString("computer_name"), 
                                       rs.getString("status")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return clients;
    }

    public boolean updateClient(Client client) {
        String sql = "UPDATE Clients SET ip_address = ?, mac_address = ?, computer_name = ?, status = ? WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, client.getIpAddress());
            pstmt.setString(2, client.getMacAddress());
            pstmt.setString(3, client.getComputerName());
            pstmt.setString(4, client.getStatus());
            pstmt.setInt(5, client.getId());
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean deleteClient(int id) {
        String sql = "DELETE FROM Clients WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}
