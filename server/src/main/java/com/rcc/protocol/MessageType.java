package com.rcc.protocol;

public enum MessageType {
    LOGIN,
    LOGIN_RESPONSE,
    HEARTBEAT,
    HEARTBEAT_ACK,
    CLIENT_INFO,
    COMMAND,
    COMMAND_RESULT,
    CHAT,
    SCREENSHOT_REQUEST,
    SCREENSHOT_RESPONSE,
    WEBSITE_BLOCKING,
    ERROR
}