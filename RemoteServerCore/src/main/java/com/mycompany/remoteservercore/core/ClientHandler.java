package com.mycompany.remoteservercore.core;

import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private Socket socket;
    private PrintWriter out;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    public void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }

    @Override
    public void run() {
        String clientIp = socket.getInetAddress().toString();
        try {
            out = new PrintWriter(socket.getOutputStream(), true);
            ClientManager.addClient(this);
            com.mycompany.remoteservercore.database.LogDAO.saveLog(new com.mycompany.remoteservercore.model.LogEntry(clientIp, "Client Connect", "Client connected to server"));
            
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                System.out.println("[Client] " + inputLine);
                com.mycompany.remoteservercore.database.LogDAO.saveLog(new com.mycompany.remoteservercore.model.LogEntry(clientIp, "Command Result", inputLine));
            }
        } catch (Exception e) {
            System.err.println("[WARNING] Loi xu ly client: " + e.getMessage());
            com.mycompany.remoteservercore.database.LogDAO.saveLog(new com.mycompany.remoteservercore.model.LogEntry(clientIp, "Error", e.getMessage()));
        } finally {
            ClientManager.removeClient(this);
            try {
                socket.close();
                System.out.println("[DISCONNECTED] Da dong ket noi voi client.");
                com.mycompany.remoteservercore.database.LogDAO.saveLog(new com.mycompany.remoteservercore.model.LogEntry(clientIp, "Client Disconnect", "Client disconnected from server"));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
