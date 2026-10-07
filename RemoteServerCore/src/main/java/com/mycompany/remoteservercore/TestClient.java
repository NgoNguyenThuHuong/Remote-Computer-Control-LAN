package com.mycompany.remoteservercore;

import com.mycompany.remoteservercore.features.SystemControl;
import com.mycompany.remoteservercore.model.ClientInfo;
import com.mycompany.remoteservercore.model.MessagePacket;
import com.mycompany.remoteservercore.protocol.JsonUtils;
import com.mycompany.remoteservercore.ui.ClientChatFrame;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Client thử nghiệm (Agent Client) kết nối tới MainServer qua TCP Socket.
 * Thực thi các yêu cầu của Issue #33 & Issue #35 (Issue 14):
 *  1. Parse JSON gói tin nhận được từ Server.
 *  2. Kiểm tra command (LOCK, LOGOUT, RESTART, SHUTDOWN).
 *  3. Thực thi command hệ thống qua SystemControl.
 *  4. Phản hồi kết quả (ACK / ERROR) về cho Server.
 *  5. Chat 2 chiều Server ↔ Client (giao diện ClientChatFrame + xử lý mất kết nối).
 */
public class TestClient {

    public static void runClient(String clientName, String serverIP, int port, boolean safeMode) {
        runClient(clientName, serverIP, port, safeMode, true);
    }

