package com.mycompany.remoteservercore;

import com.mycompany.remoteservercore.core.ClientHandler;
import com.mycompany.remoteservercore.core.ClientManager;
import com.mycompany.remoteservercore.model.ClientInfo;
import com.mycompany.remoteservercore.model.MessagePacket;
import com.mycompany.remoteservercore.protocol.JsonUtils;
import com.mycompany.remoteservercore.protocol.PacketRouter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kiểm thử toàn diện cho Issue #35 / Issue 14: Chat 2 chiều Server ↔ Client.
 * Kiểm tra các tiêu chí:
 *  1. Server gửi message -> Client nhận message thành công.
 *  2. Client gửi message -> Server nhận message thành công.
 *  3. Hiển thị lịch sử chat & nhãn thời gian (Timestamp).
 *  4. Xử lý mất kết nối: Server phát hiện ngắt mạng, không gây lỗi hệ thống.
 *  5. Chat phát sóng (Broadcast) tới nhiều Client.
 */
public class ChatTwoWayTest {

    private static final int CHAT_TEST_PORT = 9997;
    private static ServerSocket serverSocket;
    private static Thread serverThread;
    private static volatile boolean running = true;

    @BeforeAll
    public static void setUp() throws Exception {
        PacketRouter router = PacketRouter.getInstance();

        // Đăng ký Router handler cho SYS_INFO
        router.registerHandler(MessagePacket.TYPE_SYS_INFO, (packet, sender) -> {
            ClientInfo info = JsonUtils.fromJson(packet.getPayload(), ClientInfo.class);
            if (info != null && sender != null) {
                info.setIpAddress(sender.getClientIp());
                ClientManager.getInstance().updateClientInfo(info);
            }
        });

        // Đăng ký Router handler cho CHAT 2 chiều
        router.registerHandler(MessagePacket.TYPE_CHAT, (packet, sender) -> {
            String clientIp = (sender != null) ? sender.getClientIp() : packet.getSender();
            if (packet.getSender() == null || packet.getSender().trim().isEmpty()) {
                packet.setSender(clientIp);
            }
            ClientManager.getInstance().notifyChatMessageReceived(packet);
        });

        // Khởi động ServerSocket chạy nền
        serverSocket = new ServerSocket(CHAT_TEST_PORT);
        serverThread = new Thread(() -> {
            while (running) {
                try {
                    Socket client = serverSocket.accept();
                    ClientHandler handler = new ClientHandler(client, router);
                    new Thread(handler).start();
                } catch (Exception ignored) {
                }
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();
    }

    @AfterAll
    public static void tearDown() throws Exception {
        running = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
        }
    }

    @Test
    public void testServerToClientChat() throws Exception {
        Socket clientSocket = new Socket("127.0.0.1", CHAT_TEST_PORT);
        clientSocket.setSoTimeout(3000);
        BufferedReader clientReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
        BufferedWriter clientWriter = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8));

        String localIp = clientSocket.getLocalAddress().getHostAddress();

        // Chờ ClientHandler đăng ký
        Thread.sleep(150);

        // Server gửi tin nhắn chat tới Client
        String testMessage = "Xin chào nhân sự máy " + localIp;
        MessagePacket serverChatPacket = MessagePacket.createChat("SERVER", localIp, testMessage);
        boolean sent = ClientManager.getInstance().sendTo(localIp, serverChatPacket);
        assertTrue(sent, "Server phải gửi được tin nhắn chat tới Client đang kết nối");

        // Client đọc gói tin từ Socket
        String receivedLine = clientReader.readLine();
        assertNotNull(receivedLine, "Client phải nhận được dữ liệu từ Server");

        MessagePacket receivedPacket = JsonUtils.fromJson(receivedLine, MessagePacket.class);
        assertNotNull(receivedPacket, "Client phải parse được JSON MessagePacket");
        assertEquals(MessagePacket.TYPE_CHAT, receivedPacket.getType(), "Loại gói tin phải là CHAT");
        assertEquals("SERVER", receivedPacket.getSender(), "Người gửi phải là SERVER");
        assertEquals(testMessage, receivedPacket.getPayload(), "Nội dung tin nhắn phải khớp");
        assertTrue(receivedPacket.getTimestamp() > 0, "Timestamp phải hợp lệ (> 0)");

        clientSocket.close();
    }

