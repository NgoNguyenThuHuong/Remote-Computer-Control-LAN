package com.mycompany.remoteservercore.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Quản lý cấu hình toàn hệ thống (bao gồm Telegram notification).
 * Tải từ file config.properties và/hoặc biến môi trường.
 */
public class ConfigManager {

    private static final String CONFIG_FILE_NAME = "config.properties";
    private static final ConfigManager INSTANCE = new ConfigManager();

    private boolean telegramEnabled = false;
    private String telegramBotToken = "";
    private String telegramChatId = "";

    private ConfigManager() {
        loadConfig();
    }

    public static ConfigManager getInstance() {
        return INSTANCE;
    }

    /**
     * Nạp cấu hình từ file config.properties hoặc biến môi trường.
     */
    public synchronized void loadConfig() {
        Properties props = new Properties();

        // 1. Thử đọc từ file config.properties trong thư mục hiện tại hoặc thư mục con RemoteServerCore
        File configFile = new File(CONFIG_FILE_NAME);
        if (!configFile.exists()) {
            configFile = new File("RemoteServerCore", CONFIG_FILE_NAME);
        }

        if (configFile.exists() && configFile.isFile()) {
            try (InputStream is = new FileInputStream(configFile)) {
                props.load(is);
                System.out.println("[ConfigManager] Đã tải cấu hình từ file: " + configFile.getAbsolutePath());
            } catch (IOException e) {
                System.err.println("[ConfigManager] Lỗi đọc file config.properties: " + e.getMessage());
            }
        } else {
            // Thử đọc từ Classpath resource nếu có
            try (InputStream is = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE_NAME)) {
                if (is != null) {
                    props.load(is);
                    System.out.println("[ConfigManager] Đã tải cấu hình từ classpath resource.");
                }
            } catch (Exception ignored) {}
        }

        // 2. Gán giá trị với ưu tiên: File config -> Biến môi trường -> Mặc định
        String enabledStr = props.getProperty("telegram.enabled", System.getenv("TELEGRAM_ENABLED"));
        this.telegramEnabled = enabledStr != null && Boolean.parseBoolean(enabledStr.trim());

        String token = props.getProperty("telegram.bot_token", System.getenv("TELEGRAM_BOT_TOKEN"));
        this.telegramBotToken = (token != null) ? token.trim() : "";

        String chatId = props.getProperty("telegram.chat_id", System.getenv("TELEGRAM_CHAT_ID"));
        this.telegramChatId = (chatId != null) ? chatId.trim() : "";

        System.out.println("[ConfigManager] Telegram Notification Enabled: " + telegramEnabled 
                + " | Bot Token configured: " + (!telegramBotToken.isEmpty()) 
                + " | Chat ID configured: " + (!telegramChatId.isEmpty()));
    }

    public boolean isTelegramEnabled() {
        return telegramEnabled;
    }

    public String getTelegramBotToken() {
        return telegramBotToken;
    }

    public String getTelegramChatId() {
        return telegramChatId;
    }

    public synchronized void setTelegramConfig(boolean enabled, String botToken, String chatId) {
        this.telegramEnabled = enabled;
        this.telegramBotToken = (botToken != null) ? botToken.trim() : "";
        this.telegramChatId = (chatId != null) ? chatId.trim() : "";
    }
}
