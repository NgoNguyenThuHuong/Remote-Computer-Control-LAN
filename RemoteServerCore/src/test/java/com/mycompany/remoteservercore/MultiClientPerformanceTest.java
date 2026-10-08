package com.mycompany.remoteservercore;

import com.mycompany.remoteservercore.core.AgentCore;
import com.mycompany.remoteservercore.core.ClientHandler;
import com.mycompany.remoteservercore.core.ClientManager;
import com.mycompany.remoteservercore.database.DatabaseManager;
import com.mycompany.remoteservercore.database.LogDAO;
import com.mycompany.remoteservercore.features.ScreenCapturer;
import com.mycompany.remoteservercore.features.SystemControl;
import com.mycompany.remoteservercore.model.ClientInfo;
import com.mycompany.remoteservercore.model.LogEntry;
import com.mycompany.remoteservercore.model.MessagePacket;
import com.mycompany.remoteservercore.protocol.JsonUtils;
import com.mycompany.remoteservercore.protocol.PacketRouter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kiểm thử toàn diện cho Issue #40 (Issue 19): Kiểm thử nhiều Client & hiệu năng LAN.
 * Mục tiêu:
 *   - Kiểm tra khả năng Server xử lý nhiều Client đồng thời trong LAN (1, 2, 5, 10, 20 Clients).
 *   - Kiểm tra các tính năng cốt lõi: Connection, Heartbeat, Command, Chat, Screenshot, Log, Disconnect, Reconnect.
 *   - Đo lường và ghi nhận: Thời gian phản hồi (min/max/avg ms), tỷ lệ lỗi, mức tiêu thụ tài nguyên (RAM/CPU).
 */
public class MultiClientPerformanceTest {

    private static final int MULTI_TEST_PORT = 9995;
    private static ServerSocket serverSocket;
    private static ExecutorService serverPool;
    private static volatile boolean running = true;
    private static final ConcurrentHashMap<String, BlockingQueue<MessagePacket>> clientPacketQueues = new ConcurrentHashMap<>();

    @BeforeAll
    public static void setUp() throws Exception {
        SystemControl.setSafeMode(true);

        try {
            DatabaseManager.initialize();
        } catch (Exception ignored) {}

        PacketRouter router = PacketRouter.getInstance();

        // 1. Router handler cho SYS_INFO
        router.registerHandler(MessagePacket.TYPE_SYS_INFO, (packet, sender) -> {
            ClientInfo info = JsonUtils.fromJson(packet.getPayload(), ClientInfo.class);
            if (info != null) {
                if (sender != null) {
                    if (info.getIpAddress() != null && !info.getIpAddress().trim().isEmpty()
                            && !info.getIpAddress().equals("127.0.0.1") && !info.getIpAddress().equalsIgnoreCase("localhost")) {
                        sender.setClientIp(info.getIpAddress());
                    } else {
                        info.setIpAddress(sender.getClientIp());
                    }
                }
                ClientManager.getInstance().updateClientInfo(info);
                try {
                    LogDAO.saveLog(new LogEntry(info.getIpAddress(), "SYS_INFO", "Client registered: " + info.getHostName()));
                } catch (Exception ignored) {}
            }
        });

        // 2. Router handler cho HEARTBEAT
        router.registerHandler(MessagePacket.TYPE_HEARTBEAT, (packet, sender) -> {
            if (sender != null) {
                ClientInfo existing = ClientManager.getInstance().getClientInfo(sender.getClientIp());
                if (existing != null) {
                    existing.updatePing();
                }
                sender.sendPacket(MessagePacket.createAck("SERVER", sender.getClientIp(), "PONG"));
            }
        });

        // 3. Router handler cho CHAT
        router.registerHandler(MessagePacket.TYPE_CHAT, (packet, sender) -> {
            String clientIp = (sender != null) ? sender.getClientIp() : packet.getSender();
            if (packet.getSender() == null || packet.getSender().trim().isEmpty()) {
                packet.setSender(clientIp);
            }
            ClientManager.getInstance().notifyChatMessageReceived(packet);
            try {
                LogDAO.saveLog(new LogEntry(clientIp, "CHAT", packet.getPayload()));
            } catch (Exception ignored) {}
        });

        // 4. Router handler cho ACK
        router.registerHandler(MessagePacket.TYPE_ACK, (packet, sender) -> {
            String ip = (sender != null) ? sender.getClientIp() : packet.getSender();
            ClientManager.getInstance().notifyCommandResponse(ip, MessagePacket.TYPE_ACK, packet.getPayload());
            try {
                LogDAO.saveLog(new LogEntry(ip, "ACK", packet.getPayload()));
            } catch (Exception ignored) {}
        });

        // 5. Router handler cho ERROR
        router.registerHandler(MessagePacket.TYPE_ERROR, (packet, sender) -> {
            String ip = (sender != null) ? sender.getClientIp() : packet.getSender();
            ClientManager.getInstance().notifyCommandResponse(ip, MessagePacket.TYPE_ERROR, packet.getPayload());
            try {
                LogDAO.saveLog(new LogEntry(ip, "ERROR", packet.getPayload()));
            } catch (Exception ignored) {}
        });

        // 6. Router handler cho SCREENSHOT_RES
        router.registerHandler(MessagePacket.TYPE_SCREENSHOT_RES, (packet, sender) -> {
            String ip = (sender != null) ? sender.getClientIp() : packet.getSender();
            ClientManager.getInstance().notifyScreenshotReceived(ip, packet.getPayload());
            try {
                LogDAO.saveLog(new LogEntry(ip, "SCREENSHOT", "Length: " + (packet.getPayload() != null ? packet.getPayload().length() : 0)));
            } catch (Exception ignored) {}
        });

        // Khởi chạy ServerSocket với Thread Pool 50 luồng
        serverSocket = new ServerSocket(MULTI_TEST_PORT);
        serverPool = Executors.newFixedThreadPool(50);

        Thread acceptThread = new Thread(() -> {
            while (running) {
                try {
                    Socket socket = serverSocket.accept();
                    ClientHandler handler = new ClientHandler(socket, router);
                    serverPool.execute(handler);
                } catch (Exception ignored) {}
            }
        }, "MultiClient-AcceptThread");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    @AfterAll
    public static void tearDown() throws Exception {
        running = false;
        ClientManager.getInstance().clearAllClients();
        PacketRouter.getInstance().clearAllHandlers();
        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
        }
        if (serverPool != null) {
            serverPool.shutdownNow();
        }
    }

