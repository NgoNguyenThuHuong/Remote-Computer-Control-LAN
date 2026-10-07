package com.mycompany.remoteservercore;

import com.mycompany.remoteservercore.core.ClientHandler;
import com.mycompany.remoteservercore.core.ClientManager;
import com.mycompany.remoteservercore.features.SystemControl;
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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kiểm thử toàn diện cho Issue #34: Gửi lệnh điều khiển từ Server đến Client.
 * Luồng kiểm tra:
 *   Server UI -> Chọn Client -> Chọn Command (LOCK, LOGOUT, RESTART)
 *   -> Tạo JSON -> TCP Socket -> Client nhận -> Thực thi -> Trả kết quả (ACK/ERROR)
 *   -> Server nhận kết quả và hiển thị.
 */
public class ServerSendCommandTest {

    private static final int TEST_PORT = 9998;
    private static ServerSocket serverSocket;
    private static Thread serverThread;
    private static volatile boolean running = true;

    @BeforeAll
    public static void setUp() throws Exception {
        SystemControl.setSafeMode(true);

        // Đăng ký các PacketHandler trên Server
        PacketRouter router = PacketRouter.getInstance();
        router.registerHandler(MessagePacket.TYPE_SYS_INFO, (packet, sender) -> {
            ClientInfo info = JsonUtils.fromJson(packet.getPayload(), ClientInfo.class);
            if (info != null && sender != null) {
                info.setIpAddress(sender.getClientIp());
                ClientManager.getInstance().updateClientInfo(info);
            }
        });

        router.registerHandler(MessagePacket.TYPE_ACK, (packet, sender) -> {
            String ip = (sender != null) ? sender.getClientIp() : packet.getSender();
            ClientManager.getInstance().notifyCommandResponse(ip, MessagePacket.TYPE_ACK, packet.getPayload());
        });

        router.registerHandler(MessagePacket.TYPE_ERROR, (packet, sender) -> {
            String ip = (sender != null) ? sender.getClientIp() : packet.getSender();
            ClientManager.getInstance().notifyCommandResponse(ip, MessagePacket.TYPE_ERROR, packet.getPayload());
        });

        // Khởi động ServerSocket test trên cổng riêng
        serverSocket = new ServerSocket(TEST_PORT);
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
    public void testServerSendCommandLockSuccess() throws Exception {
        verifyCommandExecution(MessagePacket.CMD_LOCK, MessagePacket.TYPE_ACK, true);
    }

    @Test
    public void testServerSendCommandLogoutSuccess() throws Exception {
        verifyCommandExecution(MessagePacket.CMD_LOGOUT, MessagePacket.TYPE_ACK, true);
    }

    @Test
    public void testServerSendCommandRestartSuccess() throws Exception {
        verifyCommandExecution(MessagePacket.CMD_RESTART, MessagePacket.TYPE_ACK, true);
    }

    @Test
    public void testServerSendInvalidCommandError() throws Exception {
        verifyCommandExecution("INVALID_CMD_TEST", MessagePacket.TYPE_ERROR, false);
    }

    private void verifyCommandExecution(String commandToSend, String expectedResponseType, boolean isSuccessExpected) throws Exception {
        BlockingQueue<String[]> responseQueue = new LinkedBlockingQueue<>();

        ClientManager.CommandResponseListener listener = (clientIp, type, message) -> {
            responseQueue.offer(new String[]{type, message});
        };
        ClientManager.getInstance().addCommandListener(listener);

        try (Socket clientSocket = new Socket("127.0.0.1", TEST_PORT);
             BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8))) {

            String localIp = clientSocket.getLocalAddress().getHostAddress();

            // 1. Client gửi SYS_INFO
            ClientInfo clientInfo = new ClientInfo(localIp, "TEST-HOST", "testuser", "Windows 11");
            MessagePacket sysInfoPacket = new MessagePacket(MessagePacket.TYPE_SYS_INFO, "TEST-HOST", "SERVER", JsonUtils.toJson(clientInfo));
            writer.write(JsonUtils.toJson(sysInfoPacket));
            writer.newLine();
            writer.flush();

            Thread.sleep(100);

            // 2. Server gửi lệnh Command tới Client
            MessagePacket commandPacket = MessagePacket.createCommand(localIp, commandToSend);
            boolean sent = ClientManager.getInstance().sendTo(localIp, commandPacket);
            assertTrue(sent, "Server phải gửi được lệnh tới Client qua Socket");

            // 3. Client đọc lệnh và thực thi
            String line = reader.readLine();
            assertNotNull(line, "Client phải nhận được gói tin từ Server");

            MessagePacket receivedPacket = JsonUtils.fromJson(line, MessagePacket.class);
            assertEquals(MessagePacket.TYPE_COMMAND, receivedPacket.getType());
            assertEquals(commandToSend, receivedPacket.getPayload());

            // 4. Client xử lý theo logic Issue #33
            if (!SystemControl.isValidCommand(receivedPacket.getPayload())) {
                MessagePacket errPacket = MessagePacket.createError("TEST-HOST", "SERVER", "Lệnh không hợp lệ: " + receivedPacket.getPayload());
                writer.write(JsonUtils.toJson(errPacket));
                writer.newLine();
                writer.flush();
            } else {
                String result = SystemControl.execute(receivedPacket.getPayload());
                MessagePacket ackPacket = MessagePacket.createAck("TEST-HOST", "SERVER", result);
                writer.write(JsonUtils.toJson(ackPacket));
                writer.newLine();
                writer.flush();
            }

            // 5. Server nhận phản hồi qua CommandResponseListener (Issue #34)
            String[] response = responseQueue.poll(3, TimeUnit.SECONDS);
            assertNotNull(response, "Server phải nhận được phản hồi kết quả từ Client trong thời gian chờ");
            assertEquals(expectedResponseType, response[0], "Loại phản hồi phải khớp (" + expectedResponseType + ")");

            if (isSuccessExpected) {
                assertTrue(response[1].contains(commandToSend) || response[1].contains("thành công"),
                        "Nội dung phản hồi thành công phải chứa thông báo hợp lệ");
            } else {
                assertTrue(response[1].contains("không hợp lệ") || response[1].contains("Lỗi"),
                        "Nội dung phản hồi lỗi phải thông báo lệnh không hợp lệ");
            }

        } finally {
            ClientManager.getInstance().removeCommandListener(listener);
        }
    }
}
