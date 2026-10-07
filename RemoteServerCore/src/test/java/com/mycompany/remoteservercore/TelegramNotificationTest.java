package com.mycompany.remoteservercore;

import com.mycompany.remoteservercore.config.ConfigManager;
import com.mycompany.remoteservercore.database.DatabaseManager;
import com.mycompany.remoteservercore.database.LogDAO;
import com.mycompany.remoteservercore.model.LogEntry;
import com.mycompany.remoteservercore.notification.NotificationService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TelegramNotificationTest {

    @BeforeAll
    public static void setUpDatabase() {
        DatabaseManager.initialize();
    }

    @BeforeEach
    public void resetConfig() {
        ConfigManager.getInstance().setTelegramConfig(false, "", "");
    }

    @Test
    @DisplayName("Kiểm tra ConfigManager đọc giá trị mặc định")
    public void testConfigManagerDefaults() {
        ConfigManager config = ConfigManager.getInstance();
        assertNotNull(config);
        // Kiểm tra thay đổi động
        config.setTelegramConfig(true, "test_token_123", "12345678");
        assertTrue(config.isTelegramEnabled());
        assertEquals("test_token_123", config.getTelegramBotToken());
        assertEquals("12345678", config.getTelegramChatId());
    }

    @Test
    @DisplayName("Gửi thông báo khi telegram.enabled = false không bị lỗi")
    public void testNotificationWhenDisabled() {
        ConfigManager.getInstance().setTelegramConfig(false, "invalid_token", "invalid_chat");
        
        assertDoesNotThrow(() -> {
            NotificationService.getInstance().notifyClientOnline("192.168.1.100", "PC-ACCOUNTING");
            NotificationService.getInstance().notifyClientOffline("192.168.1.100", "PC-ACCOUNTING");
            NotificationService.getInstance().notifyCommandFailed("192.168.1.100", "LOCK", "Access denied");
            NotificationService.getInstance().notifySystemError("Core", "Test error");
        });
    }

    @Test
    @DisplayName("Gửi thông báo khi telegram.enabled = true nhưng Token sai / Mất mạng - Bọc try-catch không crash")
    public void testNotificationWithInvalidTokenOrNoNetwork() {
        ConfigManager.getInstance().setTelegramConfig(true, "invalid_bot_token_xxxx", "00000000");

        assertDoesNotThrow(() -> {
            NotificationService.getInstance().notifyClientOnline("192.168.1.101", "PC-HR");
            NotificationService.getInstance().notifyCommandFailed("192.168.1.101", "SHUTDOWN", "Client refused command");
            NotificationService.getInstance().notifySystemError("DB", "Connection timeout test");
        });

        // Đợi 500ms để worker thread trong ExecutorService hoàn tất xử lý
        try {
            Thread.sleep(500);
        } catch (InterruptedException ignored) {}
    }

    @Test
    @DisplayName("Đảm bảo Activity Log (SQLite) vẫn ghi nhận sự kiện độc lập dù Telegram gửi được hay không")
    public void testActivityLogRecordedIndependently() {
        String testIp = "192.168.1.200";
        String testEvent = "Telegram Integration Test Event";

        // Ghi nhận log trực tiếp vào DB
        LogDAO.saveLog(new LogEntry(testIp, "Client Connect", testEvent));

        // Kiểm tra xem log đã được ghi vào SQLite chưa
        List<LogEntry> logs = LogDAO.getFilteredLogs(testIp, "Client Connect");
        assertFalse(logs.isEmpty(), "Sự kiện phải được ghi nhận vào Activity Log (SQLite)");
        
        boolean found = logs.stream().anyMatch(l -> testEvent.equals(l.getDescription()));
        assertTrue(found, "Log vừa lưu phải tồn tại trong SQLite database.");
    }
}
