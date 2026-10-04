package com.mycompany.remoteservercore.model;

import java.io.Serializable;

/**
 * Model đại diện cho tài khoản người dùng trong hệ thống.
 * Role quy định mức độ quyền hạn: ADMIN toàn quyền, USER chỉ xem.
 */
public class User implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Role {
        ADMIN,  // Toàn quyền điều khiển
        USER    // Chỉ được xem (read-only)
    }

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_MANAGER = "MANAGER";
    public static final String ROLE_OBSERVER = "OBSERVER";

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_LOCKED = "LOCKED";

    private int id;
    private String username;
    private String passwordHash;
    private String fullName;
    private Role role;
    private String status;
    private String createdAt;

    public User() {
        this.role = Role.ADMIN;
        this.status = STATUS_ACTIVE;
    }

    public User(int id, String username, String passwordHash, Role role) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.status = STATUS_ACTIVE;
    }

    public User(String username, String passwordHash, String fullName, Role role) {
        this();
        this.username = username;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role;
    }

    public boolean isAdmin() {
        return Role.ADMIN.equals(this.role);
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", role=" + role +
                ", status='" + status + '\'' +
                '}';
    }
}
