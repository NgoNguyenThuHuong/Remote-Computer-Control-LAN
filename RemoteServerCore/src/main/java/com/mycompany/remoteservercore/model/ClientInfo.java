package com.mycompany.remoteservercore.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Model đại diện thông tin một Client đang/đã kết nối đến Server.
 *
 * <p>Lưu trữ in-memory — {@link com.mycompany.remoteservercore.core.ClientManager}
 * quản lý danh sách và thông báo cho Dashboard qua listener khi có thay đổi.
 */
public class ClientInfo {

    // ─── Enum trạng thái kết nối ─────────────────────────────────────────────
    public enum Status {
        ONLINE,   // Đang kết nối, heartbeat đang hoạt động
        OFFLINE   // Đã ngắt kết nối hoặc heartbeat timeout
    }

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy");

    // ─── Fields ──────────────────────────────────────────────────────────────
    private final String        id;            // IP:port làm unique key
    private       String        hostname;      // Tên máy tính (hostname)
    private       String        ipAddress;     // Địa chỉ IP
    private       Status        status;        // ONLINE / OFFLINE
    private       LocalDateTime connectedAt;   // Thời điểm kết nối lần đầu
    private       LocalDateTime lastHeartbeat; // Lần cuối nhận heartbeat

    // ─── Constructor ─────────────────────────────────────────────────────────
    public ClientInfo(String id, String hostname, String ipAddress) {
        this.id            = id;
        this.hostname      = hostname;
        this.ipAddress     = ipAddress;
        this.status        = Status.ONLINE;
        this.connectedAt   = LocalDateTime.now();
        this.lastHeartbeat = LocalDateTime.now();
    }

    // ─── Getters ─────────────────────────────────────────────────────────────
    public String        getId()            { return id; }
    public String        getHostname()      { return hostname; }
    public String        getIpAddress()     { return ipAddress; }
    public Status        getStatus()        { return status; }
    public LocalDateTime getConnectedAt()   { return connectedAt; }
    public LocalDateTime getLastHeartbeat() { return lastHeartbeat; }

    // ─── Setters ─────────────────────────────────────────────────────────────
    public void setHostname(String hostname)         { this.hostname = hostname; }
    public void setIpAddress(String ipAddress)       { this.ipAddress = ipAddress; }
    public void setStatus(Status status)             { this.status = status; }
    public void setLastHeartbeat(LocalDateTime time) { this.lastHeartbeat = time; }

    /** Cập nhật heartbeat về thời điểm hiện tại, đồng thời set ONLINE */
    public void updateHeartbeat() {
        this.lastHeartbeat = LocalDateTime.now();
        this.status        = Status.ONLINE;
    }

    /** Đánh dấu client offline */
    public void markOffline() {
        this.status = Status.OFFLINE;
    }

    /** Thời điểm kết nối dạng chuỗi để hiển thị trên bảng */
    public String getConnectedAtFormatted() {
        return connectedAt != null ? connectedAt.format(FMT) : "-";
    }

    /** Heartbeat cuối dạng chuỗi */
    public String getLastHeartbeatFormatted() {
        return lastHeartbeat != null ? lastHeartbeat.format(FMT) : "-";
    }

    @Override
    public String toString() {
        return "ClientInfo{id='" + id + "', hostname='" + hostname
                + "', ip='" + ipAddress + "', status=" + status + "}";
    }
}