    @Test
    public void testClientToServerChat() throws Exception {
        Socket clientSocket = new Socket("127.0.0.1", CHAT_TEST_PORT);
        BufferedWriter clientWriter = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8));

        BlockingQueue<MessagePacket> serverReceivedQueue = new LinkedBlockingQueue<>();
        ClientManager.ChatMessageListener chatListener = serverReceivedQueue::offer;

        ClientManager.getInstance().addChatMessageListener(chatListener);

        try {
            // Client gửi tin nhắn Chat lên Server
            String clientMsg = "Báo cáo: Đã hoàn thành công việc hôm nay!";
            MessagePacket clientPacket = MessagePacket.createChat("CLIENT-TEST-01", "SERVER", clientMsg);

            clientWriter.write(JsonUtils.toJson(clientPacket));
            clientWriter.newLine();
            clientWriter.flush();

            // Chờ Server nhận tin nhắn qua ChatListener
            MessagePacket serverPacket = serverReceivedQueue.poll(3, TimeUnit.SECONDS);
            assertNotNull(serverPacket, "Server phải nhận được tin nhắn CHAT từ Client qua listener");
            assertEquals(MessagePacket.TYPE_CHAT, serverPacket.getType());
            assertEquals("CLIENT-TEST-01", serverPacket.getSender());
            assertEquals(clientMsg, serverPacket.getPayload());
            assertTrue(serverPacket.getTimestamp() > 0, "Timestamp phải có giá trị hợp lệ");

        } finally {
            ClientManager.getInstance().removeChatMessageListener(chatListener);
            clientSocket.close();
        }
    }

    @Test
    public void testTimestampFormatting() {
        long now = System.currentTimeMillis();
        MessagePacket packet = MessagePacket.createChat("SENDER", "TARGET", "Hello", now);
        assertEquals(now, packet.getTimestamp());

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        String formattedTime = formatter.format(Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()));
        assertNotNull(formattedTime);
        assertTrue(formattedTime.matches("\\d{2}:\\d{2}:\\d{2}"), "Timestamp định dạng đúng mẫu HH:mm:ss");
    }

    @Test
    public void testDisconnectHandling() throws Exception {
        Socket clientSocket = new Socket("127.0.0.1", CHAT_TEST_PORT);
        String localIp = clientSocket.getLocalAddress().getHostAddress();

        CountDownLatch disconnectLatch = new CountDownLatch(1);
        ClientManager.ClientEventListener disconnectListener = new ClientManager.ClientEventListener() {
            @Override public void onClientConnected(ClientInfo clientInfo) {}
            @Override public void onClientUpdated(ClientInfo clientInfo) {}
            @Override public void onClientDisconnected(String clientIp) {
                if (localIp.equals(clientIp)) {
                    disconnectLatch.countDown();
                }
            }
        };

        ClientManager.getInstance().addListener(disconnectListener);

        try {
            Thread.sleep(150);

            // Client ngắt kết nối đột ngột
            clientSocket.close();

            // Server phải phát hiện và gọi onClientDisconnected
            boolean disconnected = disconnectLatch.await(3, TimeUnit.SECONDS);
            assertTrue(disconnected, "Server phải kích hoạt sự kiện onClientDisconnected khi Client mất kết nối");

            // Server cố gửi tin nhắn tới Client đã mất kết nối -> sendTo phải trả về false và không gây ngoại lệ
            MessagePacket packet = MessagePacket.createChat("SERVER", localIp, "Bạn còn đó không?");
            boolean sendResult = ClientManager.getInstance().sendTo(localIp, packet);
            assertFalse(sendResult, "Gửi tin nhắn tới máy đã ngắt kết nối phải trả về false");

        } finally {
            ClientManager.getInstance().removeListener(disconnectListener);
        }
    }

    @Test
    public void testBroadcastChat() throws Exception {
        Socket client = new Socket("127.0.0.1", CHAT_TEST_PORT);
        client.setSoTimeout(3000);
        BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));

        Thread.sleep(200);

        String broadcastMsg = "Thông báo: Họp toàn công ty lúc 14h!";
        MessagePacket broadcastPacket = MessagePacket.createChat("SERVER", "ALL", broadcastMsg);
        ClientManager.getInstance().broadcast(broadcastPacket);

        String line = reader.readLine();
        assertNotNull(line, "Client phải nhận được tin broadcast");

        MessagePacket packet = JsonUtils.fromJson(line, MessagePacket.class);
        assertNotNull(packet, "Phải parse được JSON gói tin broadcast");
        assertEquals(MessagePacket.TYPE_CHAT, packet.getType());
        assertEquals("ALL", packet.getTarget());
        assertEquals(broadcastMsg, packet.getPayload());

        client.close();
    }
}
