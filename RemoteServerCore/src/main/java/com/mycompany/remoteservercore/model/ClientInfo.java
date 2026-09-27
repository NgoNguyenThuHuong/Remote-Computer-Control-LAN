package com.mycompany.remoteservercore.model;

import java.io.Serializable;

/**
 * Đối tượng lưu trữ thông tin hệ thống và trạng thái của máy tính Client (Agent).
 */
public class ClientInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final String STATUS_ONLINE = "ONLINE";
    public static final String STATUS_OFFLINE = "OFFLINE";
    public static final String STATUS_BUSY = "BUSY";

    private String ipAddress;
    private String hostName;
    private String macAddress;
    private String osName;
    private String currentUser;
    private String department;
    private double cpuUsage;      // Phần trăm CPU (0 - 100)
    private double ramUsage;      // Phần trăm RAM (0 - 100)
    private long totalRamMb;      // Tổng dung lượng RAM (MB)
    private long usedRamMb;       // Dung lượng RAM đã dùng (MB)
    private double diskUsage;     // Phần trăm Disk (0 - 100)
    private String status;        // ONLINE / OFFLINE / BUSY
    private long lastPingTime;    // Thời điểm nhận heartbeat gần nhất (epoch ms)
    private long connectedAt;     // Thời điểm bắt đầu kết nối (epoch ms)

    public ClientInfo() {
        this.status = STATUS_ONLINE;
        this.lastPingTime = System.currentTimeMillis();
        this.connectedAt = System.currentTimeMillis();
    }

    public ClientInfo(String ipAddress, String hostName, String macAddress, String osName) {
        this();
        this.ipAddress = ipAddress;
        this.hostName = hostName;
        this.macAddress = macAddress;
        this.osName = osName;
    }

    public void updatePing() {
        this.lastPingTime = System.currentTimeMillis();
        this.status = STATUS_ONLINE;
    }

    public boolean isOnline(long timeoutMs) {
        return STATUS_ONLINE.equalsIgnoreCase(status) &&
                (System.currentTimeMillis() - lastPingTime <= timeoutMs);
    }

    public String getFormattedRam() {
        if (totalRamMb > 0) {
            return String.format("%d / %d MB (%.1f%%)", usedRamMb, totalRamMb, ramUsage);
        }
        return String.format("%.1f%%", ramUsage);
    }

    // Getters and Setters
    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getHostName() {
        return hostName;
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    public String getMacAddress() {
        return macAddress;
    }

    public void setMacAddress(String macAddress) {
        this.macAddress = macAddress;
    }

    public String getOsName() {
        return osName;
    }

    public void setOsName(String osName) {
        this.osName = osName;
    }

    public String getCurrentUser() {
        return currentUser;
    }

    public void setCurrentUser(String currentUser) {
        this.currentUser = currentUser;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public double getCpuUsage() {
        return cpuUsage;
    }

    public void setCpuUsage(double cpuUsage) {
        this.cpuUsage = cpuUsage;
    }

    public double getRamUsage() {
        return ramUsage;
    }

    public void setRamUsage(double ramUsage) {
        this.ramUsage = ramUsage;
    }

    public long getTotalRamMb() {
        return totalRamMb;
    }

    public void setTotalRamMb(long totalRamMb) {
        this.totalRamMb = totalRamMb;
    }

    public long getUsedRamMb() {
        return usedRamMb;
    }

    public void setUsedRamMb(long usedRamMb) {
        this.usedRamMb = usedRamMb;
    }

    public double getDiskUsage() {
        return diskUsage;
    }

    public void setDiskUsage(double diskUsage) {
        this.diskUsage = diskUsage;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getLastPingTime() {
        return lastPingTime;
    }

    public void setLastPingTime(long lastPingTime) {
        this.lastPingTime = lastPingTime;
    }

    public long getConnectedAt() {
        return connectedAt;
    }

    public void setConnectedAt(long connectedAt) {
        this.connectedAt = connectedAt;
    }

    @Override
    public String toString() {
        return "ClientInfo{" +
                "ip='" + ipAddress + '\'' +
                ", hostName='" + hostName + '\'' +
                ", os='" + osName + '\'' +
                ", user='" + currentUser + '\'' +
                ", dept='" + department + '\'' +
                ", cpu=" + cpuUsage + "%" +
                ", ram=" + ramUsage + "%" +
                ", status='" + status + '\'' +
                '}';
    }
}
