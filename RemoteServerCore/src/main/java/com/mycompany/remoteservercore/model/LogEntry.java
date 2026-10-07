package com.mycompany.remoteservercore.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Cấu trúc bản ghi lịch sử hoạt động và sự kiện trong hệ thống (Log Entry).
 * Tương thích cả giao diện LogFrame (clientIp, eventType) và chuẩn hệ thống.
 */
public class LogEntry implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static final String ACTION_LOGIN = "LOGIN";
    public static final String ACTION_LOGOUT = "LOGOUT";
    public static final String ACTION_LOCK = "LOCK_MACHINE";
    public static final String ACTION_REBOOT = "REBOOT_MACHINE";
    public static final String ACTION_SHUTDOWN = "SHUTDOWN_MACHINE";
    public static final String ACTION_SCREENSHOT = "CAPTURE_SCREEN";
    public static final String ACTION_CHAT = "CHAT_MESSAGE";
    public static final String ACTION_BLOCK_WEB = "BLOCK_WEB";

    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_WARNING = "WARNING";

    private int id;
    private String timestamp;
    private String username;
    private String clientIp;
    private String eventType;
    private String description;
    private String status;

    public LogEntry() {
        this.timestamp = LocalDateTime.now().format(FORMATTER);
        this.status = STATUS_SUCCESS;
    }

    public LogEntry(String clientIp, String eventType, String description) {
        this();
        this.clientIp = clientIp;
        this.eventType = eventType;
        this.description = description;
        this.username = "SYSTEM";
    }

    public LogEntry(int id, String timestamp, String clientIp, String eventType, String description) {
        this.id = id;
        this.timestamp = timestamp;
        this.clientIp = clientIp;
        this.eventType = eventType;
        this.description = description;
        this.status = STATUS_SUCCESS;
    }

    public LogEntry(String username, String clientIp, String action, String description, String status) {
        this();
        this.username = username;
        this.clientIp = clientIp;
        this.eventType = action;
        this.description = description;
        this.status = status;
    }

    public LogEntry(int id, String timestamp, String username, String clientIp, String action, String description, String status) {
        this.id = id;
        this.timestamp = timestamp;
        this.username = username;
        this.clientIp = clientIp;
        this.eventType = action;
        this.description = description;
        this.status = status;
    }

    public static LogEntry of(String username, String targetIp, String action, String description, String status) {
        return new LogEntry(username, targetIp, action, description, status);
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public String getTargetIp() {
        return clientIp;
    }

    public void setTargetIp(String targetIp) {
        this.clientIp = targetIp;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getAction() {
        return eventType;
    }

    public void setAction(String action) {
        this.eventType = action;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "[" + timestamp + "] [" + status + "] " + username + " -> " + clientIp + " : " + eventType + " (" + description + ")";
    }
}
