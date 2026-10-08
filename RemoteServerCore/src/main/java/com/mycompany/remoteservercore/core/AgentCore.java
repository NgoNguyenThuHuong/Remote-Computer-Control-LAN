package com.mycompany.remoteservercore.core;

import com.mycompany.remoteservercore.features.SystemControl;
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
import java.util.Timer;
import java.util.TimerTask;

/**
 * Lớp lõi xử lý phía Client Agent (AgentCore).
 * Cung cấp vòng đời kết nối TCP, gửi nhận gói tin hệ thống, chat 2 chiều,
 * thực thi lệnh điều khiển từ xa và định kỳ gửi heartbeat duy trì kết nối.
 */
public class AgentCore {

    public interface ChatListener {
        void onMessageReceived(String sender, String message, long timestamp);
        void onDisconnected();
    }

    public interface CommandListener {
        void onCommandReceived(String command, boolean success, String result);
    }

    public interface PacketListener {
        void onPacketReceived(MessagePacket packet);
    }

    private final String serverIp;
    private final int serverPort;
    private final String clientName;
    private final String virtualIp;
    private Socket socket;
    private BufferedReader reader;
    private BufferedWriter writer;
    private volatile boolean running = false;
    private ChatListener chatListener;
    private CommandListener commandListener;
    private PacketListener packetListener;
    private Timer heartbeatTimer;

    public AgentCore(String serverIp, int serverPort, String clientName) {
        this(serverIp, serverPort, clientName, null);
    }

    public AgentCore(String serverIp, int serverPort, String clientName, String virtualIp) {
        this.serverIp = serverIp;
        this.serverPort = serverPort;
        this.clientName = clientName;
        this.virtualIp = virtualIp;
    }

    public synchronized void connect() throws IOException {
        socket = new Socket(serverIp, serverPort);
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        running = true;

        // Gửi thông tin SYS_INFO khởi tạo
        String ipToSend = (virtualIp != null && !virtualIp.trim().isEmpty())
                ? virtualIp
                : socket.getLocalAddress().getHostAddress();
        ClientInfo info = new ClientInfo(ipToSend, clientName, "User", System.getProperty("os.name"));
        info.setDepartment("Test LAN Dept");
        info.setCpuUsage(12.5);
        info.setRamUsage(45.0);
        info.setTotalRamMb(16384);
        info.setUsedRamMb(7372);

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
                    if (packetListener != null) {
                        try {
                            packetListener.onPacketReceived(packet);
                        } catch (Exception ignored) {}
                    }

                    if (MessagePacket.TYPE_CHAT.equalsIgnoreCase(packet.getType())) {
                        if (chatListener != null) {
                            chatListener.onMessageReceived(packet.getSender(), packet.getPayload(), packet.getTimestamp());
                        }
                    } else if (MessagePacket.TYPE_COMMAND.equalsIgnoreCase(packet.getType())) {
                        handleCommand(packet);
                    } else if (MessagePacket.TYPE_SCREENSHOT_REQ.equalsIgnoreCase(packet.getType())) {
                        handleScreenshotRequest(packet);
                    }
                }
            }
        } catch (IOException e) {
            if (running) {
                System.err.println("[AgentCore] Mất kết nối tới Server: " + e.getMessage());
            }
        } finally {
            disconnect();
            if (chatListener != null) {
                chatListener.onDisconnected();
            }
        }
    }

    private void handleCommand(MessagePacket packet) {
        String commandStr = packet.getPayload();
        if (!SystemControl.isValidCommand(commandStr)) {
            String errDetail = "Lệnh không hợp lệ: " + commandStr;
            MessagePacket errPacket = MessagePacket.createError(clientName, "SERVER", errDetail);
            sendPacket(errPacket);
            if (commandListener != null) {
                commandListener.onCommandReceived(commandStr, false, errDetail);
            }
        } else {
            try {
                String result = SystemControl.execute(commandStr);
                MessagePacket ackPacket = MessagePacket.createAck(clientName, "SERVER", result);
                sendPacket(ackPacket);
                if (commandListener != null) {
                    commandListener.onCommandReceived(commandStr, true, result);
                }
            } catch (Exception e) {
                MessagePacket errPacket = MessagePacket.createError(clientName, "SERVER", "Lỗi thực thi: " + e.getMessage());
                sendPacket(errPacket);
                if (commandListener != null) {
                    commandListener.onCommandReceived(commandStr, false, e.getMessage());
                }
            }
        }
    }

    private void handleScreenshotRequest(MessagePacket packet) {
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

    public boolean sendHeartbeat() {
        MessagePacket packet = MessagePacket.createHeartbeat(clientName, "PING");
        return sendPacket(packet);
    }

    public synchronized void startHeartbeatTimer(long intervalMs) {
        stopHeartbeatTimer();
        heartbeatTimer = new Timer("HeartbeatTimer-" + clientName, true);
        heartbeatTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (isConnected()) {
                    sendHeartbeat();
                }
            }
        }, intervalMs, intervalMs);
    }

    public synchronized void stopHeartbeatTimer() {
        if (heartbeatTimer != null) {
            heartbeatTimer.cancel();
            heartbeatTimer = null;
        }
    }

    public synchronized void reconnect() throws IOException {
        disconnect();
        try {
            Thread.sleep(200);
        } catch (InterruptedException ignored) {}
        connect();
    }

    public void disconnect() {
        if (!running) {
            return;
        }
        running = false;
        stopHeartbeatTimer();
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
        try {
            if (reader != null) reader.close();
        } catch (IOException ignored) {}
        try {
            if (writer != null) writer.close();
        } catch (IOException ignored) {}
    }

    public void setChatListener(ChatListener chatListener) {
        this.chatListener = chatListener;
    }

    public void setCommandListener(CommandListener commandListener) {
        this.commandListener = commandListener;
    }

    public void setPacketListener(PacketListener packetListener) {
        this.packetListener = packetListener;
    }

    public boolean isConnected() {
        return running && socket != null && !socket.isClosed();
    }

    public String getClientName() {
        return clientName;
    }

    public String getVirtualIp() {
        return virtualIp;
    }
}
