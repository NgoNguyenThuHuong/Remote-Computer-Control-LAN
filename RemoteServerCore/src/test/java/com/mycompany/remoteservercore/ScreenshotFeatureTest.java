package com.mycompany.remoteservercore;

import com.mycompany.remoteservercore.core.ClientHandler;
import com.mycompany.remoteservercore.core.ClientManager;
import com.mycompany.remoteservercore.features.ScreenCapturer;
import com.mycompany.remoteservercore.model.ClientInfo;
import com.mycompany.remoteservercore.model.MessagePacket;
import com.mycompany.remoteservercore.protocol.JsonUtils;
import com.mycompany.remoteservercore.protocol.PacketRouter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
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
 * Kiểm thử toàn diện cho Issue #38 (Issue 17): Screenshot từ Client.
 * Luồng kiểm thử:
 *   1. Chức năng chụp màn hình & chuyển đổi Base64 của ScreenCapturer.
 *   2. Server gửi SCREENSHOT_REQ qua TCP Socket.
 *   3. Client tiếp nhận lệnh -> chụp màn hình -> gửi SCREENSHOT_RES qua Socket.
 *   4. Server nhận phản hồi -> giải mã Base64 -> xác nhận ảnh hợp lệ.
 *   5. Xử lý ngoại lệ truyền dữ liệu và định dạng ảnh hỏng.
 */
public class ScreenshotFeatureTest {

    private static final int SCREENSHOT_PORT = 9996;
    private static ServerSocket serverSocket;
    private static Thread serverThread;
    private static volatile boolean running = true;

    @BeforeAll
    public static void setUp() throws Exception {
        PacketRouter router = PacketRouter.getInstance();

        // 1. Router handler cho SYS_INFO
        router.registerHandler(MessagePacket.TYPE_SYS_INFO, (packet, sender) -> {
            ClientInfo info = JsonUtils.fromJson(packet.getPayload(), ClientInfo.class);
            if (info != null && sender != null) {
                info.setIpAddress(sender.getClientIp());
                ClientManager.getInstance().updateClientInfo(info);
            }
        });

        // 2. Router handler cho SCREENSHOT_RES
        router.registerHandler(MessagePacket.TYPE_SCREENSHOT_RES, (packet, sender) -> {
            String ip = (sender != null) ? sender.getClientIp() : packet.getSender();
            ClientManager.getInstance().notifyScreenshotReceived(ip, packet.getPayload());
        });

        // 3. Khởi chạy ServerSocket nền cho kiểm thử
        serverSocket = new ServerSocket(SCREENSHOT_PORT);
        serverThread = new Thread(() -> {
            while (running) {
                try {
                    Socket socket = serverSocket.accept();
                    ClientHandler handler = new ClientHandler(socket, router);
                    new Thread(handler).start();
                } catch (Exception ignored) {}
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
    public void testScreenCaptureAndBase64Conversion() throws Exception {
        // Kiểm tra khả năng chụp ảnh và chuyển đổi Base64 của ScreenCapturer
        BufferedImage image = ScreenCapturer.captureScreen();
        assertNotNull(image, "Ảnh chụp màn hình không được null");
        assertTrue(image.getWidth() > 0, "Chiều rộng ảnh phải > 0");
        assertTrue(image.getHeight() > 0, "Chiều cao ảnh phải > 0");

        String base64 = ScreenCapturer.captureScreenAsBase64();
        assertNotNull(base64, "Chuỗi Base64 ảnh không được null");
        assertFalse(base64.trim().isEmpty(), "Chuỗi Base64 ảnh không được rỗng");

        BufferedImage decoded = ScreenCapturer.base64ToImage(base64);
        assertNotNull(decoded, "Ảnh sau khi giải mã Base64 không được null");
        assertEquals(image.getWidth(), decoded.getWidth(), "Chiều rộng ảnh giải mã phải khớp");
        assertEquals(image.getHeight(), decoded.getHeight(), "Chiều cao ảnh giải mã phải khớp");
    }

    @Test
    public void testFullScreenshotRequestResponseFlow() throws Exception {
        BlockingQueue<String> receivedImageQueue = new LinkedBlockingQueue<>();
        ClientManager.ScreenshotListener listener = (clientIp, base64Image) -> {
            receivedImageQueue.offer(base64Image);
        };
        ClientManager.getInstance().addScreenshotListener(listener);

        try (Socket clientSocket = new Socket("127.0.0.1", SCREENSHOT_PORT);
             BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8))) {

            clientSocket.setSoTimeout(5000);
            Thread.sleep(150);
            String localIp = clientSocket.getLocalAddress().getHostAddress();

            // 1. Client gửi SYS_INFO
            ClientInfo clientInfo = new ClientInfo(localIp, "CLIENT-MAY-TEST", "huong", "Windows 11");
            MessagePacket sysInfoPacket = new MessagePacket(MessagePacket.TYPE_SYS_INFO, "CLIENT-MAY-TEST", "SERVER", JsonUtils.toJson(clientInfo));
            writer.write(JsonUtils.toJson(sysInfoPacket));
            writer.newLine();
            writer.flush();
            Thread.sleep(100);

            // 2. Server gửi yêu cầu SCREENSHOT_REQ tới Client
            MessagePacket reqPacket = MessagePacket.createScreenshotRequest(localIp);
            boolean sent = ClientManager.getInstance().sendTo(localIp, reqPacket);
            assertTrue(sent, "Server phải gửi thành công gói tin SCREENSHOT_REQ tới Client");

            // 3. Phía Client nhận gói tin SCREENSHOT_REQ
            String reqLine = reader.readLine();
            assertNotNull(reqLine, "Client phải nhận được yêu cầu chụp màn hình từ Server");
            MessagePacket receivedReq = JsonUtils.fromJson(reqLine, MessagePacket.class);
            assertEquals(MessagePacket.TYPE_SCREENSHOT_REQ, receivedReq.getType(), "Loại gói tin phải là SCREENSHOT_REQ");

            // 4. Client chụp màn hình và gửi phản hồi SCREENSHOT_RES về Server
            String base64Image = ScreenCapturer.captureScreenAsBase64();
            MessagePacket resPacket = MessagePacket.createScreenshotResponse("CLIENT-MAY-TEST", base64Image);
            writer.write(JsonUtils.toJson(resPacket));
            writer.newLine();
            writer.flush();

            // 5. Server nhận phản hồi qua ScreenshotListener
            String serverReceivedBase64 = receivedImageQueue.poll(5, TimeUnit.SECONDS);
            assertNotNull(serverReceivedBase64, "Server phải nhận được ảnh màn hình từ Client");

            BufferedImage serverImage = ScreenCapturer.base64ToImage(serverReceivedBase64);
            assertNotNull(serverImage, "Server phải giải mã được ảnh chụp màn hình");
            assertTrue(serverImage.getWidth() > 0, "Ảnh nhận được tại Server phải có chiều rộng > 0");
            assertTrue(serverImage.getHeight() > 0, "Ảnh nhận được tại Server phải có chiều cao > 0");

        } finally {
            ClientManager.getInstance().removeScreenshotListener(listener);
            Thread.sleep(100);
        }
    }

    @Test
    public void testCorruptedBase64Handling() {
        assertThrows(Exception.class, () -> {
            ScreenCapturer.base64ToImage("!!!INVALID_BASE64_STRING_DATA!!!");
        }, "Giải mã Base64 không hợp lệ phải ném ra Exception");

        assertThrows(Exception.class, () -> {
            ScreenCapturer.base64ToImage("");
        }, "Dữ liệu Base64 rỗng phải ném ra Exception");
    }
}