    public static void runClient(String clientName, String serverIP, int port, boolean safeMode, boolean enableGui) {
        // Cấu hình chế độ an toàn Safe Mode cho thử nghiệm
        SystemControl.setSafeMode(safeMode);

        Socket socket = null;
        BufferedWriter writer = null;
        BufferedReader reader = null;
        ClientChatFrame[] chatFrameHolder = new ClientChatFrame[1];

        try {
<<<<<<< HEAD
            System.out.println("[" + clientName + "] Đang kết nối tới Server " + serverIP + ":" + port + "...");
            socket = new Socket(serverIP, port);
            System.out.println("[" + clientName + "] Kết nối thành công tới Server!");

            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

            final BufferedWriter outWriter = writer;

            // Khởi tạo giao diện Chat phía Client nếu có hỗ trợ đồ họa
            if (enableGui && !GraphicsEnvironment.isHeadless()) {
                SwingUtilities.invokeLater(() -> {
                    ClientChatFrame frame = new ClientChatFrame(clientName, message -> {
                        try {
                            MessagePacket chatPacket = MessagePacket.createChat(clientName, "SERVER", message);
                            synchronized (outWriter) {
                                outWriter.write(JsonUtils.toJson(chatPacket));
                                outWriter.newLine();
                                outWriter.flush();
                            }
                            return true;
                        } catch (IOException e) {
                            System.err.println("[" + clientName + "] Lỗi khi gửi tin nhắn chat: " + e.getMessage());
                            return false;
                        }
                    });
                    frame.setVisible(true);
                    chatFrameHolder[0] = frame;
                });
            }

            // 1. Gửi gói tin thông tin hệ thống Client (SYS_INFO)
            ClientInfo clientInfo = new ClientInfo();
            clientInfo.setIpAddress(socket.getLocalAddress().getHostAddress());
            clientInfo.setHostName(clientName);
            clientInfo.setOsName(System.getProperty("os.name"));
            clientInfo.setCurrentUser(System.getProperty("user.name"));
            clientInfo.setDepartment("Phòng Kiểm Thử");
            clientInfo.setCpuUsage(15.5);
            clientInfo.setRamUsage(48.2);
            clientInfo.setTotalRamMb(16384);
            clientInfo.setUsedRamMb(7890);

            MessagePacket sysInfoPacket = new MessagePacket(
                    MessagePacket.TYPE_SYS_INFO,
                    clientName,
                    "SERVER",
                    JsonUtils.toJson(clientInfo)
            );

            System.out.println("[" + clientName + "] >> Gửi SYS_INFO lên Server...");
            synchronized (outWriter) {
                outWriter.write(JsonUtils.toJson(sysInfoPacket));
                outWriter.newLine();
                outWriter.flush();
            }

            // 2. Vòng lặp lắng nghe lệnh và tin nhắn từ Server
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                if (line.startsWith("BLOCK_WEB:")) {
                    String[] domains = line.substring("BLOCK_WEB:".length()).split(",");
                    System.out.println("[" + clientName + "] Dang ap dung cau hinh chan cac website:");
                    for (String d : domains) {
                        if (!d.trim().isEmpty()) {
                            System.out.println(" - " + d.trim());
                        }
                    }
                    System.out.println("[" + clientName + "] Da ap dung thanh cong!");
                    continue;
                }

                // 2.1. Parse JSON gói tin từ Server
                MessagePacket packet = JsonUtils.fromJson(line, MessagePacket.class);
                if (packet == null) {
                    System.err.println("[" + clientName + "] << Lỗi Parse JSON: " + line);
                    MessagePacket errPacket = MessagePacket.createError(clientName, "SERVER", "Lỗi định dạng JSON gói tin");
                    synchronized (outWriter) {
                        outWriter.write(JsonUtils.toJson(errPacket));
                        outWriter.newLine();
                        outWriter.flush();
                    }
                    continue;
                }

                System.out.println("[" + clientName + "] << Nhận gói tin [" + packet.getType() + "] từ Server. Payload: " + packet.getPayload());

                // 2.2. Xử lý các loại gói tin
                if (MessagePacket.TYPE_COMMAND.equalsIgnoreCase(packet.getType())) {
                    String commandStr = packet.getPayload();
                    System.out.println("[" + clientName + "] >> Xử lý Command từ Server: " + commandStr);

                    // 2.3. Kiểm tra tính hợp lệ của Command
                    if (!SystemControl.isValidCommand(commandStr)) {
                        System.err.println("[" + clientName + "] Command không hợp lệ: " + commandStr);
                        MessagePacket errPacket = MessagePacket.createError(
                                clientName,
                                packet.getSender(),
                                "Lệnh không hợp lệ hoặc không được hỗ trợ: " + commandStr
                        );
                        synchronized (outWriter) {
                            outWriter.write(JsonUtils.toJson(errPacket));
                            outWriter.newLine();
                            outWriter.flush();
                        }
                        continue;
                    }

                    // 2.4. Thực thi Command & Xử lý Exception
                    try {
                        String resultMsg = SystemControl.execute(commandStr);
                        System.out.println("[" + clientName + "] " + resultMsg);

                        // 2.5. Trả kết quả thực thi về Server (ACK)
                        MessagePacket ackPacket = MessagePacket.createAck(
                                clientName,
                                packet.getSender(),
                                resultMsg
                        );
                        synchronized (outWriter) {
                            outWriter.write(JsonUtils.toJson(ackPacket));
                            outWriter.newLine();
                            outWriter.flush();
                        }

                    } catch (Exception e) {
                        System.err.println("[" + clientName + "] Lỗi ngoại lệ khi thực thi lệnh " + commandStr + ": " + e.getMessage());
                        MessagePacket errPacket = MessagePacket.createError(
                                clientName,
                                packet.getSender(),
                                "Lỗi thực thi lệnh " + commandStr + ": " + e.getMessage()
                        );
                        synchronized (outWriter) {
                            outWriter.write(JsonUtils.toJson(errPacket));
                            outWriter.newLine();
                            outWriter.flush();
                        }
                    }

                } else if (MessagePacket.TYPE_ACK.equalsIgnoreCase(packet.getType())) {
                    System.out.println("[" + clientName + "] ACK từ Server: " + packet.getPayload());

                } else if (MessagePacket.TYPE_CHAT.equalsIgnoreCase(packet.getType())) {
                    // Xử lý gói tin CHAT 2 chiều từ Server (Issue #35 / Issue 14)
                    System.out.println("[" + clientName + "] 💬 Tin nhắn Chat từ Server: " + packet.getPayload());
                    if (chatFrameHolder[0] != null) {
                        chatFrameHolder[0].onMessageReceived(packet.getSender(), packet.getPayload(), packet.getTimestamp());
                    }
                }
            }

        } catch (IOException e) {
            System.err.println("[" + clientName + "] Mất kết nối tới Server: " + e.getMessage());
            if (chatFrameHolder[0] != null) {
                chatFrameHolder[0].setConnected(false);
            }
        } finally {
            try {
                if (socket != null && !socket.isClosed()) socket.close();
            } catch (IOException ignored) {}
            System.out.println("[" + clientName + "] Đã dừng Client Agent.");
        }
    }

    public static void main(String[] args) {
        String serverIP = "127.0.0.1";
        int port = 9999;

        // Bật Safe Mode = true để thử nghiệm an toàn trên NetBeans (không thực sự tắt máy khi test)
        boolean safeMode = true;

        System.out.println("=== BẮT ĐẦU CHẠY CLIENT AGENT (KIỂM THỬ ISSUE #33 & ISSUE #35) ===");
        System.out.println("[CHÚ Ý] Safe Mode đang BẬT. Cửa sổ Chat nội bộ phía Client đang khởi động...");

        runClient("CLIENT-MAY-01", serverIP, port, safeMode, true);
    }
}
