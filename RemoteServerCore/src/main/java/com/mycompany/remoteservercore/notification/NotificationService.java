package com.mycompany.remoteservercore.notification;

import com.mycompany.remoteservercore.config.ConfigManager;
import com.mycompany.remoteservercore.database.LogDAO;
import com.mycompany.remoteservercore.model.LogEntry;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Service độc lập gửi thông báo sự kiện qua Telegram Bot API (Issue #39 / Issue 18).
 * 
 * <p>Lưu ý kỹ thuật:
 * <ul>
 *   <li>Thực thi bất đồng bộ (Asynchronous) thông qua ExecutorService với Daemon thread.</li>
 *   <li>Không bao giờ làm nghẽn (non-blocking) luồng chính của Server hay GUI.</li>
 *   <li>Bọc try-catch xung quanh mọi thao tác HTTP Telegram API; lỗi mạng hay timeout KHÔNG crash hệ thống.</li>
 *   <li>Tự động ghi Activity Log vào SQLite (LogDAO) để bảo đảm dấu vết sự kiện ngay cả khi Telegram tắt hoặc lỗi.</li>
 * </ul>
 */
public class NotificationService {

    private static final NotificationService INSTANCE = new NotificationService();
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ExecutorService executor;

    private NotificationService() {
        // Sử dụng Daemon Thread để không cản trở việc tắt JVM/Server
        this.executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "TelegramNotificationWorker");
                t.setDaemon(true);
                return t;
            }
        });
    }

    public static NotificationService getInstance() {
        return INSTANCE;
    }

    /**
     * Gửi thông báo Client vừa kết nối Online.
     */
    public void notifyClientOnline(String clientIp, String hostName) {
        String msg = String.format("🟢 [Client Online]\n• Máy trạm: %s\n• IP: %s\n• Thời gian: %s",
                (hostName != null) ? hostName : "Unknown",
                (clientIp != null) ? clientIp : "Unknown",
                LocalDateTime.now().format(DATE_FORMATTER));
        
        sendNotification("Client Online", clientIp, msg);
    }

    /**
     * Gửi thông báo Client vừa ngắt kết nối Offline.
     */
    public void notifyClientOffline(String clientIp, String hostName) {
        String msg = String.format("🔴 [Client Offline]\n• Máy trạm: %s\n• IP: %s\n• Thời gian: %s",
                (hostName != null) ? hostName : "Unknown",
                (clientIp != null) ? clientIp : "Unknown",
                LocalDateTime.now().format(DATE_FORMATTER));

        sendNotification("Client Offline", clientIp, msg);
    }

    /**
     * Gửi thông báo thực thi lệnh thất bại trên Client.
     */
    public void notifyCommandFailed(String clientIp, String command, String errorDetails) {
        String msg = String.format("⚠️ [Command Failed]\n• IP mục tiêu: %s\n• Lệnh: %s\n• Chi tiết lỗi: %s\n• Thời gian: %s",
                (clientIp != null) ? clientIp : "Unknown",
                (command != null) ? command : "Unknown",
                (errorDetails != null) ? errorDetails : "N/A",
                LocalDateTime.now().format(DATE_FORMATTER));

        sendNotification("Command Failed", clientIp, msg);
    }

    /**
     * Gửi thông báo thực thi lệnh thành công trên Client.
     */
    public void notifyCommandExecuted(String clientIp, String command, String resultDetails) {
        String msg = String.format("⚡ [Command Executed]\n• IP mục tiêu: %s\n• Lệnh: %s\n• Kết quả: %s\n• Thời gian: %s",
                (clientIp != null) ? clientIp : "Unknown",
                (command != null) ? command : "Unknown",
                (resultDetails != null) ? resultDetails : "Success",
                LocalDateTime.now().format(DATE_FORMATTER));

        sendNotification("Command Executed", clientIp, msg);
    }

    /**
     * Gửi thông báo nhận ảnh chụp màn hình thành công từ Client.
     */
    public void notifyScreenshotCaptured(String clientIp, String details) {
        String msg = String.format("📷 [Screenshot Received]\n• IP mục tiêu: %s\n• Chi tiết: %s\n• Thời gian: %s",
                (clientIp != null) ? clientIp : "Unknown",
                (details != null) ? details : "Chụp màn hình thành công",
                LocalDateTime.now().format(DATE_FORMATTER));

        sendNotification("Screenshot Received", clientIp, msg);
    }

    /**
     * Gửi thông báo sự cố/lỗi hệ thống Server.
     */
    public void notifySystemError(String component, String errorMessage) {
        String msg = String.format("🚨 [System Error]\n• Thành phần: %s\n• Thông báo lỗi: %s\n• Thời gian: %s",
                (component != null) ? component : "Server Core",
                (errorMessage != null) ? errorMessage : "Unknown error",
                LocalDateTime.now().format(DATE_FORMATTER));

        sendNotification("System Error", "Server", msg);
    }

    /**
     * Gửi thông báo tùy chỉnh tổng quát.
     *
     * @param eventType Loại sự kiện (Client Online, Command Failed, System Error...)
     * @param clientIp IP máy trạm liên quan hoặc "Server"
     * @param message Nội dung chi tiết cần gửi
     */
    public void sendNotification(String eventType, String clientIp, String message) {
        // Gửi bất đồng bộ qua Telegram Bot API (nếu được bật)
        executor.submit(() -> {
            try {
                ConfigManager config = ConfigManager.getInstance();
                if (!config.isTelegramEnabled()) {
                    System.out.println("[NotificationService] Telegram notification disabled in config.");
                    return;
                }

                String token = config.getTelegramBotToken();
                String chatId = config.getTelegramChatId();

                if (token.isEmpty() || chatId.isEmpty()) {
                    System.err.println("[NotificationService] Bot Token hoặc Chat ID chưa được cấu hình.");
                    return;
                }

                sendTelegramHttpRequest(token, chatId, message);
            } catch (Exception e) {
                // Bọc try-catch tuyệt đối để không bao giờ làm crash hệ thống
                System.err.println("[NotificationService] Lỗi gửi Telegram notification: " + e.getMessage());
            }
        });
    }

    /**
     * Thực hiện HTTP POST request tới Telegram Bot API.
     * Cài đặt timeout rõ ràng (5 giây) để tránh treo Server nếu mất mạng.
     */
    private void sendTelegramHttpRequest(String botToken, String chatId, String text) {
        HttpURLConnection conn = null;
        try {
            String apiUrl = "https://api.telegram.org/bot" + botToken + "/sendMessage";
            URL url = new URL(apiUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setConnectTimeout(5000); // Timeout kết nối 5 giây
            conn.setReadTimeout(5000);    // Timeout đọc 5 giây
            conn.setDoOutput(true);

            // Escape chuỗi JSON an toàn
            String jsonPayload = String.format("{\"chat_id\":\"%s\",\"text\":%s}",
                    escapeJson(chatId),
                    escapeJsonString(text));

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                System.out.println("[NotificationService] Đã gửi thông báo Telegram thành công!");
            } else {
                System.err.println("[NotificationService] Gửi Telegram thất bại. Response code: " + responseCode);
            }
        } catch (Exception e) {
            System.err.println("[NotificationService] Lỗi kết nối HTTP Telegram API: " + e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private String escapeJsonString(String str) {
        if (str == null) return "\"\"";
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < ' ') {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                    break;
            }
        }
        sb.append("\"");
        return sb.toString();
    }

    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
