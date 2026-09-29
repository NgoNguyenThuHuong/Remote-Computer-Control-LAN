package com.mycompany.remoteservercore.core;

/*
 * Entry point của Server.
 * Luồng khởi động:
 *   1. Khởi tạo DB (tạo bảng, seed tài khoản mặc định).
 *   2. Mở LoginFrame trên EDT (Event Dispatch Thread của Swing).
 *   3. ServerSocket chạy trên thread riêng — độc lập với UI.
 */

import com.mycompany.remoteservercore.database.DatabaseManager;
import com.mycompany.remoteservercore.ui.LoginFrame;

import javax.swing.*;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainServer {

    private static final int PORT = 9999;

    // Thread pool xử lý các client kết nối đến
    private static final ExecutorService pool = Executors.newFixedThreadPool(50);

    public static void main(String[] args) {
        System.out.println("=== HE THONG SERVER QUAN LY PHONG BAN ===");

        // 1. Khởi tạo database: tạo bảng + seed tài khoản mặc định
        try {
            DatabaseManager.initialize();
        } catch (RuntimeException e) {
            System.err.println("[FATAL] Không thể khởi tạo database: " + e.getMessage());
            System.exit(1);
        }

        // 2. Mở màn hình đăng nhập trên EDT (bắt buộc với Swing)
        SwingUtilities.invokeLater(() -> {
            // Đặt Look & Feel hệ thống để trông đẹp hơn
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) { /* Giữ nguyên L&F mặc định nếu lỗi */ }

            LoginFrame loginFrame = new LoginFrame();
            loginFrame.setVisible(true);
        });

        // 3. Khởi động ServerSocket trên thread riêng (không block EDT)
        Thread serverThread = new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(PORT)) {
                System.out.println("[INFO] Server đang KHỞI ĐỘNG VÀ LẮNG NGHE TẠI CỔNG: " + PORT);

                while (!Thread.currentThread().isInterrupted()) {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("[CONNECTED] Phát hiện kết nối mới từ IP: "
                            + clientSocket.getInetAddress());
                    // Giao cho thread pool xử lý
                    pool.execute(new ClientHandler(clientSocket));
                }

            } catch (IOException e) {
                if (!Thread.currentThread().isInterrupted()) {
                    System.err.println("[ERROR] Lỗi khởi động ServerSocket: " + e.getMessage());
                }
            }
        }, "ServerAcceptThread");

        serverThread.setDaemon(true); // Dừng khi UI đóng
        serverThread.start();

        // Đăng ký hook để đóng DB khi tắt ứng dụng
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[SHUTDOWN] Đang dọn dẹp tài nguyên...");
            pool.shutdownNow();
            DatabaseManager.close();
        }, "ShutdownHook"));
    }
}
