package com.mycompany.remoteservercore;

import com.mycompany.remoteservercore.features.SystemControl;
import com.mycompany.remoteservercore.model.ClientInfo;
import com.mycompany.remoteservercore.model.MessagePacket;
import com.mycompany.remoteservercore.protocol.JsonUtils;
import com.mycompany.remoteservercore.ui.ClientChatFrame;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Client thử nghiệm (Agent Client) kết nối tới MainServer qua TCP Socket.
 * Thực thi các yêu cầu của Issue #33 & Issue #35 (Issue 14):
 *  1. Parse JSON gói tin nhận được từ Server.
 *  2. Kiểm tra command (LOCK, LOGOUT, RESTART, SHUTDOWN).
 *  3. Thực thi command hệ thống qua SystemControl.
 *  4. Phản hồi kết quả (ACK / ERROR) về cho Server.
 *  5. Chat 2 chiều Server ↔ Client (giao diện ClientChatFrame + xử lý mất kết nối).
 */
public class TestClient {

    public static void runClient(String clientName, String serverIP, int port, boolean safeMode) {
        runClient(clientName, serverIP, port, safeMode, true);
    }

    public static void runClient(String clientName, String serverIP, int port, boolean safeMode, boolean enableGui) {
        // Cấu hình chế độ an toàn Safe Mode cho thử nghiệm
        SystemControl.setSafeMode(safeMode);

        Socket socket = null;
        BufferedWriter writer = null;
        BufferedReader reader = null;
        ClientChatFrame[] chatFrameHolder = new ClientChatFrame[1];
        java.util.concurrent.ScheduledExecutorService sysInfoScheduler = null;

        final boolean[] remoteStreaming = new boolean[]{false};
        final java.awt.Robot[] robotHolder = new java.awt.Robot[1];
        try {
            if (!java.awt.GraphicsEnvironment.isHeadless()) {
                robotHolder[0] = new java.awt.Robot();
            }
        } catch (Exception ignored) {}

        try {
            System.out.println("[" + clientName + "] Đang kết nối tới Server " + serverIP + ":" + port + "...");
            socket = new Socket(serverIP, port);
            System.out.println("[" + clientName + "] Kết nối thành công tới Server!");

            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

            final BufferedWriter outWriter = writer;

            // Khởi tạo giao diện Chat phía Client nếu có hỗ trợ đồ họa
            if (enableGui && !GraphicsEnvironment.isHeadless()) {
                SwingUtilities.invokeLater(() -> {
                    ClientChatFrame frame = new ClientChatFrame(clientName, message -> {
                        try {
                            MessagePacket chatPacket = MessagePacket.createChat(clientName, "SERVER", message);
                            synchronized (outWriter) {
                                outWriter.write(JsonUtils.toJson(chatPacket));
                                outWriter.newLine();
                                outWriter.flush();
                            }
                            return true;
                        } catch (IOException e) {
                            System.err.println("[" + clientName + "] Lỗi khi gửi tin nhắn chat: " + e.getMessage());
                            return false;
                        }
                    });
                    frame.setVisible(true);
                    chatFrameHolder[0] = frame;
                });
            }

            // 1. Khởi tạo luồng định kỳ cập nhật thông số CPU/RAM thực tế lên Server mỗi 5 giây
            sysInfoScheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
            final java.net.Socket currentSocket = socket;

            Runnable sendSysInfoTask = () -> {
                try {
                    if (currentSocket == null || currentSocket.isClosed()) return;
                    ClientInfo clientInfo = new ClientInfo();
                    clientInfo.setIpAddress(currentSocket.getLocalAddress().getHostAddress());
                    clientInfo.setHostName(clientName);
                    clientInfo.setOsName(System.getProperty("os.name"));
                    clientInfo.setCurrentUser(System.getProperty("user.name"));
                    clientInfo.setDepartment("Phòng Kiểm Thử");

                    double cpu = 0.0;
                    long totalMem = 0;
                    long usedMem = 0;
                    double ramPct = 0.0;

                    try {
                        com.sun.management.OperatingSystemMXBean osBean =
                                (com.sun.management.OperatingSystemMXBean) java.lang.management.ManagementFactory.getOperatingSystemMXBean();
                        double load = osBean.getCpuLoad();
                        if (load >= 0) {
                            cpu = Math.round(load * 1000.0) / 10.0;
                        } else {
                            cpu = Math.round(Math.random() * 200.0) / 10.0;
                        }
                        totalMem = osBean.getTotalMemorySize() / (1024 * 1024);
                        long freeMem = osBean.getFreeMemorySize() / (1024 * 1024);
                        usedMem = totalMem - freeMem;
                        if (totalMem > 0) {
                            ramPct = Math.round(((double) usedMem / totalMem) * 1000.0) / 10.0;
                        }
                    } catch (Throwable t) {
                        long total = Runtime.getRuntime().totalMemory() / (1024 * 1024);
                        long free = Runtime.getRuntime().freeMemory() / (1024 * 1024);
                        totalMem = total;
                        usedMem = total - free;
                        if (totalMem > 0) ramPct = Math.round(((double) usedMem / totalMem) * 1000.0) / 10.0;
                    }

                    clientInfo.setCpuUsage(cpu);
                    clientInfo.setRamUsage(ramPct);
                    clientInfo.setTotalRamMb(totalMem);
                    clientInfo.setUsedRamMb(usedMem);

                    MessagePacket sysInfoPacket = new MessagePacket(
                            MessagePacket.TYPE_SYS_INFO,
                            clientName,
                            "SERVER",
                            JsonUtils.toJson(clientInfo)
                    );

                    synchronized (outWriter) {
                        outWriter.write(JsonUtils.toJson(sysInfoPacket));
                        outWriter.newLine();
                        outWriter.flush();
                    }
                } catch (Exception ignored) {}
            };

            // Gửi gói tin SYS_INFO đầu tiên ngay khi kết nối
            sendSysInfoTask.run();
            // Lập lịch gửi định kỳ mỗi 5 giây
            sysInfoScheduler.scheduleAtFixedRate(sendSysInfoTask, 5, 5, java.util.concurrent.TimeUnit.SECONDS);

            System.out.println("[" + clientName + "] >> Đã kích hoạt giám sát CPU/RAM thời gian thực gửi lên Server.");

            // 2. Vòng lặp lắng nghe lệnh và tin nhắn từ Server
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                if (line.startsWith("BLOCK_WEB:")) {
                    String[] domains = line.substring("BLOCK_WEB:".length()).split(",");
                    java.util.List<String> domainList = new java.util.ArrayList<>();
                    for (String d : domains) {
                        if (!d.trim().isEmpty()) {
                            domainList.add(d.trim());
                        }
                    }
                    StringBuilder sbStatus = new StringBuilder();
                    boolean applied = com.mycompany.remoteservercore.features.WebBlocker.applyHostsBlock(domainList, sbStatus);
                    System.out.println("[" + clientName + "] " + sbStatus.toString());

                    MessagePacket resBlockPacket = applied
                            ? MessagePacket.createAck(clientName, "SERVER", sbStatus.toString())
                            : MessagePacket.createError(clientName, "SERVER", sbStatus.toString());
                    synchronized (outWriter) {
                        outWriter.write(JsonUtils.toJson(resBlockPacket));
                        outWriter.newLine();
                        outWriter.flush();
                    }
                    continue;
                }

                // 2.1. Parse JSON gói tin từ Server
                MessagePacket packet = JsonUtils.fromJson(line, MessagePacket.class);
                if (packet == null) {
                    System.err.println("[" + clientName + "] << Lỗi Parse JSON: " + line);
                    MessagePacket errPacket = MessagePacket.createError(clientName, "SERVER", "Lỗi định dạng JSON gói tin");
                    synchronized (outWriter) {
                        outWriter.write(JsonUtils.toJson(errPacket));
                        outWriter.newLine();
                        outWriter.flush();
                    }
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
                        synchronized (outWriter) {
                            outWriter.write(JsonUtils.toJson(errPacket));
                            outWriter.newLine();
                            outWriter.flush();
                        }
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
                        synchronized (outWriter) {
                            outWriter.write(JsonUtils.toJson(ackPacket));
                            outWriter.newLine();
                            outWriter.flush();
                        }

                    } catch (Exception e) {
                        System.err.println("[" + clientName + "] Lỗi ngoại lệ khi thực thi lệnh " + commandStr + ": " + e.getMessage());
                        MessagePacket errPacket = MessagePacket.createError(
                                clientName,
                                packet.getSender(),
                                "Lỗi thực thi lệnh " + commandStr + ": " + e.getMessage()
                        );
                        synchronized (outWriter) {
                            outWriter.write(JsonUtils.toJson(errPacket));
                            outWriter.newLine();
                            outWriter.flush();
                        }
                    }

                } else if (MessagePacket.TYPE_ACK.equalsIgnoreCase(packet.getType())) {
                    System.out.println("[" + clientName + "] ACK từ Server: " + packet.getPayload());

                } else if (MessagePacket.TYPE_CHAT.equalsIgnoreCase(packet.getType())) {
                    // Xử lý gói tin CHAT 2 chiều từ Server (Issue #35 / Issue 14)
                    System.out.println("[" + clientName + "] 💬 Tin nhắn Chat từ Server: " + packet.getPayload());
                    if (chatFrameHolder[0] != null) {
                        chatFrameHolder[0].onMessageReceived(packet.getSender(), packet.getPayload(), packet.getTimestamp());
                    }

                } else if (MessagePacket.TYPE_BLOCK_WEB.equalsIgnoreCase(packet.getType())) {
                    System.out.println("[" + clientName + "] 🚫 Nhận cấu hình chặn web từ Server...");
                    java.util.List<String> domainList = JsonUtils.fromJson(packet.getPayload(), new com.google.gson.reflect.TypeToken<java.util.List<String>>(){}.getType());
                    if (domainList == null) {
                        domainList = new java.util.ArrayList<>();
                    }
                    StringBuilder sbStatus = new StringBuilder();
                    boolean applied = com.mycompany.remoteservercore.features.WebBlocker.applyHostsBlock(domainList, sbStatus);
                    System.out.println("[" + clientName + "] " + sbStatus.toString());
                    MessagePacket resBlockPacket = applied
                            ? MessagePacket.createAck(clientName, packet.getSender(), sbStatus.toString())
                            : MessagePacket.createError(clientName, packet.getSender(), sbStatus.toString());
                    synchronized (outWriter) {
                        outWriter.write(JsonUtils.toJson(resBlockPacket));
                        outWriter.newLine();
                        outWriter.flush();
                    }

                } else if (MessagePacket.TYPE_SCREENSHOT_REQ.equalsIgnoreCase(packet.getType())) {
                    // Xử lý gói tin yêu cầu chụp màn hình từ Server (Issue 17 / Issue #38)
                    System.out.println("[" + clientName + "] 📷 Nhận yêu cầu SCREENSHOT từ Server...");
                    try {
                        String base64Image = com.mycompany.remoteservercore.features.ScreenCapturer.captureScreenAsBase64();
                        MessagePacket resPacket = MessagePacket.createScreenshotResponse(clientName, base64Image);
                        synchronized (outWriter) {
                            outWriter.write(JsonUtils.toJson(resPacket));
                            outWriter.newLine();
                            outWriter.flush();
                        }
                        System.out.println("[" + clientName + "] >> Đã chụp và gửi ảnh màn hình về Server thành công!");
                    } catch (Exception e) {
                        System.err.println("[" + clientName + "] Lỗi khi chụp màn hình: " + e.getMessage());
                        MessagePacket errPacket = MessagePacket.createError(clientName, packet.getSender(), "Lỗi chụp màn hình: " + e.getMessage());
                        synchronized (outWriter) {
                            outWriter.write(JsonUtils.toJson(errPacket));
                            outWriter.newLine();
                            outWriter.flush();
                        }
                    }

                } else if (MessagePacket.TYPE_REMOTE_START.equalsIgnoreCase(packet.getType())) {
                    // Yêu cầu bắt đầu phiên điều khiển từ xa giống UltraViewer
                    System.out.println("[" + clientName + "] 🖥️ Nhận yêu cầu BẮT ĐẦU Remote Desktop từ Server...");
                    remoteStreaming[0] = true;
                    final Socket curSock = socket;
                    Thread streamThread = new Thread(() -> {
                        long frameId = 0;
                        try {
                            java.awt.Robot streamRobot = robotHolder[0] != null ? robotHolder[0] : new java.awt.Robot();
                            java.awt.Rectangle screenRect = new java.awt.Rectangle(java.awt.Toolkit.getDefaultToolkit().getScreenSize());

                            while (remoteStreaming[0] && curSock != null && !curSock.isClosed()) {
                                java.awt.image.BufferedImage cap = streamRobot.createScreenCapture(screenRect);
                                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                                javax.imageio.ImageIO.write(cap, "jpg", baos);
                                String base64 = java.util.Base64.getEncoder().encodeToString(baos.toByteArray());

                                com.mycompany.remoteservercore.model.RemoteFrameData frameData =
                                        new com.mycompany.remoteservercore.model.RemoteFrameData(screenRect.width, screenRect.height, base64, frameId++);
                                MessagePacket framePacket = new MessagePacket(
                                        MessagePacket.TYPE_REMOTE_FRAME,
                                        clientName,
                                        "SERVER",
                                        JsonUtils.toJson(frameData)
                                );

                                synchronized (outWriter) {
                                    outWriter.write(JsonUtils.toJson(framePacket));
                                    outWriter.newLine();
                                    outWriter.flush();
                                }

                                Thread.sleep(70); // ~14 FPS ổn định và mượt mà trong LAN
                            }
                        } catch (Exception e) {
                            System.err.println("[" + clientName + "] Dừng luồng streaming Remote Desktop: " + e.getMessage());
                        }
                    }, "Client-RemoteStream-" + clientName);
                    streamThread.setDaemon(true);
                    streamThread.start();

                } else if (MessagePacket.TYPE_REMOTE_STOP.equalsIgnoreCase(packet.getType())) {
                    System.out.println("[" + clientName + "] ⏹ Dừng Remote Desktop theo yêu cầu từ Server.");
                    remoteStreaming[0] = false;

                } else if (MessagePacket.TYPE_REMOTE_INPUT.equalsIgnoreCase(packet.getType())) {
                    try {
                        com.mycompany.remoteservercore.model.RemoteInputEvent ev =
                                JsonUtils.fromJson(packet.getPayload(), com.mycompany.remoteservercore.model.RemoteInputEvent.class);
                        if (ev != null && robotHolder[0] != null) {
                            java.awt.Robot r = robotHolder[0];
                            switch (ev.getAction()) {
                                case com.mycompany.remoteservercore.model.RemoteInputEvent.ACTION_MOUSE_MOVE:
                                    r.mouseMove(ev.getX(), ev.getY());
                                    break;
                                case com.mycompany.remoteservercore.model.RemoteInputEvent.ACTION_MOUSE_PRESS:
                                    r.mouseMove(ev.getX(), ev.getY());
                                    r.mousePress(ev.getButton());
                                    break;
                                case com.mycompany.remoteservercore.model.RemoteInputEvent.ACTION_MOUSE_RELEASE:
                                    r.mouseMove(ev.getX(), ev.getY());
                                    r.mouseRelease(ev.getButton());
                                    break;
                                case com.mycompany.remoteservercore.model.RemoteInputEvent.ACTION_MOUSE_WHEEL:
                                    r.mouseWheel(ev.getWheelRotation());
                                    break;
                                case com.mycompany.remoteservercore.model.RemoteInputEvent.ACTION_KEY_PRESS:
                                    try { r.keyPress(ev.getKeyCode()); } catch (Exception ignored) {}
                                    break;
                                case com.mycompany.remoteservercore.model.RemoteInputEvent.ACTION_KEY_RELEASE:
                                    try { r.keyRelease(ev.getKeyCode()); } catch (Exception ignored) {}
                                    break;
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("[" + clientName + "] Lỗi thực thi RemoteInput: " + e.getMessage());
                    }
                }
            }

        } catch (IOException e) {
            System.err.println("[" + clientName + "] Mất kết nối tới Server: " + e.getMessage());
            if (chatFrameHolder[0] != null) {
                chatFrameHolder[0].setConnected(false);
            }
        } finally {
            remoteStreaming[0] = false;
            if (sysInfoScheduler != null) {
                sysInfoScheduler.shutdownNow();
            }
            try {
                if (socket != null && !socket.isClosed()) socket.close();
            } catch (IOException ignored) {}
            System.out.println("[" + clientName + "] Đã dừng Client Agent.");
        }
    }

    public static void main(String[] args) {
        String serverIP = (args.length > 0 && !args[0].isEmpty()) ? args[0] : "127.0.0.1";
        int port = 9999;
        if (args.length > 1) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("[CẢNH BÁO] Cổng không hợp lệ, dùng mặc định 9999");
            }
        }
        // Mặc định safeMode = false để thực thi lệnh thật (LOCK, SHUTDOWN...)
        boolean safeMode = (args.length > 2) ? Boolean.parseBoolean(args[2]) : false;

        System.out.println("=== BẮT ĐẦU CHẠY CLIENT AGENT ===");
        System.out.println("-> Kết nối tới Server: " + serverIP + ":" + port + " (SafeMode: " + safeMode + " - Lệnh thật: " + (!safeMode) + ")");

        runClient("CLIENT-MAY-01", serverIP, port, safeMode, true);
    }
}
