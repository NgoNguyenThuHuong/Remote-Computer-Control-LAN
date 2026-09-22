package com.rcc.model;

public class Log {
    private int id;
    private int clientId;
    private String action;
    private String createdAt;

    public Log() {
    }

    public Log(int id, int clientId, String action, String createdAt) {
        this.id = id;
        this.clientId = clientId;
        this.action = action;
        this.createdAt = createdAt;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getClientId() { return clientId; }
    public void setClientId(int clientId) { this.clientId = clientId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "Log{" +
                "id=" + id +
                ", clientId=" + clientId +
                ", action='" + action + '\'' +
                ", createdAt='" + createdAt + '\'' +
                '}';
    }
}
