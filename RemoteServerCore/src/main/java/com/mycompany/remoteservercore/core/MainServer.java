package com.mycompany.remoteservercore.core;

import com.mycompany.remoteservercore.model.ClientInfo;
import com.mycompany.remoteservercore.model.MessagePacket;
import com.mycompany.remoteservercore.protocol.JsonUtils;
import com.mycompany.remoteservercore.protocol.PacketRouter;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Server chính quản lý lắng nghe kết nối mạng LAN từ các Client Agent.
 * Chạy ServerSocket đa luồng với ExecutorService Thread Pool và điều phối gói tin qua PacketRouter.
 */
public class MainServer {
    public static final int DEFAULT_PORT = 9999;
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
                System.out.println("[MainServer] Da cap nhat ClientInfo: " + info.getHostName() + " (" + info.getIpAddress() + ") - CPU: " + info.getCpuUsage() + "%, RAM: " + info.getFormattedRam());

                // Server gửi phản hồi JSON (ACK) lại cho Client
                if (sender != null) {
                    sender.sendPacket(MessagePacket.createAck("SERVER", sender.getClientIp(), "Server da tiep nhan SYS_INFO thanh cong"));
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

        // 3. Xử lý tin nhắn Chat từ Client
        router.registerHandler(MessagePacket.TYPE_CHAT, (packet, sender) -> {
            System.out.println("[MainServer - CHAT] Tu [" + packet.getSender() + " (" + (sender != null ? sender.getClientIp() : "") + ")]: " + packet.getPayload());

            // Server gửi phản hồi Chat JSON ngược lại cho Client
            if (sender != null) {
                sender.sendPacket(MessagePacket.createChat("SERVER", sender.getClientIp(), "Server da nhan tin: \"" + packet.getPayload() + "\""));
            }
        });
    }

    /**
     * Khởi động Server lắng nghe kết nối từ các máy nhân sự.
     */
    public void start() {
        System.out.println("=== HE THONG SERVER QUAN LY PHONG BAN ===");
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("[INFO] Server dang KHOI DONG VA LANG NGHE TAI CONG: " + port);

            while (running) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("[CONNECTED] Phat hien ket noi moi tu IP: " + clientSocket.getInetAddress().getHostAddress());
                pool.execute(new ClientHandler(clientSocket));
            }
        } catch (IOException e) {
            if (running) {
                System.err.println("[ERROR] Loi khoi dong server Server Socket: " + e.getMessage());
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
            System.err.println("[ERROR] Loi khi dong ServerSocket: " + e.getMessage());
        }
        pool.shutdown();
        System.out.println("[INFO] Server da dung hoat dong.");
    }

    public static void main(String[] args) {
        MainServer server = new MainServer();
        server.start();
    }
}