    @BeforeEach
    public void resetClients() {
        ClientManager.getInstance().clearAllClients();
        clientPacketQueues.clear();
        try {
            Thread.sleep(150);
        } catch (InterruptedException ignored) {}
    }

    /**
     * Tạo và kết nối một tập hợp Client Agent mô phỏng mạng LAN.
     */
    private List<AgentCore> createAndConnectClients(int count) throws Exception {
        return createAndConnectClients(count, 1);
    }

    private List<AgentCore> createAndConnectClients(int count, int subnetId) throws Exception {
        List<AgentCore> clients = new ArrayList<>();
        CountDownLatch connectLatch = new CountDownLatch(count);

        for (int i = 1; i <= count; i++) {
            String clientName = String.format("LAN-CLIENT-%d-%02d", subnetId, i);
            String virtualIp = String.format("192.168.%d.%d", subnetId, 100 + i);
            AgentCore agent = new AgentCore("127.0.0.1", MULTI_TEST_PORT, clientName, virtualIp);

            BlockingQueue<MessagePacket> queue = new LinkedBlockingQueue<>();
            clientPacketQueues.put(clientName, queue);
            clientPacketQueues.put(virtualIp, queue);

            agent.setPacketListener(queue::offer);

            clients.add(agent);
        }

        // Kết nối đồng thời sử dụng Executor
        ExecutorService connectPool = Executors.newFixedThreadPool(Math.min(count, 20));
        List<Future<?>> futures = new ArrayList<>();
        for (AgentCore agent : clients) {
            futures.add(connectPool.submit(() -> {
                try {
                    agent.connect();
                    connectLatch.countDown();
                } catch (IOException e) {
                    System.err.println("Lỗi kết nối client: " + e.getMessage());
                }
            }));
        }

        boolean allConnected = connectLatch.await(5, TimeUnit.SECONDS);
        connectPool.shutdown();
        assertTrue(allConnected, "Tất cả " + count + " clients phải kết nối thành công trong thời hạn cho phép");

        // Đợi Server xử lý xong gói SYS_INFO cho toàn bộ clients
        long deadline = System.currentTimeMillis() + 5000;
        boolean allReady = false;
        while (System.currentTimeMillis() < deadline && !allReady) {
            allReady = true;
            for (AgentCore c : clients) {
                if (ClientManager.getInstance().getClientInfo(c.getVirtualIp()) == null) {
                    allReady = false;
                    break;
                }
            }
            if (!allReady) {
                Thread.sleep(30);
            }
        }
        assertTrue(allReady, "Tất cả " + count + " clients phải hoàn tất đăng ký thông tin hệ thống trên Server");

        // Dọn sạch hàng đợi sau giai đoạn handshake SYS_INFO
        for (AgentCore c : clients) {
            BlockingQueue<MessagePacket> q = clientPacketQueues.get(c.getClientName());
            if (q != null) {
                q.clear();
            }
        }
        return clients;
    }

