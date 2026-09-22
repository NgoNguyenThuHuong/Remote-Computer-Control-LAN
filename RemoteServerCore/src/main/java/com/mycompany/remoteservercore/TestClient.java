/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.mycompany.remoteservercore;

/**
 *
 * @author admin
 */
import java.io.IOException;
import java.net.Socket;
public class TestClient {
    public static void main(String[] args) {
        String serverIP = "127.0.0.1"; // Địa chỉ localhost (máy tính của bạn)
        int port = 9999; // Cổng mà Server đang lắng nghe
        
        try {
            System.out.println("Client đang dang thu ket noi Server...");
            Socket socket = new Socket(serverIP, port);
            System.out.println("Da ket noi thanh cong voi Server!");
            
            // Giữ kết nối trong 3 giây để Server kịp ghi nhận, sau đó tự đóng
            Thread.sleep(3000);
            socket.close();
            System.out.println("Da dong ket noi tu phia Client.");
            
        } catch (IOException | InterruptedException e) {
            System.err.println("Loi ket noi toi Server: " + e.getMessage());
        }
    }
}
