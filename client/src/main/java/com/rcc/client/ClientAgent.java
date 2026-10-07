package com.rcc.client;

import com.rcc.protocol.JsonProtocol;
import com.rcc.protocol.Message;
import com.rcc.protocol.MessageType;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class ClientAgent {
    private String serverIp;
    private int port;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private Timer heartbeatTimer;
    private boolean isRunning = false;
    
    public ClientAgent(String serverIp, int port) {
        this.serverIp = serverIp;
        this.port = port;
    }
    
    public void start() {
        isRunning = true;
        connectAndRun();
    }
    
    private void connectAndRun() {
        while (isRunning) {
            try {
                System.out.println("Connecting to Server " + serverIp + ":" + port + "...");
                socket = new Socket(serverIp, port);
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                
                System.out.println("Connected to Server!");
                
                sendClientInfo();
                startHeartbeat();
                
                // Read messages from server
                String line;
                while ((line = in.readLine()) != null) {
                    try {
                        Message msg = JsonProtocol.deserialize(line);
                        handleServerMessage(msg);
                    } catch (Exception e) {
                        System.err.println("Error parsing message: " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                System.err.println("Connection lost or error: " + e.getMessage());
            } finally {
                cleanup();
            }
            
            if (isRunning) {
                System.out.println("Reconnecting in 5 seconds...");
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
    
    private void sendClientInfo() {
        try {
            InetAddress localMachine = InetAddress.getLocalHost();
            String hostname = localMachine.getHostName();
            String ip = localMachine.getHostAddress();
            
            Map<String, String> info = new HashMap<>();
            info.put("hostname", hostname);
            info.put("ip", ip);
            info.put("os", System.getProperty("os.name"));
            
            Message msg = new Message(MessageType.CLIENT_INFO, hostname, info);
            out.println(JsonProtocol.serialize(msg));
            System.out.println("Sent Client Info: " + info);
            
        } catch (Exception e) {
            System.err.println("Error sending client info: " + e.getMessage());
        }
    }
    
    private void startHeartbeat() {
        if (heartbeatTimer != null) {
            heartbeatTimer.cancel();
        }
        heartbeatTimer = new Timer(true);
        heartbeatTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    InetAddress localMachine = InetAddress.getLocalHost();
                    Message heartbeat = new Message(MessageType.HEARTBEAT, localMachine.getHostName(), "ping");
                    out.println(JsonProtocol.serialize(heartbeat));
                } catch (Exception e) {
                    System.err.println("Error sending heartbeat: " + e.getMessage());
                    cleanup();
                }
            }
        }, 5000, 5000); // 5 seconds
    }
    
    private void handleServerMessage(Message msg) {
        if (msg == null || msg.getType() == null) return;

        if (msg.getType() == MessageType.HEARTBEAT_ACK || msg.getType() == MessageType.ACK) {
            // Heartbeat ACK / PONG received silently
        } else if (msg.getType() == MessageType.COMMAND) {
            String cmd = (msg.getPayload() != null) ? msg.getPayload().toString() : "";
            System.out.println("Received Command: " + cmd);
            try {
                String hostname = InetAddress.getLocalHost().getHostName();
                Message ack = new Message(MessageType.ACK, hostname, "[SAFE_MODE] Giả lập thực thi thành công lệnh: " + cmd);
                out.println(JsonProtocol.serialize(ack));
                System.out.println("Sent ACK response for command: " + cmd);
            } catch (Exception e) {
                System.err.println("Error responding to command: " + e.getMessage());
            }
        } else if (msg.getType() == MessageType.SCREENSHOT_REQUEST) {
            System.out.println("Received Screenshot Request");
            try {
                String hostname = InetAddress.getLocalHost().getHostName();
                Message ack = new Message(MessageType.ACK, hostname, "Screenshot request acknowledged");
                out.println(JsonProtocol.serialize(ack));
            } catch (Exception e) {}
        } else {
            System.out.println("Received from server: " + msg.getType() + " - " + msg.getPayload());
        }
    }
    
    private void cleanup() {
        try {
            if (heartbeatTimer != null) {
                heartbeatTimer.cancel();
                heartbeatTimer = null;
            }
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (Exception e) {
            // ignore
        }
    }
    
    public void stop() {
        isRunning = false;
        cleanup();
    }
    
    public static void main(String[] args) {
        // Default to localhost, port 9999 to match MainServer
        ClientAgent agent = new ClientAgent("127.0.0.1", 9999);
        agent.start();
    }
}
