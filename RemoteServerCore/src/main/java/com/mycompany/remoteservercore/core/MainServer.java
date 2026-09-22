/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.mycompany.remoteservercore.core;

/**
 *
 * @author admin
 */
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
public class MainServer {
    private static final int PORT = 9999;
    private static final ExecutorService pool = Executors.newFixedThreadPool(50);

    public static void main(String[] args) {
        System.out.println("=== HE THONG SERVER QUAN LY PHONG BAN ===");
        
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("[INFO] Server đang KHOI DONG VA LANG NGHE TAI CONG:: " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("[CONNECTED] Phat Hien Ket Noi Moi Tu IP: " + clientSocket.getInetAddress());
                pool.execute(new ClientHandler(clientSocket));
            }
            
        } catch (IOException e) {
            System.err.println("[ERROR] Loi khoi dong server Server Socket: " + e.getMessage());
        }
    }
}

class ClientHandler implements Runnable {
    private Socket socket;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            // Xử lý dữ liệu client tại đây
        } catch (Exception e) {
            System.err.println("[WARNING] Loi xu ly client: " + e.getMessage());
        } finally {
            try {
                socket.close();
                System.out.println("[DISCONNECTED] Da dong ket noi voi client.");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
