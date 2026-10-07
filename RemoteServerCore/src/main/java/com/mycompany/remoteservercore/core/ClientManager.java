package com.mycompany.remoteservercore.core;

import com.mycompany.remoteservercore.model.ClientInfo;
import com.mycompany.remoteservercore.model.MessagePacket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Quản lý danh sách các máy nhân sự (Client Agent) đang kết nối tới Server.
 * Cung cấp chức năng gửi tin nhắn/lệnh riêng lẻ hoặc phát sóng (Broadcast) tới toàn mạng LAN.
 */
public class ClientManager {

    public interface ClientEventListener {
        void onClientConnected(ClientInfo clientInfo);
        void onClientDisconnected(String clientIp);
        void onClientUpdated(ClientInfo clientInfo);
    }

    public interface CommandResponseListener {
        void onCommandResponse(String clientIp, String type, String message);
    }

    public interface ChatMessageListener {
        void onChatMessageReceived(MessagePacket packet);
    }

    public interface ScreenshotListener {
        void onScreenshotReceived(String clientIp, String base64Image);
    }

    private static final ClientManager INSTANCE = new ClientManager();

    private final Map<String, ClientHandler> activeHandlers = new ConcurrentHashMap<>();
    private final Map<String, ClientInfo> clientInfoMap = new ConcurrentHashMap<>();
    private final List<ClientEventListener> listeners = new CopyOnWriteArrayList<>();
    private final List<CommandResponseListener> commandListeners = new CopyOnWriteArrayList<>();
    private final List<ChatMessageListener> chatListeners = new CopyOnWriteArrayList<>();
    private final List<ScreenshotListener> screenshotListeners = new CopyOnWriteArrayList<>();

    private ClientManager() {
    }

    public static ClientManager getInstance() {
        return INSTANCE;
    }

    public static synchronized void addClient(ClientHandler client) {
        if (client != null) {
            getInstance().registerClient(client);
        }
    }

    public static synchronized void removeClient(ClientHandler client) {
        if (client != null) {
            getInstance().unregisterClient(client);
        }
    }

    public static synchronized void broadcast(String message) {
        for (ClientHandler handler : getInstance().activeHandlers.values()) {
            if (handler.isRunning()) {
                handler.sendMessage(message);
            }
        }
    }

    /**
     * Đăng ký một Client vừa kết nối thành công.
     */
    public void registerClient(ClientHandler handler) {
        if (handler == null || handler.getClientIp() == null) {
            return;
        }
        String ip = handler.getClientIp();
        activeHandlers.put(ip, handler);

        ClientInfo info = clientInfoMap.computeIfAbsent(ip, k -> new ClientInfo(ip, "Unknown-Host", "N/A", "Unknown-OS"));
        info.setStatus(ClientInfo.STATUS_ONLINE);
        info.updatePing();

        for (ClientEventListener listener : listeners) {
            try {
                listener.onClientConnected(info);
            } catch (Exception e) {
                System.err.println("[ClientManager] Loi callback listener: " + e.getMessage());
            }
        }
        com.mycompany.remoteservercore.notification.NotificationService.getInstance()
                .notifyClientOnline(ip, info.getHostName());
        System.out.println("[ClientManager] Da them Client vao danh sach quan ly: " + ip + " (Tong so: " + activeHandlers.size() + ")");
    }

    /**
     * Hủy đăng ký ClientHandler. Chỉ xóa khi handler hiện tại chính là handler ngắt kết nối.
     */
    public void unregisterClient(ClientHandler handler) {
        if (handler == null) {
            return;
        }
        String ip = handler.getClientIp();
        boolean removed = activeHandlers.remove(ip, handler);
        if (removed) {
            ClientInfo info = clientInfoMap.get(ip);
            if (info != null) {
                info.setStatus(ClientInfo.STATUS_OFFLINE);
            }
            for (ClientEventListener listener : listeners) {
                try {
                    listener.onClientDisconnected(ip);
                } catch (Exception e) {
                    System.err.println("[ClientManager] Loi callback listener: " + e.getMessage());
                }
            }
            com.mycompany.remoteservercore.notification.NotificationService.getInstance()
                    .notifyClientOffline(ip, (info != null) ? info.getHostName() : ip);
            System.out.println("[ClientManager] Da xoa Client khoi danh sach: " + ip + " (Con lai: " + activeHandlers.size() + ")");
        }
    }

