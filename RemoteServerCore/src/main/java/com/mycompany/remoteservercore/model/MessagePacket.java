package com.mycompany.remoteservercore.model;

import java.io.Serializable;

/**
 * Khuôn mẫu gói tin chuẩn (JSON Packet) truyền tải giữa Server và Client qua TCP Socket.
 */
public class MessagePacket implements Serializable {
    private static final long serialVersionUID = 1L;

    // Các loại gói tin chuẩn trong hệ thống
    public static final String TYPE_HEARTBEAT = "HEARTBEAT";
    public static final String TYPE_SYS_INFO = "SYS_INFO";
    public static final String TYPE_COMMAND = "COMMAND";
    public static final String TYPE_CHAT = "CHAT";
    public static final String TYPE_SCREENSHOT_REQ = "SCREENSHOT_REQ";
    public static final String TYPE_SCREENSHOT_RES = "SCREENSHOT_RES";
    public static final String TYPE_BLOCK_WEB = "BLOCK_WEB";
    public static final String TYPE_LOG = "LOG";
    public static final String TYPE_ACK = "ACK";
    public static final String TYPE_ERROR = "ERROR";

    // Các lệnh điều khiển hệ thống cơ bản
    public static final String CMD_LOCK = "LOCK";
    public static final String CMD_LOGOUT = "LOGOUT";
    public static final String CMD_RESTART = "RESTART";
    public static final String CMD_SHUTDOWN = "SHUTDOWN";

    private String type;
    private String sender;
    private String target;
    private String payload;
    private long timestamp;

    public MessagePacket() {
        this.timestamp = System.currentTimeMillis();
    }

    public MessagePacket(String type, String payload) {
        this(type, "SERVER", "ALL", payload);
    }

    public MessagePacket(String type, String sender, String target, String payload) {
        this.type = type;
        this.sender = sender;
        this.target = target;
        this.payload = payload;
        this.timestamp = System.currentTimeMillis();
    }

    public MessagePacket(String type, String sender, String target, String payload, long timestamp) {
        this.type = type;
        this.sender = sender;
        this.target = target;
        this.payload = payload;
        this.timestamp = timestamp;
    }

    // Factory methods tiện ích
    public static MessagePacket createHeartbeat(String sender, String clientInfoJson) {
        return new MessagePacket(TYPE_HEARTBEAT, sender, "SERVER", clientInfoJson);
    }

    public static MessagePacket createChat(String sender, String target, String message) {
        return new MessagePacket(TYPE_CHAT, sender, target, message);
    }

    public static MessagePacket createChat(String sender, String target, String message, long timestamp) {
        return new MessagePacket(TYPE_CHAT, sender, target, message, timestamp);
    }

    public static MessagePacket createCommand(String target, String command) {
        return new MessagePacket(TYPE_COMMAND, "SERVER", target, command);
    }

    public static MessagePacket createScreenshotRequest(String target) {
        return new MessagePacket(TYPE_SCREENSHOT_REQ, "SERVER", target, "CAPTURE");
    }

    public static MessagePacket createScreenshotResponse(String sender, String base64ImageData) {
        return new MessagePacket(TYPE_SCREENSHOT_RES, sender, "SERVER", base64ImageData);
    }

    public static MessagePacket createBlockWebUpdate(String target, String domainsJson) {
        return new MessagePacket(TYPE_BLOCK_WEB, "SERVER", target, domainsJson);
    }

    public static MessagePacket createAck(String sender, String target, String message) {
        return new MessagePacket(TYPE_ACK, sender, target, message);
    }

    public static MessagePacket createError(String sender, String target, String errorDetail) {
        return new MessagePacket(TYPE_ERROR, sender, target, errorDetail);
    }

    // Getters and Setters
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "MessagePacket{" +
                "type='" + type + '\'' +
                ", sender='" + sender + '\'' +
                ", target='" + target + '\'' +
                ", payloadLen=" + (payload != null ? payload.length() : 0) +
                ", timestamp=" + timestamp +
                '}';
    }
}
