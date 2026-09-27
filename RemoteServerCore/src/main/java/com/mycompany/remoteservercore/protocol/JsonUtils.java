package com.mycompany.remoteservercore.protocol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.mycompany.remoteservercore.model.MessagePacket;
import java.lang.reflect.Type;

/**
 * Tiện ích hỗ trợ Serialize và Deserialize JSON sử dụng Gson.
 * Tối ưu cho việc truyền tải dòng tin qua TCP Socket trong mạng LAN.
 */
public class JsonUtils {
    // Gson dạng nén một dòng (phù hợp gửi qua Socket bằng BufferedReader.readLine())
    private static final Gson GSON_COMPACT = new Gson();

    // Gson dạng định dạng đẹp (phục vụ hiển thị log hoặc debug)
    private static final Gson GSON_PRETTY = new GsonBuilder().setPrettyPrinting().create();

    private JsonUtils() {
        // Chặn khởi tạo đối tượng tiện ích
    }

    /**
     * Chuyển đổi đối tượng bất kỳ thành chuỗi JSON dạng 1 dòng (compact).
     *
     * @param obj Đối tượng cần serialize
     * @return Chuỗi JSON hoặc null nếu obj là null
     */
    public static String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        return GSON_COMPACT.toJson(obj);
    }

    /**
     * Chuyển đổi đối tượng bất kỳ thành chuỗi JSON có format thụt đầu dòng (pretty print).
     *
     * @param obj Đối tượng cần format
     * @return Chuỗi JSON đẹp
     */
    public static String toPrettyJson(Object obj) {
        if (obj == null) {
            return null;
        }
        return GSON_PRETTY.toJson(obj);
    }

    /**
     * Parse chuỗi JSON thành đối tượng kiểu chỉ định.
     *
     * @param json Chuỗi JSON đầu vào
     * @param classOfT Kiểu lớp cần chuyển thành
     * @param <T> Kiểu dữ liệu đích
     * @return Đối tượng kiểu T, hoặc null nếu chuỗi lỗi cú pháp
     */
    public static <T> T fromJson(String json, Class<T> classOfT) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            return GSON_COMPACT.fromJson(json, classOfT);
        } catch (JsonSyntaxException e) {
            System.err.println("[JsonUtils] Loi phan giai JSON: " + e.getMessage());
            return null;
        }
    }

    /**
     * Parse chuỗi JSON có kiểu generic (ví dụ: List<String>, Map<String, Object>).
     *
     * @param json Chuỗi JSON đầu vào
     * @param typeOfT Đối tượng Type
     * @param <T> Kiểu dữ liệu đích
     * @return Đối tượng kiểu T, hoặc null nếu lỗi
     */
    public static <T> T fromJson(String json, Type typeOfT) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            return GSON_COMPACT.fromJson(json, typeOfT);
        } catch (JsonSyntaxException e) {
            System.err.println("[JsonUtils] Loi phan giai Generic JSON: " + e.getMessage());
            return null;
        }
    }

    /**
     * Tiện ích nhanh để parse chuỗi JSON sang MessagePacket.
     *
     * @param json Chuỗi JSON gói tin
     * @return MessagePacket hoặc null
     */
    public static MessagePacket toMessagePacket(String json) {
        return fromJson(json, MessagePacket.class);
    }

    /**
     * Kiểm tra chuỗi có phải cú pháp JSON hợp lệ không.
     *
     * @param json Chuỗi cần kiểm tra
     * @return true nếu hợp lệ
     */
    public static boolean isValidJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return false;
        }
        try {
            GSON_COMPACT.fromJson(json, Object.class);
            return true;
        } catch (JsonSyntaxException e) {
            return false;
        }
    }
}
