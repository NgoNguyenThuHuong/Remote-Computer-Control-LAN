package com.mycompany.remoteservercore.core;

import com.mycompany.remoteservercore.database.DatabaseManager;
import com.mycompany.remoteservercore.database.UserDAO;
import com.mycompany.remoteservercore.model.User;

/**
 * Dịch vụ xác thực (Authentication Service).
 *
 * <p>Quy trình đăng nhập:
 * <ol>
 *   <li>Nhận username + password thô từ UI.</li>
 *   <li>Tìm user trong DB qua {@link UserDAO}.</li>
 *   <li>Băm password thô bằng SHA-256 rồi so sánh với hash trong DB.</li>
 *   <li>Trả về {@link User} nếu khớp, {@code null} nếu sai.</li>
 * </ol>
 */
public class AuthService {

    private final UserDAO userDAO;

    public AuthService() {
        this.userDAO = new UserDAO();
    }

    /**
     * Xác thực đăng nhập.
     *
     * @param username tên đăng nhập
     * @param password mật khẩu thô (chưa băm) từ form nhập
     * @return {@link User} nếu đăng nhập đúng, {@code null} nếu sai username/password
     */
    public User login(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isEmpty()) {
            return null;
        }

        // Tìm user trong DB theo username
        User user = userDAO.findByUsername(username.trim());
        if (user == null) {
            System.out.println("[Auth] Không tìm thấy username: " + username);
            return null;
        }

        // Băm mật khẩu thô và so sánh với hash đã lưu
        String inputHash = DatabaseManager.sha256(password);
        if (inputHash.equals(user.getPasswordHash())) {
            System.out.println("[Auth] Đăng nhập thành công: " + user);
            return user;
        } else {
            System.out.println("[Auth] Sai mật khẩu cho user: " + username);
            return null;
        }
    }
}
