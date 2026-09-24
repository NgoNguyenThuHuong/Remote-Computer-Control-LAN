package com.rcc.model;

public class Client {
    private int id;
    private String ipAddress;
    private String macAddress;
    private String computerName;
    private String status;

    public Client() {
    }

    public Client(int id, String ipAddress, String macAddress, String computerName, String status) {
        this.id = id;
        this.ipAddress = ipAddress;
        this.macAddress = macAddress;
        this.computerName = computerName;
        this.status = status;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public String getMacAddress() { return macAddress; }
    public void setMacAddress(String macAddress) { this.macAddress = macAddress; }
    public String getComputerName() { return computerName; }
    public void setComputerName(String computerName) { this.computerName = computerName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    @Override
    public String toString() {
        return "Client{" +
                "id=" + id +
                ", ipAddress='" + ipAddress + '\'' +
                ", macAddress='" + macAddress + '\'' +
                ", computerName='" + computerName + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}
