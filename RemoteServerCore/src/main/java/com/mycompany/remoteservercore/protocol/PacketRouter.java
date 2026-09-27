package com.mycompany.remoteservercore.protocol;

import com.mycompany.remoteservercore.core.ClientHandler;
import com.mycompany.remoteservercore.model.MessagePacket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Bộ điều hướng và phân loại gói tin (Router/Dispatcher).
 * Nhận MessagePacket từ ClientHandler và chuyển tiếp đến các module xử lý tương ứng
 * theo từng loại gói tin (HEARTBEAT, CHAT, SCREENSHOT, COMMAND...).
 */
public class PacketRouter {

    @FunctionalInterface
    public interface PacketHandler {
        void handle(MessagePacket packet, ClientHandler sender);
    }

    private static final PacketRouter INSTANCE = new PacketRouter();

    private final Map<String, List<PacketHandler>> handlerMap = new ConcurrentHashMap<>();
    private PacketHandler defaultHandler;

    public PacketRouter() {
        // Khởi tạo handler mặc định cho việc ghi log kiểm thử
        this.defaultHandler = (packet, sender) -> {
            System.out.println("[PacketRouter - Default] Nhan goi tin [" + packet.getType() +
                    "] tu IP " + (sender != null ? sender.getClientIp() : "N/A") +
                    " voi noi dung: " + packet.getPayload());
        };
    }

    public static PacketRouter getInstance() {
        return INSTANCE;
    }

    /**
     * Đăng ký handler xử lý cho một loại gói tin cụ thể.
     *
     * @param packetType Loại gói tin (MessagePacket.TYPE_*)
     * @param handler Bộ xử lý thực thi
     */
    public void registerHandler(String packetType, PacketHandler handler) {
        if (packetType == null || handler == null) {
            return;
        }
        handlerMap.computeIfAbsent(packetType.toUpperCase(), k -> new CopyOnWriteArrayList<>()).add(handler);
        System.out.println("[PacketRouter] Da dang ky handler cho type: " + packetType.toUpperCase());
    }

    /**
     * Hủy đăng ký handler cho một loại gói tin.
     */
    public void unregisterHandler(String packetType, PacketHandler handler) {
        if (packetType == null || handler == null) {
            return;
        }
        List<PacketHandler> list = handlerMap.get(packetType.toUpperCase());
        if (list != null) {
            list.remove(handler);
        }
    }

    /**
     * Điều hướng gói tin đến các Handler đã đăng ký tương ứng với packet.getType().
     *
     * @param packet Gói tin nhận được
     * @param sender Luồng ClientHandler gửi gói tin
     */
    public void route(MessagePacket packet, ClientHandler sender) {
        if (packet == null || packet.getType() == null) {
            System.err.println("[PacketRouter] Bo qua goi tin null hoac thieu type!");
            return;
        }

        String type = packet.getType().toUpperCase();
        List<PacketHandler> handlers = handlerMap.get(type);

        if (handlers != null && !handlers.isEmpty()) {
            for (PacketHandler handler : handlers) {
                try {
                    handler.handle(packet, sender);
                } catch (Exception e) {
                    System.err.println("[PacketRouter] Loi khi thuc thi handler cho type " + type + ": " + e.getMessage());
                }
            }
        } else {
            // Chuyển sang handler mặc định nếu chưa đăng ký xử lý riêng
            if (defaultHandler != null) {
                defaultHandler.handle(packet, sender);
            }
        }
    }

    public void setDefaultHandler(PacketHandler defaultHandler) {
        this.defaultHandler = defaultHandler;
    }

    public void clearAllHandlers() {
        handlerMap.clear();
    }
}
