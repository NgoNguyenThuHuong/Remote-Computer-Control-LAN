package com.mycompany.remoteservercore;

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

/**
 * Client thử nghiệm (Agent Client) kết nối tới MainServer qua TCP Socket.
 * Thực thi các yêu cầu của Issue #33:
 *  1. Parse JSON gói tin nhận được từ Server.
 *  2. Kiểm tra command (LOCK, LOGOUT, RESTART, SHUTDOWN).
 *  3. Thực thi command hệ thống qua SystemControl.
 *  4. Phản hồi kết quả (ACK / ERROR) về cho Server.
 *  5. Xử lý lệnh không hợp lệ & Exception Handling.
 */
public class TestClient {

    public static void runClient(String clientName, String serverIP, int port, boolean safeMode) {
        // Cấu hình chế độ an toàn Safe Mode cho thử nghiệm
        SystemControl.setSafeMode(safeMode);

        try {
            System.out.println("[" + clientName + "] Đang kết nối tới Server " + serverIP + ":" + port + "...");
            Socket socket = new Socket(serverIP, port);
            System.out.println("[" + clientName + "] Kết nối thành công tới Server!");

            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

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
            writer.write(JsonUtils.toJson(sysInfoPacket));
            writer.newLine();
            writer.flush();

            // 2. Vòng lặp lắng nghe lệnh từ Server (Issue #33)
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                // 2.1. Parse JSON gói tin từ Server
                MessagePacket packet = JsonUtils.fromJson(line, MessagePacket.class);
                if (packet == null) {
                    System.err.println("[" + clientName + "] << Lỗi Parse JSON: " + line);
                    MessagePacket errPacket = MessagePacket.createError(clientName, "SERVER", "Lỗi định dạng JSON gói tin");
                    writer.write(JsonUtils.toJson(errPacket));
                    writer.newLine();
                    writer.flush();
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
                        writer.write(JsonUtils.toJson(errPacket));
                        writer.newLine();
                        writer.flush();
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
                        writer.write(JsonUtils.toJson(ackPacket));
                        writer.newLine();
                        writer.flush();

                    } catch (Exception e) {
                        System.err.println("[" + clientName + "] Lỗi ngoại lệ khi thực thi lệnh " + commandStr + ": " + e.getMessage());
                        MessagePacket errPacket = MessagePacket.createError(
                                clientName,
                                packet.getSender(),
                                "Lỗi thực thi lệnh " + commandStr + ": " + e.getMessage()
                        );
                        writer.write(JsonUtils.toJson(errPacket));
                        writer.newLine();
                        writer.flush();
                    }

                } else if (MessagePacket.TYPE_ACK.equalsIgnoreCase(packet.getType())) {
                    System.out.println("[" + clientName + "] ACK từ Server: " + packet.getPayload());
                } else if (MessagePacket.TYPE_CHAT.equalsIgnoreCase(packet.getType())) {
                    System.out.println("[" + clientName + "] Tin nhắn Chat từ Server: " + packet.getPayload());
                }
            }

        } catch (IOException e) {
            System.err.println("[" + clientName + "] Mất kết nối tới Server: " + e.getMessage());
        } finally {
            System.out.println("[" + clientName + "] Đã dừng Client Agent.");
        }
    }

    public static void main(String[] args) {
        String serverIP = "127.0.0.1";
        int port = 9999;

        // Bật Safe Mode = true để thử nghiệm an toàn trên NetBeans (không thực sự tắt máy khi test)
        boolean safeMode = true;

        System.out.println("=== BẮT ĐẦU CHẠY CLIENT AGENT (KIỂM THỬ ISSUE #33) ===");
        System.out.println("[CHÚ Ý] Safe Mode đang BẬT. Các lệnh LOCK, RESTART, LOGOUT sẽ được giả lập thực thi an toàn.");

        runClient("CLIENT-MAY-01", serverIP, port, safeMode);
    }
}
