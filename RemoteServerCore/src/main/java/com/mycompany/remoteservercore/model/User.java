package com.mycompany.remoteservercore.model;

/**
 * Model đại diện cho tài khoản người dùng trong hệ thống.
 * Role quy định mức độ quyền hạn: ADMIN toàn quyền, USER chỉ xem.
 */
public class User {

    // ─── Enum phân quyền ───────────────────────────────────────────────────────
    public enum Role {
        ADMIN,  // Toàn quyền điều khiển
        USER    // Chỉ được xem (read-only)
    }

    // ─── Fields ────────────────────────────────────────────────────────────────
    private int    id;
    private String username;
    private String passwordHash; // Mật khẩu đã băm SHA-256, không lưu raw
    private Role   role;

    // ─── Constructor đầy đủ (dùng khi load từ DB) ──────────────────────────────
    public User(int id, String username, String passwordHash, Role role) {
        this.id           = id;
        this.username     = username;
        this.passwordHash = passwordHash;
        this.role         = role;
    }

    // ─── Getters ───────────────────────────────────────────────────────────────
    public int    getId()           { return id; }
    public String getUsername()     { return username; }
    public String getPasswordHash() { return passwordHash; }
    public Role   getRole()         { return role; }

    // ─── Setters ───────────────────────────────────────────────────────────────
    public void setId(int id)                    { this.id = id; }
    public void setUsername(String username)     { this.username = username; }
    public void setPasswordHash(String hash)     { this.passwordHash = hash; }
    public void setRole(Role role)               { this.role = role; }

    // ─── Tiện ích ──────────────────────────────────────────────────────────────
    /** Kiểm tra nhanh xem user có phải ADMIN không */
    public boolean isAdmin() {
        return Role.ADMIN.equals(this.role);
    }

    @Override
    public String toString() {
        return "User{id=" + id + ", username='" + username + "', role=" + role + "}";
    }
}
