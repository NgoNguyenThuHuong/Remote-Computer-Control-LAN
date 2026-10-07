package com.mycompany.remoteservercore.core;

import com.mycompany.remoteservercore.database.DatabaseManager;
import com.mycompany.remoteservercore.database.LogDAO;
import com.mycompany.remoteservercore.model.ClientInfo;
import com.mycompany.remoteservercore.model.LogEntry;
import com.mycompany.remoteservercore.model.MessagePacket;
import com.mycompany.remoteservercore.protocol.JsonUtils;
import com.mycompany.remoteservercore.protocol.PacketRouter;
import com.mycompany.remoteservercore.ui.LoginFrame;

import javax.swing.*;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Entry point của Server.
 * Luồng khởi động:
 *   1. Khởi tạo DB (tạo bảng users + logs, seed tài khoản mặc định admin/user).
 *   2. Đăng ký các PacketHandler điều hướng gói tin mạng.
 *   3. Mở LoginFrame trên EDT (Event Dispatch Thread của Swing).
 *   4. ServerSocket chạy trên thread riêng — lắng nghe kết nối từ các máy Client.
 */
public class MainServer {

    public static final int DEFAULT_PORT = 9999;
    public static final int PORT = DEFAULT_PORT;

    private final int port;
    private final ExecutorService pool;
    private volatile boolean running = true;
    private ServerSocket serverSocket;

    public MainServer() {
        this(DEFAULT_PORT);
    }

    public MainServer(int port) {
        this.port = port;
        this.pool = Executors.newFixedThreadPool(50);
        initDefaultHandlers();
    }

    /**
     * Khởi tạo các bộ xử lý gói tin mặc định cho Server.
     */
    private void initDefaultHandlers() {
        PacketRouter router = PacketRouter.getInstance();

        // 1. Xử lý gói tin thông tin hệ thống Client (SYS_INFO)
        router.registerHandler(MessagePacket.TYPE_SYS_INFO, (packet, sender) -> {
            ClientInfo info = JsonUtils.fromJson(packet.getPayload(), ClientInfo.class);
            if (info != null) {
                if (sender != null) {
                    info.setIpAddress(sender.getClientIp());
                }
                ClientManager.getInstance().updateClientInfo(info);
                System.out.println("[MainServer] Đã cập nhật ClientInfo: " + info.getHostName() + " (" + info.getIpAddress() + ") - CPU: " + info.getCpuUsage() + "%, RAM: " + info.getFormattedRam());

                // Server gửi phản hồi JSON (ACK) lại cho Client
                if (sender != null) {
                    sender.sendPacket(MessagePacket.createAck("SERVER", sender.getClientIp(), "Server đã tiếp nhận SYS_INFO thành công"));
                }
            }
        });

        // 2. Xử lý gói tin Heartbeat để duy trì trạng thái online
        router.registerHandler(MessagePacket.TYPE_HEARTBEAT, (packet, sender) -> {
            if (sender != null) {
                ClientInfo existing = ClientManager.getInstance().getClientInfo(sender.getClientIp());
                if (existing != null) {
                    existing.updatePing();
                }
                sender.sendPacket(MessagePacket.createAck("SERVER", sender.getClientIp(), "PONG"));
            }
        });

        // 3. Xử lý tin nhắn Chat 2 chiều từ Client (Issue #35 / Issue 14)
        router.registerHandler(MessagePacket.TYPE_CHAT, (packet, sender) -> {
            String clientIp = (sender != null) ? sender.getClientIp() : packet.getSender();
            System.out.println("[MainServer - CHAT] Từ [" + packet.getSender() + " (" + clientIp + ")]: " + packet.getPayload());

            if (packet.getSender() == null || packet.getSender().trim().isEmpty()) {
                packet.setSender(clientIp);
            }

            // Chuyển tiếp tin nhắn cho các listener (ChatFrame, DashboardFrame)
            ClientManager.getInstance().notifyChatMessageReceived(packet);
        });

        // 4. Xử lý phản hồi lệnh thành công (ACK) từ Client (Issue #34)
        router.registerHandler(MessagePacket.TYPE_ACK, (packet, sender) -> {
            String ip = (sender != null) ? sender.getClientIp() : packet.getSender();
            System.out.println("[MainServer - ACK] Nhận phản hồi thành công từ [" + ip + "]: " + packet.getPayload());
            ClientManager.getInstance().notifyCommandResponse(ip, MessagePacket.TYPE_ACK, packet.getPayload());
        });

        // 5. Xử lý phản hồi lệnh lỗi (ERROR) từ Client (Issue #34)
        router.registerHandler(MessagePacket.TYPE_ERROR, (packet, sender) -> {
            String ip = (sender != null) ? sender.getClientIp() : packet.getSender();
            System.err.println("[MainServer - ERROR] Nhận báo lỗi từ [" + ip + "]: " + packet.getPayload());
            ClientManager.getInstance().notifyCommandResponse(ip, MessagePacket.TYPE_ERROR, packet.getPayload());
        });

        // 6. Xử lý phản hồi ảnh chụp màn hình từ Client (Issue 17 / Issue #38)
        router.registerHandler(MessagePacket.TYPE_SCREENSHOT_RES, (packet, sender) -> {
            String ip = (sender != null) ? sender.getClientIp() : packet.getSender();
            System.out.println("[MainServer - SCREENSHOT] Nhận ảnh màn hình từ Client [" + ip + "], payload length: "
                    + (packet.getPayload() != null ? packet.getPayload().length() : 0));
            ClientManager.getInstance().notifyScreenshotReceived(ip, packet.getPayload());
        });
    }

    /**
     * Khởi động ServerSocket lắng nghe kết nối từ các máy nhân sự.
     */
    public void start() {
        System.out.println("=== HỆ THỐNG SERVER QUẢN LÝ PHÒNG BAN ===");

        // 1. Khởi tạo database: tạo bảng users, logs + seed tài khoản mặc định
        try {
            DatabaseManager.initialize();
            LogDAO.saveLog(new LogEntry("Server", "Login", "Server started on port " + port));
        } catch (RuntimeException e) {
            System.err.println("[FATAL] Không thể khởi tạo database: " + e.getMessage());
        }

        // 2. Mở màn hình đăng nhập trên EDT (bắt buộc với Swing)
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}

            LoginFrame loginFrame = new LoginFrame();
            loginFrame.setVisible(true);
        });

        // 3. Khởi động ServerSocket lắng nghe kết nối
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("[INFO] Server đang KHỞI ĐỘNG VÀ LẮNG NGHE TẠI CỔNG: " + port);

            while (running) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("[CONNECTED] Phát hiện kết nối mới từ IP: " + clientSocket.getInetAddress().getHostAddress());
                pool.execute(new ClientHandler(clientSocket));
            }
        } catch (IOException e) {
            if (running) {
                System.err.println("[ERROR] Lỗi khởi động ServerSocket: " + e.getMessage());
            }
        } finally {
            stop();
        }
    }

    /**
     * Dừng Server an toàn và giải phóng tài nguyên.
     */
    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("[ERROR] Lỗi khi đóng ServerSocket: " + e.getMessage());
        }
        pool.shutdown();
        DatabaseManager.close();
        System.out.println("[INFO] Server đã dừng hoạt động.");
    }

    public static void main(String[] args) {
        MainServer server = new MainServer();

        // Shutdown hook giải phóng tài nguyên khi tắt JVM
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "ShutdownHook"));

        server.start();
    }
}
