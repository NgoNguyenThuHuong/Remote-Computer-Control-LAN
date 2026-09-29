package com.mycompany.remoteservercore.core;

import com.mycompany.remoteservercore.model.ClientInfo;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Quản lý danh sách Client đang kết nối đến Server (in-memory).
 *
 * <p>Thread-safe: sử dụng ConcurrentHashMap để tránh race condition
 * khi nhiều ClientHandler cùng thêm/xóa client.
 *
 * <p>Hỗ trợ listener để Dashboard tự động cập nhật khi danh sách thay đổi.
 */
public class ClientManager {

    // ─── Singleton ───────────────────────────────────────────────────────────
    private static final ClientManager INSTANCE = new ClientManager();
    public static ClientManager getInstance() { return INSTANCE; }
    private ClientManager() {}

    // ─── Danh sách client: key = clientId (IP:port) ──────────────────────────
    private final ConcurrentHashMap<String, ClientInfo> clients = new ConcurrentHashMap<>();

    // ─── Listener interface cho Dashboard ────────────────────────────────────
    /**
     * Giao diện để Dashboard đăng ký nhận thông báo khi danh sách client thay đổi.
     */
    public interface ClientChangeListener {
        /** Gọi khi có client mới kết nối */
        void onClientConnected(ClientInfo client);
        /** Gọi khi client ngắt kết nối */
        void onClientDisconnected(ClientInfo client);
        /** Gọi khi heartbeat của client được cập nhật */
        void onClientHeartbeat(ClientInfo client);
    }

    // ─── Danh sách listener (CopyOnWriteArrayList — thread-safe) ─────────────
    private final List<ClientChangeListener> listeners = new CopyOnWriteArrayList<>();

    /** Đăng ký listener (Dashboard gọi khi khởi tạo) */
    public void addListener(ClientChangeListener listener) {
        listeners.add(listener);
    }

    /** Hủy đăng ký listener (Dashboard gọi khi đóng) */
    public void removeListener(ClientChangeListener listener) {
        listeners.remove(listener);
    }

    // ─── Thêm client mới kết nối ─────────────────────────────────────────────
    /**
     * Đăng ký client mới. Gọi bởi ClientHandler khi nhận kết nối.
     *
     * @param id       unique key (vd: "192.168.1.5:54321")
     * @param hostname tên máy client
     * @param ip       địa chỉ IP
     * @return ClientInfo vừa tạo
     */
    public ClientInfo addClient(String id, String hostname, String ip) {
        ClientInfo info = new ClientInfo(id, hostname, ip);
        clients.put(id, info);
        System.out.println("[ClientManager] Client kết nối: " + info);
        // Thông báo cho tất cả listener (Dashboard)
        for (ClientChangeListener l : listeners) {
            l.onClientConnected(info);
        }
        return info;
    }

    // ─── Xóa client khi disconnect ───────────────────────────────────────────
    /**
     * Đánh dấu client offline (không xóa khỏi danh sách để còn xem lịch sử).
     *
     * @param id clientId
     */
    public void removeClient(String id) {
        ClientInfo info = clients.get(id);
        if (info != null) {
            info.markOffline();
            System.out.println("[ClientManager] Client ngắt kết nối: " + info);
            for (ClientChangeListener l : listeners) {
                l.onClientDisconnected(info);
            }
        }
    }

    // ─── Cập nhật heartbeat ──────────────────────────────────────────────────
    /**
     * Cập nhật heartbeat cho client. Gọi khi nhận gói tin heartbeat.
     *
     * @param id clientId
     */
    public void updateHeartbeat(String id) {
        ClientInfo info = clients.get(id);
        if (info != null) {
            info.updateHeartbeat();
            for (ClientChangeListener l : listeners) {
                l.onClientHeartbeat(info);
            }
        }
    }

    // ─── Truy vấn ────────────────────────────────────────────────────────────

    /** Lấy tất cả client (cả online lẫn offline) */
    public List<ClientInfo> getAllClients() {
        return new ArrayList<>(clients.values());
    }

    /** Lấy chỉ các client đang ONLINE */
    public List<ClientInfo> getOnlineClients() {
        List<ClientInfo> online = new ArrayList<>();
        for (ClientInfo c : clients.values()) {
            if (c.getStatus() == ClientInfo.Status.ONLINE) {
                online.add(c);
            }
        }
        return online;
    }

    /** Lấy client theo id */
    public ClientInfo getClient(String id) {
        return clients.get(id);
    }

    /** Số lượng client đang online */
    public int getOnlineCount() {
        return (int) clients.values().stream()
                .filter(c -> c.getStatus() == ClientInfo.Status.ONLINE)
                .count();
    }

    /** Tổng số client đã từng kết nối */
    public int getTotalCount() {
        return clients.size();
    }
}