    public void unregisterClient(String clientIp) {
        if (clientIp == null) {
            return;
        }
        activeHandlers.remove(clientIp);
        ClientInfo info = clientInfoMap.get(clientIp);
        if (info != null) {
            info.setStatus(ClientInfo.STATUS_OFFLINE);
        }
        for (ClientEventListener listener : listeners) {
            try {
                listener.onClientDisconnected(clientIp);
            } catch (Exception e) {
                System.err.println("[ClientManager] Loi callback listener: " + e.getMessage());
            }
        }
    }

    public void updateClientInfo(ClientInfo info) {
        if (info == null || info.getIpAddress() == null) {
            return;
        }
        info.updatePing();
        clientInfoMap.put(info.getIpAddress(), info);

        for (ClientEventListener listener : listeners) {
            try {
                listener.onClientUpdated(info);
            } catch (Exception e) {
                System.err.println("[ClientManager] Loi callback listener: " + e.getMessage());
            }
        }
    }

    public boolean sendTo(String ip, MessagePacket packet) {
        ClientHandler handler = activeHandlers.get(ip);
        if (handler != null && handler.isRunning()) {
            return handler.sendPacket(packet);
        }
        System.err.println("[ClientManager] Khong the gui den " + ip + " (May khong online hoac mat ket noi)");
        return false;
    }

    public void broadcast(MessagePacket packet) {
        for (ClientHandler handler : activeHandlers.values()) {
            if (handler.isRunning()) {
                handler.sendPacket(packet);
            }
        }
    }

    public List<ClientInfo> getAllClients() {
        return new ArrayList<>(clientInfoMap.values());
    }

    public ClientInfo getClientInfo(String ip) {
        return clientInfoMap.get(ip);
    }

    public int getOnlineCount() {
        return activeHandlers.size();
    }

    public void addListener(ClientEventListener listener) {
        listeners.add(listener);
    }

    public void removeListener(ClientEventListener listener) {
        listeners.remove(listener);
    }

    public void addCommandListener(CommandResponseListener listener) {
        commandListeners.add(listener);
    }

    public void removeCommandListener(CommandResponseListener listener) {
        commandListeners.remove(listener);
    }

    public void notifyCommandResponse(String clientIp, String type, String message) {
        for (CommandResponseListener listener : commandListeners) {
            try {
                listener.onCommandResponse(clientIp, type, message);
            } catch (Exception e) {
                System.err.println("[ClientManager] Loi callback command listener: " + e.getMessage());
            }
        }
    }

    public void addChatMessageListener(ChatMessageListener listener) {
        chatListeners.add(listener);
    }

    public void removeChatMessageListener(ChatMessageListener listener) {
        chatListeners.remove(listener);
    }

    public void notifyChatMessageReceived(MessagePacket packet) {
        for (ChatMessageListener listener : chatListeners) {
            try {
                listener.onChatMessageReceived(packet);
            } catch (Exception e) {
                System.err.println("[ClientManager] Loi callback chat listener: " + e.getMessage());
            }
        }
    }

    public void addScreenshotListener(ScreenshotListener listener) {
        screenshotListeners.add(listener);
    }

    public void removeScreenshotListener(ScreenshotListener listener) {
        screenshotListeners.remove(listener);
    }

    public void notifyScreenshotReceived(String clientIp, String base64Image) {
        for (ScreenshotListener listener : screenshotListeners) {
            try {
                listener.onScreenshotReceived(clientIp, base64Image);
            } catch (Exception e) {
                System.err.println("[ClientManager] Loi callback screenshot listener: " + e.getMessage());
            }
        }
    }
}
