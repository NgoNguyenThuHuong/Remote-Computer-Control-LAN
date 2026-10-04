package com.mycompany.remoteservercore.database;

import com.mycompany.remoteservercore.model.User;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Data Access Object cho bảng {@code users}.
 * Mọi câu query đều dùng PreparedStatement để chống SQL Injection.
 */
public class UserDAO {

    /**
     * Tìm user theo username.
     *
     * @param username tên đăng nhập cần tìm
     * @return đối tượng {@link User} nếu tìm thấy, hoặc {@code null} nếu không có
     */
    public User findByUsername(String username) {
        String sql = "SELECT id, username, password_hash, role FROM users WHERE username = ?";

        try (Connection   conn = DatabaseManager.getConnection();
             PreparedStatement ps   = conn.prepareStatement(sql)) {

            // Dùng PreparedStatement — tránh SQL Injection
            ps.setString(1, username);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int    id           = rs.getInt("id");
                    String uname        = rs.getString("username");
                    String passwordHash = rs.getString("password_hash");
                    String roleStr      = rs.getString("role");

                    // Ánh xạ chuỗi role sang enum, mặc định USER nếu không nhận ra
                    User.Role role;
                    try {
                        role = User.Role.valueOf(roleStr);
                    } catch (IllegalArgumentException e) {
                        System.err.println("[UserDAO] Role không hợp lệ: " + roleStr + " → mặc định USER");
                        role = User.Role.USER;
                    }

                    return new User(id, uname, passwordHash, role);
                }
            }
        } catch (SQLException e) {
            System.err.println("[UserDAO] Lỗi truy vấn findByUsername: " + e.getMessage());
        }

        return null; // Không tìm thấy user
    }
}
