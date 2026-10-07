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

    private static final ClientManager INSTANCE = new ClientManager();

    private final Map<String, ClientHandler> activeHandlers = new ConcurrentHashMap<>();
    private final Map<String, ClientInfo> clientInfoMap = new ConcurrentHashMap<>();
    private final List<ClientEventListener> listeners = new CopyOnWriteArrayList<>();
    private final List<CommandResponseListener> commandListeners = new CopyOnWriteArrayList<>();

    private ClientManager() {
    }

    public static ClientManager getInstance() {
        return INSTANCE;
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
        System.out.println("[ClientManager] Da them Client vao danh sach quan ly: " + ip + " (Tong so: " + activeHandlers.size() + ")");
    }

    /**
     * Hủy đăng ký Client khi ngắt kết nối.
     */
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
        System.out.println("[ClientManager] Da xoa Client khoi danh sach: " + clientIp + " (Con lai: " + activeHandlers.size() + ")");
    }

    /**
     * Cập nhật thông tin hệ thống của máy Client (ví dụ từ gói tin SYS_INFO hoặc HEARTBEAT).
     */
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

    /**
     * Gửi gói tin đến một máy Client chỉ định qua địa chỉ IP.
     */
    public boolean sendTo(String ip, MessagePacket packet) {
        ClientHandler handler = activeHandlers.get(ip);
        if (handler != null && handler.isRunning()) {
            return handler.sendPacket(packet);
        }
        System.err.println("[ClientManager] Khong the gui den " + ip + " (May khong online hoac mat ket noi)");
        return false;
    }

    /**
     * Phát sóng gói tin tới toàn bộ các máy Client đang online.
     */
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
}
