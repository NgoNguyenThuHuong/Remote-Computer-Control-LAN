package com.mycompany.remoteservercore.core;

import com.mycompany.remoteservercore.database.LogDAO;
import com.mycompany.remoteservercore.model.LogEntry;
import com.mycompany.remoteservercore.model.MessagePacket;
import com.mycompany.remoteservercore.protocol.JsonUtils;
import com.mycompany.remoteservercore.protocol.PacketRouter;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Luồng xử lý độc lập cho từng Agent/Client kết nối tới Server.
 * Chịu trách nhiệm nhận và gửi các gói tin JSON hoặc chuỗi lệnh raw qua TCP Socket,
 * đồng thời ghi nhận lịch sử vào Activity Log (LogDAO).
 */
public class ClientHandler implements Runnable {
    private final Socket socket;
    private final String clientIp;
    private BufferedReader reader;
    private BufferedWriter writer;
    private volatile boolean running = true;
    private PacketRouter packetRouter;

    public ClientHandler(Socket socket) {
        this.socket = socket;
        this.clientIp = (socket.getInetAddress() != null) ? socket.getInetAddress().getHostAddress() : "UNKNOWN";
        this.packetRouter = PacketRouter.getInstance();
    }

    public ClientHandler(Socket socket, PacketRouter packetRouter) {
        this.socket = socket;
        this.clientIp = (socket.getInetAddress() != null) ? socket.getInetAddress().getHostAddress() : "UNKNOWN";
        this.packetRouter = packetRouter;
    }

    @Override
    public void run() {
        try {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

            System.out.println("[ClientHandler] Bat dau phuc vu Client IP: " + clientIp);
            ClientManager.getInstance().registerClient(this);

            try {
                LogDAO.saveLog(new LogEntry(clientIp, "Client Connect", "Client connected to server"));
            } catch (Exception e) {
                System.err.println("[ClientHandler] Log error: " + e.getMessage());
            }

            String line;
            while (running && (line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                // Parse gói tin JSON sang MessagePacket
                MessagePacket packet = JsonUtils.fromJson(line, MessagePacket.class);
                if (packet != null) {
                    if (packetRouter != null) {
                        packetRouter.route(packet, this);
                    } else {
                        System.out.println("[ClientHandler] Nhan goi tin tu " + clientIp + ": " + packet.getType());
                    }
                    try {
                        LogDAO.saveLog(new LogEntry(clientIp, packet.getType(), packet.getPayload()));
                    } catch (Exception ignored) {}
                } else {
                    // Nếu nhận chuỗi raw text thông thường
                    System.out.println("[ClientHandler] Nhận dữ liệu text từ " + clientIp + ": " + line);
                    try {
                        LogDAO.saveLog(new LogEntry(clientIp, "Command Result", line));
                    } catch (Exception ignored) {}
                }
            }
        } catch (IOException e) {
            if (running) {
                System.err.println("[ClientHandler] Mat ket noi voi Client " + clientIp + ": " + e.getMessage());
                try {
                    LogDAO.saveLog(new LogEntry(clientIp, "Error", e.getMessage()));
                } catch (Exception ignored) {}
            }
        } finally {
            try {
                LogDAO.saveLog(new LogEntry(clientIp, "Client Disconnect", "Client disconnected from server"));
            } catch (Exception ignored) {}
            close();
        }
    }

    /**
     * Gửi chuỗi tin nhắn thông thường (raw string) tới Client.
     * Hỗ trợ cho các tính năng broadcast cấu hình như chặn web.
     */
    public synchronized boolean sendMessage(String message) {
        if (!running || socket.isClosed() || writer == null) {
            return false;
        }
        try {
            writer.write(message);
            writer.newLine();
            writer.flush();
            return true;
        } catch (IOException e) {
            System.err.println("[ClientHandler] Loi khi gui text toi " + clientIp + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Gửi một MessagePacket tới Client qua Socket dưới định dạng JSON.
     *
     * @param packet Gói tin cần gửi
     * @return true nếu gửi thành công, false nếu có lỗi
     */
    public synchronized boolean sendPacket(MessagePacket packet) {
        if (!running || socket.isClosed() || writer == null) {
            return false;
        }
        try {
            String json = JsonUtils.toJson(packet);
            writer.write(json);
            writer.newLine();
            writer.flush();
            return true;
        } catch (IOException e) {
            System.err.println("[ClientHandler] Loi khi gui goi tin toi " + clientIp + ": " + e.getMessage());
            close();
            return false;
        }
    }

    /**
     * Đóng kết nối an toàn với Client.
     */
    public void close() {
        if (!running && socket.isClosed()) {
            return;
        }
        running = false;
        ClientManager.getInstance().unregisterClient(this);
        try {
            if (reader != null) {
                reader.close();
            }
        } catch (IOException ignored) {}
        try {
            if (writer != null) {
                writer.close();
            }
        } catch (IOException ignored) {}
        try {
            if (!socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {}
        System.out.println("[ClientHandler] Da dong ket noi an toan voi Client " + clientIp);
    }

    public Socket getSocket() {
        return socket;
    }

    public String getClientIp() {
        return clientIp;
    }

    public boolean isRunning() {
        return running && !socket.isClosed();
    }

    public void setPacketRouter(PacketRouter packetRouter) {
        this.packetRouter = packetRouter;
    }
}
