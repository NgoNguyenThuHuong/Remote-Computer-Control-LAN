package com.mycompany.remoteservercore;

import com.mycompany.remoteservercore.model.ClientInfo;
import com.mycompany.remoteservercore.model.MessagePacket;
import com.mycompany.remoteservercore.protocol.JsonUtils;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Client thử nghiệm kết nối và truyền nhận gói tin JSON hai chiều với Server qua TCP Socket.
 */
public class TestClient {

    public static void runClient(String clientName, String serverIP, int port) {
        try {
            System.out.println("[" + clientName + "] Dang ket noi toi Server " + serverIP + ":" + port + "...");
            Socket socket = new Socket(serverIP, port);
            System.out.println("[" + clientName + "] Ket noi thanh cong!");

            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

            // 1. Tạo và gửi gói tin SYS_INFO (ClientInfo) dạng JSON
            ClientInfo clientInfo = new ClientInfo();
            clientInfo.setIpAddress(socket.getLocalAddress().getHostAddress());
            clientInfo.setHostName(clientName);
            clientInfo.setOsName(System.getProperty("os.name"));
            clientInfo.setCurrentUser("User-" + clientName);
            clientInfo.setDepartment("Phong Kiem Thu");
            clientInfo.setCpuUsage(18.2);
            clientInfo.setRamUsage(52.0);
            clientInfo.setTotalRamMb(16384);
            clientInfo.setUsedRamMb(8519);

            MessagePacket sysInfoPacket = new MessagePacket(
                    MessagePacket.TYPE_SYS_INFO,
                    clientName,
                    "SERVER",
                    JsonUtils.toJson(clientInfo)
            );

            System.out.println("[" + clientName + "] >> Gui SYS_INFO len Server...");
            writer.write(JsonUtils.toJson(sysInfoPacket));
            writer.newLine();
            writer.flush();

            // Đọc phản hồi JSON từ Server
            String ackResponse = reader.readLine();
            System.out.println("[" + clientName + "] << Nhan phan hoi tu Server: " + ackResponse);

            Thread.sleep(500);

            // 2. Gửi một gói tin Chat mẫu dạng JSON
            MessagePacket chatPacket = MessagePacket.createChat(
                    clientName,
                    "SERVER",
                    "Xin chao Server tu " + clientName
            );
            System.out.println("[" + clientName + "] >> Gui tin nhan Chat len Server...");
            writer.write(JsonUtils.toJson(chatPacket));
            writer.newLine();
            writer.flush();

            // Đọc phản hồi Chat từ Server
            String chatResponse = reader.readLine();
            System.out.println("[" + clientName + "] << Nhan phan hoi Chat tu Server: " + chatResponse);

            // Duy trì kết nối 2 giây rồi đóng an toàn (test disconnect)
            Thread.sleep(2000);
            socket.close();
            System.out.println("[" + clientName + "] Da ngat ket noi (disconnect) an toan.");

        } catch (IOException | InterruptedException e) {
            System.err.println("[" + clientName + "] Ngoai le: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        String serverIP = "127.0.0.1";
        int port = 9999;

        // Kịch bản kiểm thử: Khởi chạy đồng thời 2 Client để kiểm tra nhiều Client kết nối cùng lúc
        System.out.println("=== BAT DAU KIEM THU CLIENT-SERVER (1 HOAC NHIEU CLIENT) ===");

        Thread client1 = new Thread(() -> runClient("CLIENT-MAY-01", serverIP, port));
        Thread client2 = new Thread(() -> runClient("CLIENT-MAY-02", serverIP, port));

        client1.start();
        try {
            Thread.sleep(300); // Khởi chạy lệch một chút
        } catch (InterruptedException ignored) {
        }
        client2.start();

        try {
            client1.join();
            client2.join();
        } catch (InterruptedException ignored) {
        }

        System.out.println("=== KET THUC KIEM THU TAT CA CLIENT ===");
    }
}
