package com.mycompany.remoteservercore.database;

import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Quản lý kết nối SQLite và khởi tạo schema.
 *
 * <p>Singleton — lấy Connection qua {@link #getConnection()}.
 * Khi khởi động lần đầu sẽ:
 *  1. Tạo bảng {@code users} nếu chưa có.
 *  2. Seed 2 tài khoản mặc định (admin / user) nếu bảng rỗng.
 */
public class DatabaseManager {

    // ─── Đường dẫn file DB (đặt cạnh thư mục chạy ứng dụng) ──────────────────
    private static final String DB_PATH = Paths.get("server.db").toAbsolutePath().toString();
    private static final String DB_URL  = "jdbc:sqlite:" + DB_PATH;

    // ─── Tài khoản seed mặc định ──────────────────────────────────────────────
    private static final String SEED_ADMIN_USER = "admin";
    private static final String SEED_ADMIN_PASS = "admin123"; // sẽ được băm

    private static final String SEED_USER_USER  = "user";
    private static final String SEED_USER_PASS  = "user123";  // sẽ được băm

    // ─── Singleton connection ──────────────────────────────────────────────────
    private static Connection connection;

    /** Khởi tạo DB: tạo bảng + seed nếu cần. Gọi một lần khi ứng dụng khởi động. */
    public static void initialize() {
        try {
            connection = DriverManager.getConnection(DB_URL);
            connection.setAutoCommit(true);
            System.out.println("[DB] Kết nối SQLite thành công: " + DB_PATH);
            createUsersTable();
            seedDefaultUsers();
        } catch (SQLException e) {
            System.err.println("[DB] Lỗi khởi tạo database: " + e.getMessage());
            throw new RuntimeException("Không thể khởi tạo database", e);
        }
    }

    /** Trả về Connection dùng chung. Tự động mở lại nếu bị đóng. */
    public static Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(DB_URL);
            connection.setAutoCommit(true);
        }
        return connection;
    }

    /** Đóng kết nối khi tắt ứng dụng. */
    public static void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("[DB] Đã đóng kết nối SQLite.");
            }
        } catch (SQLException e) {
            System.err.println("[DB] Lỗi đóng connection: " + e.getMessage());
        }
    }

    // ─── Private helpers ───────────────────────────────────────────────────────

    /** Tạo bảng users nếu chưa tồn tại */
    private static void createUsersTable() throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS users (
                    id            INTEGER PRIMARY KEY AUTOINCREMENT,
                    username      TEXT NOT NULL UNIQUE,
                    password_hash TEXT NOT NULL,
                    role          TEXT NOT NULL DEFAULT 'USER'
                )
                """;
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
            System.out.println("[DB] Bảng 'users' đã sẵn sàng.");
        }
    }

    /** Seed tài khoản admin + user nếu bảng đang rỗng */
    private static void seedDefaultUsers() throws SQLException {
        // Kiểm tra có bản ghi nào chưa
        String countSql = "SELECT COUNT(*) FROM users";
        try (Statement stmt    = connection.createStatement();
             ResultSet rs      = stmt.executeQuery(countSql)) {
            if (rs.next() && rs.getInt(1) > 0) {
                System.out.println("[DB] Bảng users đã có dữ liệu, bỏ qua seed.");
                return;
            }
        }

        // Chèn tài khoản mặc định
        String insertSql = "INSERT INTO users (username, password_hash, role) VALUES (?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(insertSql)) {
            // Tài khoản admin
            ps.setString(1, SEED_ADMIN_USER);
            ps.setString(2, sha256(SEED_ADMIN_PASS));
            ps.setString(3, "ADMIN");
            ps.executeUpdate();

            // Tài khoản user thường
            ps.setString(1, SEED_USER_USER);
            ps.setString(2, sha256(SEED_USER_PASS));
            ps.setString(3, "USER");
            ps.executeUpdate();

            System.out.println("[DB] Đã seed tài khoản mặc định: admin (ADMIN), user (USER).");
        }
    }

    /**
     * Băm mật khẩu bằng SHA-256, trả về chuỗi hex.
     * Sử dụng chung với AuthService để đảm bảo nhất quán.
     */
    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 không khả dụng", e);
        }
    }
}
