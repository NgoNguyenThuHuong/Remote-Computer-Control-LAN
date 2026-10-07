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
        
        // Khởi tạo Database SQLite cho Log
        com.mycompany.remoteservercore.database.DatabaseManager.initializeDatabase();
        com.mycompany.remoteservercore.database.LogDAO.saveLog(new com.mycompany.remoteservercore.model.LogEntry("Server", "Login", "Server started on port " + PORT));
        
        // Hiển thị giao diện UI quản lý chặn Web trên một Thread riêng biệt
        java.awt.EventQueue.invokeLater(() -> {
            new com.mycompany.remoteservercore.ui.BlockedWebFrame().setVisible(true);
            new com.mycompany.remoteservercore.ui.LogFrame().setVisible(true);
        });
        
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("[INFO] Server dang KHOI DONG VA LANG NGHE TAI CONG:: " + PORT);

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
