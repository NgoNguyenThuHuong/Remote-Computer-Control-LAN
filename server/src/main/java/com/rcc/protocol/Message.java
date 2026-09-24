package com.rcc.protocol;

public class Message {
    private MessageType type;
    private String sender;
    private long timestamp;
    private String status;
    private Object payload;

    public Message() {
        this.timestamp = System.currentTimeMillis() / 1000L;
    }

    public Message(MessageType type, String sender, Object payload) {
        this();
        this.type = type;
        this.sender = sender;
        this.payload = payload;
    }

    public Message(MessageType type, String sender, String status, Object payload) {
        this(type, sender, payload);
        this.status = status;
    }

    // Getters and Setters
    public MessageType getType() { return type; }
    public void setType(MessageType type) { this.type = type; }

    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Object getPayload() { return payload; }
    public void setPayload(Object payload) { this.payload = payload; }
}