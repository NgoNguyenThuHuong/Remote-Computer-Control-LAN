package com.rcc.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonProtocol {
    private static final ObjectMapper mapper = new ObjectMapper();

    // Chuyển đối tượng Message thành chuỗi JSON
    public static String serialize(Message message) throws Exception {
        return mapper.writeValueAsString(message);
    }

    // Chuyển chuỗi JSON thành đối tượng Message
    public static Message deserialize(String json) throws Exception {
        return mapper.readValue(json, Message.class);
    }
}