    private MessagePacket pollForPayload(BlockingQueue<MessagePacket> queue, String expectedPayload, long timeoutSeconds) throws InterruptedException {
        long end = System.currentTimeMillis() + timeoutSeconds * 1000;
        while (System.currentTimeMillis() < end) {
            MessagePacket pkt = queue.poll(100, TimeUnit.MILLISECONDS);
            if (pkt != null && (expectedPayload == null || expectedPayload.equals(pkt.getPayload()))) {
                return pkt;
            }
        }
        return null;
    }

    private MessagePacket pollForType(BlockingQueue<MessagePacket> queue, String expectedType, long timeoutSeconds) throws InterruptedException {
        long end = System.currentTimeMillis() + timeoutSeconds * 1000;
        while (System.currentTimeMillis() < end) {
            MessagePacket pkt = queue.poll(100, TimeUnit.MILLISECONDS);
            if (pkt != null && (expectedType == null || expectedType.equalsIgnoreCase(pkt.getType()))) {
                return pkt;
            }
        }
        return null;
    }

    private void disconnectAll(List<AgentCore> clients) {
        for (AgentCore client : clients) {
            try {
                client.disconnect();
            } catch (Exception ignored) {}
        }
        try {
            Thread.sleep(200);
        } catch (InterruptedException ignored) {}
        ClientManager.getInstance().clearAllClients();
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // TEST CASE 1: 1 Client — Kết nối, Heartbeat, Command, Chat, Screenshot, Disconnect, Reconnect
    // ─────────────────────────────────────────────────────────────────────────────
    @Test
    public void test1ClientLifecycle() throws Exception {
        System.out.println("\n========== TEST 1 CLIENT: KIỂM THỬ ĐƠN LẺ VÀ VÒNG ĐỜI TOÀN DIỆN ==========");
        List<AgentCore> clients = createAndConnectClients(1, 11);
        AgentCore client = clients.get(0);
        String ip = client.getVirtualIp();
        String name = client.getClientName();

        try {
            // 1. Connection check
            assertTrue(client.isConnected(), "Client 1 phải duy trì kết nối TCP");
            assertEquals(1, ClientManager.getInstance().getOnlineCount(), "Số máy online trên Server phải bằng 1");
            ClientInfo info = ClientManager.getInstance().getClientInfo(ip);
            assertNotNull(info, "Server phải lưu ClientInfo cho IP " + ip);
            assertEquals(ClientInfo.STATUS_ONLINE, info.getStatus());

            // 2. Heartbeat roundtrip
            long startHb = System.currentTimeMillis();
            client.sendHeartbeat();
            MessagePacket hbAck = pollForPayload(clientPacketQueues.get(name), "PONG", 2);
            long hbLatency = System.currentTimeMillis() - startHb;
            assertNotNull(hbAck, "Client phải nhận được phản hồi PONG");
            assertEquals("PONG", hbAck.getPayload());
            System.out.println("[1 Client] Heartbeat Roundtrip: " + hbLatency + " ms");

            // 3. Command: Server gửi LOCK -> Client thực thi -> Server nhận ACK
            BlockingQueue<String[]> cmdQueue = new LinkedBlockingQueue<>();
            ClientManager.CommandResponseListener cmdListener = (respIp, type, msg) -> {
                if (ip.equals(respIp)) cmdQueue.offer(new String[]{type, msg});
            };
            ClientManager.getInstance().addCommandListener(cmdListener);

            long startCmd = System.currentTimeMillis();
            boolean sent = ClientManager.getInstance().sendTo(ip, MessagePacket.createCommand(ip, MessagePacket.CMD_LOCK));
            assertTrue(sent, "Server phải gửi được lệnh tới IP " + ip);

            String[] cmdResp = cmdQueue.poll(3, TimeUnit.SECONDS);
            long cmdLatency = System.currentTimeMillis() - startCmd;
            assertNotNull(cmdResp, "Server phải nhận được phản hồi ACK từ Client");
            assertEquals(MessagePacket.TYPE_ACK, cmdResp[0]);
            assertTrue(cmdResp[1].contains("LOCK"));
            System.out.println("[1 Client] Command LOCK Latency: " + cmdLatency + " ms");
            ClientManager.getInstance().removeCommandListener(cmdListener);

            // 4. Chat 2 chiều
            BlockingQueue<MessagePacket> serverChatQueue = new LinkedBlockingQueue<>();
            ClientManager.ChatMessageListener chatListener = serverChatQueue::offer;
            ClientManager.getInstance().addChatMessageListener(chatListener);

            // Client -> Server
            client.sendChatMessage("Xin chào Server từ " + name);
            MessagePacket serverChat = serverChatQueue.poll(2, TimeUnit.SECONDS);
            assertNotNull(serverChat, "Server phải nhận được tin nhắn chat từ Client");
            assertEquals("Xin chào Server từ " + name, serverChat.getPayload());

            // Server -> Client
            ClientManager.getInstance().sendTo(ip, MessagePacket.createChat("SERVER", ip, "Chào Client 1!"));
            MessagePacket clientChat = pollForType(clientPacketQueues.get(name), MessagePacket.TYPE_CHAT, 3);
            assertNotNull(clientChat, "Client phải nhận được tin nhắn chat từ Server");
            assertEquals("Chào Client 1!", clientChat.getPayload());
            ClientManager.getInstance().removeChatMessageListener(chatListener);

            // 5. Screenshot
            BlockingQueue<String> screenQueue = new LinkedBlockingQueue<>();
            ClientManager.ScreenshotListener screenListener = (sIp, base64) -> {
                if (ip.equals(sIp)) screenQueue.offer(base64);
            };
            ClientManager.getInstance().addScreenshotListener(screenListener);

            long startShot = System.currentTimeMillis();
            ClientManager.getInstance().sendTo(ip, MessagePacket.createScreenshotRequest(ip));
            String base64Image = screenQueue.poll(4, TimeUnit.SECONDS);
            long shotLatency = System.currentTimeMillis() - startShot;
            assertNotNull(base64Image, "Server phải nhận được ảnh màn hình");
            BufferedImage img = ScreenCapturer.base64ToImage(base64Image);
            assertNotNull(img);
            assertTrue(img.getWidth() > 0 && img.getHeight() > 0);
            System.out.println("[1 Client] Screenshot Capture & Transfer: " + shotLatency + " ms (" + base64Image.length() + " bytes Base64)");
            ClientManager.getInstance().removeScreenshotListener(screenListener);

            // 6. Disconnect
            CountDownLatch disconnectLatch = new CountDownLatch(1);
            ClientManager.getInstance().addListener(new ClientManager.ClientEventListener() {
                @Override public void onClientConnected(ClientInfo ci) {}
                @Override public void onClientUpdated(ClientInfo ci) {}
                @Override public void onClientDisconnected(String discIp) {
                    if (ip.equals(discIp)) disconnectLatch.countDown();
                }
            });

            client.disconnect();
            boolean discReported = disconnectLatch.await(2, TimeUnit.SECONDS);
            assertTrue(discReported, "Server phải ghi nhận sự kiện Disconnect của Client");
            assertEquals(0, ClientManager.getInstance().getOnlineCount(), "Số máy online phải về 0 sau khi ngắt kết nối");

            // 7. Reconnect
            client.reconnect();
            Thread.sleep(300);
            assertEquals(1, ClientManager.getInstance().getOnlineCount(), "Số máy online phải khôi phục về 1 sau khi Reconnect");
            assertEquals(ClientInfo.STATUS_ONLINE, ClientManager.getInstance().getClientInfo(ip).getStatus());
            System.out.println("[1 Client] Reconnect thành công!");

        } finally {
            disconnectAll(clients);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // TEST CASE 2: 2 Clients — Kết nối đồng thời, gửi lệnh song song, Broadcast chat
    // ─────────────────────────────────────────────────────────────────────────────
    @Test
    public void test2ClientsConcurrent() throws Exception {
        System.out.println("\n========== TEST 2 CLIENTS: ĐỒNG THỜI VÀ PHÂN QUYỀN LỆNH ==========");
        List<AgentCore> clients = createAndConnectClients(2, 22);
        try {
            assertEquals(2, ClientManager.getInstance().getOnlineCount(), "Server phải ghi nhận đúng 2 Client online");

            AgentCore c1 = clients.get(0);
            AgentCore c2 = clients.get(1);

            // Gửi lệnh khác nhau cho từng client
            ConcurrentHashMap<String, String> responses = new ConcurrentHashMap<>();
            CountDownLatch ackLatch = new CountDownLatch(2);

            ClientManager.CommandResponseListener cmdListener = (clientIp, type, msg) -> {
                responses.put(clientIp, msg);
                ackLatch.countDown();
            };
            ClientManager.getInstance().addCommandListener(cmdListener);

            ClientManager.getInstance().sendTo(c1.getVirtualIp(), MessagePacket.createCommand(c1.getVirtualIp(), MessagePacket.CMD_LOCK));
            ClientManager.getInstance().sendTo(c2.getVirtualIp(), MessagePacket.createCommand(c2.getVirtualIp(), MessagePacket.CMD_RESTART));

            boolean gotBothAcks = ackLatch.await(3, TimeUnit.SECONDS);
            assertTrue(gotBothAcks, "Server phải nhận đủ phản hồi lệnh từ cả 2 Clients");
            assertTrue(responses.get(c1.getVirtualIp()).contains("LOCK"));
            assertTrue(responses.get(c2.getVirtualIp()).contains("RESTART"));
            ClientManager.getInstance().removeCommandListener(cmdListener);

            // Broadcast Chat tới cả 2 Client
            String broadcastMsg = "THÔNG BÁO LAN TOÀN BỘ PHÒNG BAN!";
            ClientManager.getInstance().broadcast(MessagePacket.createChat("SERVER", "ALL", broadcastMsg));

            MessagePacket p1 = pollForType(clientPacketQueues.get(c1.getClientName()), MessagePacket.TYPE_CHAT, 3);
            MessagePacket p2 = pollForType(clientPacketQueues.get(c2.getClientName()), MessagePacket.TYPE_CHAT, 3);

            assertNotNull(p1, "Client 1 phải nhận được gói tin Broadcast");
            assertNotNull(p2, "Client 2 phải nhận được gói tin Broadcast");
            assertEquals(broadcastMsg, p1.getPayload());
            assertEquals(broadcastMsg, p2.getPayload());
            System.out.println("[2 Clients] Broadcast nhận thành công trên cả 2 máy!");

        } finally {
            disconnectAll(clients);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // TEST CASE 3: 5 Clients — Chịu tải trung bình, Heartbeat đồng thời, Command roundtrip
    // ─────────────────────────────────────────────────────────────────────────────
    @Test
    public void test5ClientsScale() throws Exception {
        System.out.println("\n========== TEST 5 CLIENTS: KIỂM THỬ QUY MÔ PHÒNG BAN NHỎ ==========");
        long startConn = System.currentTimeMillis();
        List<AgentCore> clients = createAndConnectClients(5, 33);
        long connTime = System.currentTimeMillis() - startConn;
        System.out.println("[5 Clients] Thời gian kết nối thành công: " + connTime + " ms");

        try {
            assertEquals(5, ClientManager.getInstance().getOnlineCount(), "Cả 5 Clients phải online");

            // 1. Heartbeat đồng thời từ 5 máy
            long startHb = System.currentTimeMillis();
            CountDownLatch pongLatch = new CountDownLatch(5);
            for (AgentCore client : clients) {
                new Thread(() -> {
                    client.sendHeartbeat();
                    try {
                        MessagePacket pong = pollForPayload(clientPacketQueues.get(client.getClientName()), "PONG", 3);
                        if (pong != null) {
                            pongLatch.countDown();
                        }
                    } catch (InterruptedException ignored) {}
                }).start();
            }
            boolean allPongs = pongLatch.await(3, TimeUnit.SECONDS);
            long hbDuration = System.currentTimeMillis() - startHb;
            assertTrue(allPongs, "Cả 5 clients phải nhận được phản hồi PONG");
            System.out.println("[5 Clients] Hoàn thành 5 Heartbeats đồng thời trong: " + hbDuration + " ms (TB: " + (hbDuration / 5.0) + " ms/client)");

            // 2. Chụp màn hình tuần tự từ 5 máy
            long startShot = System.currentTimeMillis();
            int shotsSuccess = 0;
            for (AgentCore client : clients) {
                BlockingQueue<String> queue = new LinkedBlockingQueue<>();
                ClientManager.ScreenshotListener listener = (ip, b64) -> {
                    if (client.getVirtualIp().equals(ip)) queue.offer(b64);
                };
                ClientManager.getInstance().addScreenshotListener(listener);
                ClientManager.getInstance().sendTo(client.getVirtualIp(), MessagePacket.createScreenshotRequest(client.getVirtualIp()));
                String imgData = queue.poll(3, TimeUnit.SECONDS);
                ClientManager.getInstance().removeScreenshotListener(listener);
                if (imgData != null && !imgData.isEmpty()) {
                    shotsSuccess++;
                }
            }
            long shotDuration = System.currentTimeMillis() - startShot;
            assertEquals(5, shotsSuccess, "Phải nhận đủ 5 ảnh màn hình từ 5 clients");
            System.out.println("[5 Clients] Chụp & nhận 5 Screenshots hoàn tất trong: " + shotDuration + " ms (TB: " + (shotDuration / 5.0) + " ms/client)");

        } finally {
            disconnectAll(clients);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // TEST CASE 4: 10 Clients — Tải cao, Đổi trạng thái, Lỗi lệnh & Nhật ký Log
    // ─────────────────────────────────────────────────────────────────────────────
    @Test
    public void test10ClientsScale() throws Exception {
        System.out.println("\n========== TEST 10 CLIENTS: KIỂM THỬ QUY MÔ PHÒNG BAN VỪA ==========");
        long startConn = System.currentTimeMillis();
        List<AgentCore> clients = createAndConnectClients(10, 44);
        long connTime = System.currentTimeMillis() - startConn;
        System.out.println("[10 Clients] Kết nối 10 clients thành công trong: " + connTime + " ms");

        try {
            assertEquals(10, ClientManager.getInstance().getOnlineCount(), "Server phải quản lý đủ 10 Clients online");

            // 1. Gửi lệnh đồng loạt tới tất cả 10 clients
            CountDownLatch cmdLatch = new CountDownLatch(10);
            AtomicInteger acks = new AtomicInteger();
            ClientManager.CommandResponseListener listener = (ip, type, msg) -> {
                if (MessagePacket.TYPE_ACK.equals(type)) {
                    acks.incrementAndGet();
                }
                cmdLatch.countDown();
            };
            ClientManager.getInstance().addCommandListener(listener);

            long startCmd = System.currentTimeMillis();
            for (AgentCore client : clients) {
                ClientManager.getInstance().sendTo(client.getVirtualIp(),
                        MessagePacket.createCommand(client.getVirtualIp(), MessagePacket.CMD_LOGOUT));
            }

            boolean allCmds = cmdLatch.await(4, TimeUnit.SECONDS);
            long cmdTime = System.currentTimeMillis() - startCmd;
            ClientManager.getInstance().removeCommandListener(listener);

            assertTrue(allCmds, "10/10 lệnh phải nhận được phản hồi");
            assertEquals(10, acks.get(), "Cả 10 máy phải thực thi lệnh LOGOUT thành công");
            System.out.println("[10 Clients] 10 lệnh LOGOUT hoàn thành trong: " + cmdTime + " ms (TB: " + (cmdTime / 10.0) + " ms/client)");

            // 2. Kiểm tra lệnh không hợp lệ (ERROR handling)
            CountDownLatch errLatch = new CountDownLatch(1);
            AtomicInteger errCount = new AtomicInteger();
            ClientManager.CommandResponseListener errListener = (ip, type, msg) -> {
                if (MessagePacket.TYPE_ERROR.equals(type)) {
                    errCount.incrementAndGet();
                    errLatch.countDown();
                }
            };
            ClientManager.getInstance().addCommandListener(errListener);
            ClientManager.getInstance().sendTo(clients.get(0).getVirtualIp(),
                    MessagePacket.createCommand(clients.get(0).getVirtualIp(), "UNKNOWN_INVALID_CMD"));

            boolean gotError = errLatch.await(3, TimeUnit.SECONDS);
            ClientManager.getInstance().removeCommandListener(errListener);
            assertTrue(gotError, "Server phải nhận được báo lỗi khi gửi lệnh không hợp lệ");
            assertEquals(1, errCount.get());

            // 3. Ngắt kết nối 5 clients -> kiểm tra còn đúng 5 clients online
            for (int i = 0; i < 5; i++) {
                clients.get(i).disconnect();
            }
            long deadlineDisc = System.currentTimeMillis() + 3000;
            while (System.currentTimeMillis() < deadlineDisc && ClientManager.getInstance().getOnlineCount() > 5) {
                Thread.sleep(50);
            }
            assertEquals(5, ClientManager.getInstance().getOnlineCount(), "Sau khi ngắt 5 máy, số online phải còn 5");

            // Reconnect 5 máy vừa ngắt
            for (int i = 0; i < 5; i++) {
                clients.get(i).reconnect();
            }
            long deadlineReconn = System.currentTimeMillis() + 3000;
            while (System.currentTimeMillis() < deadlineReconn && ClientManager.getInstance().getOnlineCount() < 10) {
                Thread.sleep(50);
            }
            assertEquals(10, ClientManager.getInstance().getOnlineCount(), "Sau khi reconnect, số online phải quay lại 10");
            System.out.println("[10 Clients] Phục hồi trạng thái Reconnect 5 máy thành công!");

        } finally {
            disconnectAll(clients);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // TEST CASE 5: 20 Clients — Tải tối đa LAN, Giám sát bộ nhớ (RAM/Heap), Throughput
    // ─────────────────────────────────────────────────────────────────────────────
    @Test
    public void test20ClientsStressAndResourcePerformance() throws Exception {
        System.out.println("\n========== TEST 20 CLIENTS: CHỊU TẢI LAN TỐI ĐA & GIÁM SÁT HIỆU NĂNG ==========");

        Runtime runtime = Runtime.getRuntime();
        runtime.gc();
        long memBefore = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);

        long startConn = System.currentTimeMillis();
        List<AgentCore> clients = createAndConnectClients(20, 55);
        long connTime = System.currentTimeMillis() - startConn;
        System.out.println("[20 Clients] Kết nối thành công 20 Clients trong: " + connTime + " ms");

        try {
            assertEquals(20, ClientManager.getInstance().getOnlineCount(), "Server phải quản lý chính xác 20 Clients online đồng thời");

            // 1. Throughput: 20 Chat messages gửi đồng thời từ 20 máy lên Server
            CountDownLatch chatLatch = new CountDownLatch(20);
            AtomicInteger chatsReceived = new AtomicInteger();
            ClientManager.ChatMessageListener chatListener = packet -> {
                chatsReceived.incrementAndGet();
                chatLatch.countDown();
            };
            ClientManager.getInstance().addChatMessageListener(chatListener);

            long startChat = System.currentTimeMillis();
            for (AgentCore client : clients) {
                new Thread(() -> {
                    client.sendChatMessage("Báo cáo ca trực từ máy " + client.getClientName());
                }).start();
            }

            boolean allChats = chatLatch.await(5, TimeUnit.SECONDS);
            long chatDuration = System.currentTimeMillis() - startChat;
            ClientManager.getInstance().removeChatMessageListener(chatListener);

            assertTrue(allChats, "Server phải tiếp nhận toàn bộ 20 tin nhắn chat đồng thời");
            assertEquals(20, chatsReceived.get());
            System.out.println("[20 Clients] Nhận 20 tin nhắn Chat đồng thời trong: " + chatDuration + " ms (Throughput: "
                    + String.format("%.1f", 20.0 / (chatDuration / 1000.0)) + " msg/sec)");

            // 2. Broadcast thông điệp khẩn cấp tới cả 20 máy
            String urgentBroadcast = "CẢNH BÁO: BẢO TRÌ HỆ THỐNG MẠNG LAN NỘI BỘ LÚC 17H30";
            long startBcast = System.currentTimeMillis();
            ClientManager.getInstance().broadcast(MessagePacket.createChat("SERVER", "ALL", urgentBroadcast));

            int broadcastReceivedCount = 0;
            for (AgentCore client : clients) {
                MessagePacket pkt = pollForPayload(clientPacketQueues.get(client.getClientName()), urgentBroadcast, 3);
                if (pkt != null && urgentBroadcast.equals(pkt.getPayload())) {
                    broadcastReceivedCount++;
                }
            }
            long bcastDuration = System.currentTimeMillis() - startBcast;
            assertEquals(20, broadcastReceivedCount, "Tất cả 20 clients phải nhận được thông điệp broadcast");
            System.out.println("[20 Clients] Broadcast gửi và nhận trên 20 máy hoàn tất trong: " + bcastDuration + " ms");

            // 3. Heartbeat đồng thời từ 20 clients
            for (AgentCore c : clients) {
                BlockingQueue<MessagePacket> q = clientPacketQueues.get(c.getClientName());
                if (q != null) q.clear();
            }

            long startHb = System.currentTimeMillis();
            CountDownLatch hbLatch = new CountDownLatch(20);
            for (AgentCore client : clients) {
                new Thread(() -> {
                    client.sendHeartbeat();
                    try {
                        MessagePacket pong = pollForPayload(clientPacketQueues.get(client.getClientName()), "PONG", 4);
                        if (pong != null) {
                            hbLatch.countDown();
                        }
                    } catch (InterruptedException ignored) {}
                }).start();
            }
            boolean allPongs = hbLatch.await(5, TimeUnit.SECONDS);
            long hbDuration = System.currentTimeMillis() - startHb;
            assertTrue(allPongs, "Toàn bộ 20 clients phải nhận được phản hồi PONG");
            System.out.println("[20 Clients] 20 Heartbeats xử lý đồng thời trong: " + hbDuration + " ms (TB: " + (hbDuration / 20.0) + " ms/client)");

            // 4. Đo lường mức sử dụng RAM / Heap
            runtime.gc();
            long memAfter = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
            long memDelta = Math.max(0, memAfter - memBefore);
            System.out.println("[20 Clients] Mức tiêu thụ bộ nhớ: Trước = " + memBefore + " MB | Sau = " + memAfter + " MB | Chênh lệch = " + memDelta + " MB");

        } finally {
            disconnectAll(clients);
            assertEquals(0, ClientManager.getInstance().getOnlineCount(), "Sau khi ngắt kết nối toàn bộ, danh sách online phải bằng 0");
            System.out.println("[20 Clients] Giải phóng tài nguyên và ngắt kết nối an toàn 20 Clients.");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // TEST CASE 6: Benchmark Tổng hợp (1, 2, 5, 10, 20 Clients) và Báo cáo Hiệu năng
    // ─────────────────────────────────────────────────────────────────────────────
    @Test
    public void testPerformanceBenchmarkSummary() throws Exception {
        System.out.println("\n==========================================================================");
        System.out.println("   BẢNG TỔNG HỢP KIỂM THỬ HIỆU NĂNG HỆ THỐNG MẠNG LAN (BENCHMARK SUMMARY)   ");
        System.out.println("==========================================================================");

        int[] clientScales = {1, 2, 5, 10, 20};
        System.out.printf("%-10s | %-16s | %-16s | %-16s | %-12s%n",
                "Quy mô", "Kết nối (ms)", "Heartbeat (ms)", "Lệnh CMD (ms)", "Tỷ lệ lỗi");
        System.out.println("-----------+------------------+------------------+------------------+-------------");

        for (int scale : clientScales) {
            long tConnStart = System.currentTimeMillis();
            List<AgentCore> testClients = createAndConnectClients(scale, 60 + scale);
            long tConn = System.currentTimeMillis() - tConnStart;

            for (AgentCore c : testClients) {
                BlockingQueue<MessagePacket> q = clientPacketQueues.get(c.getClientName());
                if (q != null) q.clear();
            }

            // Đo Heartbeat
            long tHbStart = System.currentTimeMillis();
            CountDownLatch hbLatch = new CountDownLatch(scale);
            for (AgentCore c : testClients) {
                new Thread(() -> {
                    c.sendHeartbeat();
                    try {
                        MessagePacket pong = pollForPayload(clientPacketQueues.get(c.getClientName()), "PONG", 3);
                        if (pong != null) hbLatch.countDown();
                    } catch (Exception ignored) {}
                }).start();
            }
            boolean hbOk = hbLatch.await(4, TimeUnit.SECONDS);
            long tHb = System.currentTimeMillis() - tHbStart;

            // Đo Command
            long tCmdStart = System.currentTimeMillis();
            CountDownLatch cmdLatch = new CountDownLatch(scale);
            ClientManager.CommandResponseListener cmdListener = (ip, type, msg) -> cmdLatch.countDown();
            ClientManager.getInstance().addCommandListener(cmdListener);

            for (AgentCore c : testClients) {
                ClientManager.getInstance().sendTo(c.getVirtualIp(),
                        MessagePacket.createCommand(c.getVirtualIp(), MessagePacket.CMD_LOCK));
            }
            boolean cmdOk = cmdLatch.await(4, TimeUnit.SECONDS);
            long tCmd = System.currentTimeMillis() - tCmdStart;
            ClientManager.getInstance().removeCommandListener(cmdListener);

            String errRate = (hbOk && cmdOk) ? "0.0%" : "Có lỗi";

            System.out.printf("%-10s | %-16s | %-16s | %-16s | %-12s%n",
                    scale + " Client",
                    tConn + " ms",
                    String.format("%.1f ms/tb", (double) tHb / scale),
                    String.format("%.1f ms/tb", (double) tCmd / scale),
                    errRate);

            disconnectAll(testClients);
            Thread.sleep(150);
        }
        System.out.println("==========================================================================\n");
    }
}
