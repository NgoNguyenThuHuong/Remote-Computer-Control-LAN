package com.mycompany.remoteservercore.core;

import com.mycompany.remoteservercore.model.ClientInfo;
import com.mycompany.remoteservercore.model.MessagePacket;
import com.mycompany.remoteservercore.protocol.JsonUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Lớp lõi xử lý phía Client Agent (AgentCore).
 * Cung cấp vòng đời kết nối TCP, gửi nhận gói tin hệ thống và chat 2 chiều.
 */
public class AgentCore {

    public interface ChatListener {
        void onMessageReceived(String sender, String message, long timestamp);
        void onDisconnected();
    }

    private final String serverIp;
    private final int serverPort;
    private final String clientName;
    private Socket socket;
    private BufferedReader reader;
    private BufferedWriter writer;
    private volatile boolean running = false;
    private ChatListener chatListener;

    public AgentCore(String serverIp, int serverPort, String clientName) {
        this.serverIp = serverIp;
        this.serverPort = serverPort;
        this.clientName = clientName;
    }

    public synchronized void connect() throws IOException {
        socket = new Socket(serverIp, serverPort);
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        running = true;

        // Gửi thông tin SYS_INFO khởi tạo
        ClientInfo info = new ClientInfo(socket.getLocalAddress().getHostAddress(), clientName, "User", System.getProperty("os.name"));
        MessagePacket packet = new MessagePacket(MessagePacket.TYPE_SYS_INFO, clientName, "SERVER", JsonUtils.toJson(info));
        sendPacket(packet);

        // Khởi chạy luồng đọc dữ liệu từ Server
        Thread readerThread = new Thread(this::listenLoop, "AgentCore-Reader-" + clientName);
        readerThread.setDaemon(true);
        readerThread.start();
    }

    private void listenLoop() {
        try {
            String line;
            while (running && (line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                MessagePacket packet = JsonUtils.fromJson(line, MessagePacket.class);
                if (packet != null) {
                    if (MessagePacket.TYPE_CHAT.equalsIgnoreCase(packet.getType())) {
                        if (chatListener != null) {
                            chatListener.onMessageReceived(packet.getSender(), packet.getPayload(), packet.getTimestamp());
                        }
                    } else if (MessagePacket.TYPE_SCREENSHOT_REQ.equalsIgnoreCase(packet.getType())) {
                        try {
                            String base64Img = com.mycompany.remoteservercore.features.ScreenCapturer.captureScreenAsBase64();
                            MessagePacket resPacket = MessagePacket.createScreenshotResponse(clientName, base64Img);
                            sendPacket(resPacket);
                        } catch (Exception e) {
                            System.err.println("[AgentCore] Lỗi khi chụp màn hình: " + e.getMessage());
                            MessagePacket errPacket = MessagePacket.createError(clientName, "SERVER", "Lỗi chụp màn hình: " + e.getMessage());
                            sendPacket(errPacket);
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("[AgentCore] Mất kết nối tới Server: " + e.getMessage());
        } finally {
            disconnect();
            if (chatListener != null) {
                chatListener.onDisconnected();
            }
        }
    }

    public synchronized boolean sendPacket(MessagePacket packet) {
        if (!running || writer == null || socket.isClosed()) {
            return false;
        }
        try {
            writer.write(JsonUtils.toJson(packet));
            writer.newLine();
            writer.flush();
            return true;
        } catch (IOException e) {
            System.err.println("[AgentCore] Lỗi khi gửi gói tin: " + e.getMessage());
            return false;
        }
    }

    public boolean sendChatMessage(String message) {
        MessagePacket packet = MessagePacket.createChat(clientName, "SERVER", message);
        return sendPacket(packet);
    }

    public synchronized void disconnect() {
        running = false;
        try {
            if (reader != null) reader.close();
        } catch (IOException ignored) {}
        try {
            if (writer != null) writer.close();
        } catch (IOException ignored) {}
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
    }

    public void setChatListener(ChatListener chatListener) {
        this.chatListener = chatListener;
    }

    public boolean isConnected() {
        return running && socket != null && !socket.isClosed();
    }

    public String getClientName() {
        return clientName;
    }
}
