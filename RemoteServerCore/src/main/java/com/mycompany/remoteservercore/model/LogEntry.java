/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.mycompany.remoteservercore.model;

public class LogEntry {
    private int id;
    private String timestamp;
    private String clientIp;
    private String eventType;
    private String description;

    public LogEntry(int id, String timestamp, String clientIp, String eventType, String description) {
        this.id = id;
        this.timestamp = timestamp;
        this.clientIp = clientIp;
        this.eventType = eventType;
        this.description = description;
    }

    public LogEntry(String clientIp, String eventType, String description) {
        this.clientIp = clientIp;
        this.eventType = eventType;
        this.description = description;
    }

    public int getId() { return id; }
    public String getTimestamp() { return timestamp; }
    public String getClientIp() { return clientIp; }
    public String getEventType() { return eventType; }
    public String getDescription() { return description; }
}
