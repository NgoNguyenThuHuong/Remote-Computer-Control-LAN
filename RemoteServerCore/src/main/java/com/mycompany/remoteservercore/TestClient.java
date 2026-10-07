/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.mycompany.remoteservercore;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;

public class TestClient {
    public static void main(String[] args) {
        String serverIP = "127.0.0.1"; // Địa chỉ localhost (máy tính của bạn)
        int port = 9999; // Cổng mà Server đang lắng nghe
        
        try {
            System.out.println("Client dang dang thu ket noi Server...");
            Socket socket = new Socket(serverIP, port);
            System.out.println("Da ket noi thanh cong voi Server!");
            
            // Thread để lắng nghe tin nhắn từ Server
            Thread listenerThread = new Thread(() -> {
                try {
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    String line;
                    while ((line = in.readLine()) != null) {
                        System.out.println("[Client nhan] " + line);
                        if (line.startsWith("BLOCK_WEB:")) {
                            String[] domains = line.substring("BLOCK_WEB:".length()).split(",");
                            System.out.println("[Client] Dang ap dung cau hinh chan các website sau:");
                            for (String d : domains) {
                                if (!d.isEmpty()) {
                                    System.out.println(" - " + d);
                                }
                            }
                            System.out.println("[Client] Da ap dung thanh cong!");
                        }
                    }
                } catch (IOException e) {
                    System.out.println("Da ngat ket noi voi Server.");
                }
            });
            listenerThread.start();
            
            // Giữ kết nối trong 30 giây để Server kịp gửi cấu hình
            Thread.sleep(30000);
            socket.close();
            System.out.println("Da dong ket noi tu phia Client.");
            
        } catch (IOException | InterruptedException e) {
            System.err.println("Loi ket noi toi Server: " + e.getMessage());
        }
    }
}
