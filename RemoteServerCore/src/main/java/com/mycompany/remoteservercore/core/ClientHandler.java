package com.mycompany.remoteservercore.core;

import com.mycompany.remoteservercore.model.ClientInfo;

import java.io.IOException;
import java.net.Socket;

/**
 * Xử lý một client kết nối vào server.
 * Mỗi kết nối được chạy trong thread riêng từ thread pool (xem MainServer).
 *
 * <p>Khi kết nối:
 * <ol>
 *   <li>Đăng ký client vào {@link ClientManager} → Dashboard tự cập nhật.</li>
 *   <li>Giữ kết nối, xử lý packet (TODO: tích hợp PacketRouter - Issue #30).</li>
 *   <li>Khi ngắt: đánh dấu client offline trong ClientManager.</li>
 * </ol>
 *
 * TODO (Issue #30 - Hợp): Tích hợp heartbeat, AgentCore, PacketRouter vào đây.
 */
public class ClientHandler implements Runnable {

    private final Socket socket;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        // Lấy IP của client làm unique ID tạm thời
        String ip       = socket.getInetAddress().getHostAddress();
        int    port     = socket.getPort();
        String clientId = ip + ":" + port;

        // Lấy hostname (có thể blocking, giới hạn timeout)
        String hostname;
        try {
            hostname = socket.getInetAddress().getHostName();
        } catch (Exception e) {
            hostname = ip; // fallback nếu không phân giải được
        }

        System.out.println("[ClientHandler] Client kết nối: " + hostname + " (" + ip + ")");

        // Đăng ký client vào ClientManager → Dashboard sẽ nhận sự kiện onClientConnected
        ClientInfo clientInfo = ClientManager.getInstance().addClient(clientId, hostname, ip);

        try {
            // TODO: Tích hợp PacketRouter và AgentCore (Issue #30 - Hợp)
            // Ví dụ giả lập: giữ kết nối, định kỳ cập nhật heartbeat
            while (!socket.isClosed() && socket.isConnected()) {
                // Mô phỏng đọc dữ liệu (sẽ thay bằng protocol thực)
                int data = socket.getInputStream().read();
                if (data == -1) break; // Client đóng kết nối

                // TODO: Xử lý packet theo protocol của Hợp
                // Nếu là heartbeat packet → cập nhật heartbeat
                ClientManager.getInstance().updateHeartbeat(clientId);
            }

        } catch (IOException e) {
            // IOException thường xảy ra khi client ngắt đột ngột — không log lỗi
            System.out.println("[ClientHandler] Client " + hostname + " đã ngắt kết nối.");
        } finally {
            // Đánh dấu client offline trong ClientManager → Dashboard cập nhật
            ClientManager.getInstance().removeClient(clientId);
            try {
                socket.close();
            } catch (IOException ignored) {}
            System.out.println("[ClientHandler] Đã đóng kết nối với: " + clientId);
        }
    }
}
