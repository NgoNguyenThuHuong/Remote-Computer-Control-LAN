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
 * Quản lý kết nối SQLite và khởi tạo schema cho hệ thống Server.
 *
 * Mỗi lần gọi {@link #getConnection()} trả về một Connection mới để an toàn cho đa luồng
 * và tương thích với mô hình try-with-resources.
 */
public class DatabaseManager {

    private static final String DB_PATH = Paths.get("server.db").toAbsolutePath().toString();
    private static final String DB_URL  = "jdbc:sqlite:" + DB_PATH;

    private static final String SEED_ADMIN_USER = "admin";
    private static final String SEED_ADMIN_PASS = "admin123";

    private static final String SEED_USER_USER  = "user";
    private static final String SEED_USER_PASS  = "user123";

    private static volatile boolean initialized = false;

    /** Khởi tạo DB: tạo bảng users & logs + seed nếu cần. Gọi một lần khi ứng dụng khởi động. */
    public static synchronized void initialize() {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement pragmaStmt = conn.createStatement()) {
            System.out.println("[DB] Kết nối SQLite thành công: " + DB_PATH);
            try {
                pragmaStmt.execute("PRAGMA journal_mode = WAL;");
                pragmaStmt.execute("PRAGMA busy_timeout = 5000;");
            } catch (Exception ignored) {}
            createUsersTable(conn);
            createLogsTable(conn);
            seedDefaultUsers(conn);
            initialized = true;
        } catch (SQLException e) {
            System.err.println("[DB] Lỗi khởi tạo database: " + e.getMessage());
            throw new RuntimeException("Không thể khởi tạo database", e);
        }
    }

    /** Alias tương thích cho initialize() */
    public static void initializeDatabase() {
        initialize();
    }

    /** Trả về Connection mới từ DriverManager, an toàn cho đa luồng và try-with-resources. */
    public static Connection getConnection() throws SQLException {
        if (!initialized) {
            synchronized (DatabaseManager.class) {
                if (!initialized) {
                    try {
                        initialize();
                    } catch (Exception ignored) {}
                }
            }
        }
        Connection conn = DriverManager.getConnection(DB_URL);
        try (Statement s = conn.createStatement()) {
            s.execute("PRAGMA busy_timeout = 5000;");
        } catch (Exception ignored) {}
        return conn;
    }

    /** Đóng kết nối khi tắt ứng dụng (tương thích ngược). */
    public static void close() {
        System.out.println("[DB] SQLite database manager closed.");
    }

    private static void createUsersTable(Connection conn) throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS users (
                    id            INTEGER PRIMARY KEY AUTOINCREMENT,
                    username      TEXT NOT NULL UNIQUE,
                    password_hash TEXT NOT NULL,
                    role          TEXT NOT NULL DEFAULT 'USER'
                )
                """;
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            System.out.println("[DB] Bảng 'users' đã sẵn sàng.");
        }
    }

    private static void createLogsTable(Connection conn) throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS logs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
                    client_ip TEXT NOT NULL,
                    event_type TEXT NOT NULL,
                    description TEXT NOT NULL
                )
                """;
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            System.out.println("[DB] Bảng 'logs' đã sẵn sàng.");
        }
    }

    private static void seedDefaultUsers(Connection conn) throws SQLException {
        String countSql = "SELECT COUNT(*) FROM users";
        try (Statement stmt = conn.createStatement();
             ResultSet rs   = stmt.executeQuery(countSql)) {
            if (rs.next() && rs.getInt(1) > 0) {
                System.out.println("[DB] Bảng users đã có dữ liệu, bỏ qua seed.");
                return;
            }
        }

        String insertSql = "INSERT INTO users (username, password_hash, role) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            ps.setString(1, SEED_ADMIN_USER);
            ps.setString(2, sha256(SEED_ADMIN_PASS));
            ps.setString(3, "ADMIN");
            ps.executeUpdate();

            ps.setString(1, SEED_USER_USER);
            ps.setString(2, sha256(SEED_USER_PASS));
            ps.setString(3, "USER");
            ps.executeUpdate();

            System.out.println("[DB] Đã seed tài khoản mặc định: admin (ADMIN), user (USER).");
        }
    }

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
