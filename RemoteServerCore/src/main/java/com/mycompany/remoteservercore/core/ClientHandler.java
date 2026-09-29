package com.mycompany.remoteservercore.core;

import java.io.IOException;
import java.net.Socket;

/**
 * Xử lý một client kết nối vào server.
 * Mỗi kết nối sẽ được chạy trong một thread riêng từ thread pool.
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
        System.out.println("[ClientHandler] Đang xử lý client: "
                + socket.getInetAddress().getHostAddress());
        try {
            // TODO: Tích hợp PacketRouter và AgentCore (Issue #30)
            // Hiện tại giữ kết nối mở cho đến khi client ngắt

        } catch (Exception e) {
            System.err.println("[ClientHandler] Lỗi xử lý client: " + e.getMessage());
        } finally {
            try {
                socket.close();
                System.out.println("[ClientHandler] Đã đóng kết nối với client: "
                        + socket.getInetAddress().getHostAddress());
            } catch (IOException e) {
                System.err.println("[ClientHandler] Lỗi đóng socket: " + e.getMessage());
            }
        }
    }
}
