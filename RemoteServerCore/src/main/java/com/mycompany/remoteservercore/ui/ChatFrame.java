package com.mycompany.remoteservercore.ui;

import com.mycompany.remoteservercore.core.ClientManager;
import com.mycompany.remoteservercore.model.ClientInfo;
import com.mycompany.remoteservercore.model.MessagePacket;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Giao diện Chat 2 chiều giữa Server và Client (Issue #35 / Issue 14).
 * Cung cấp:
 *  - Chọn Client cụ thể hoặc Broadcast cho toàn bộ mạng LAN.
 *  - Gửi tin nhắn từ Server -> Client qua TCP Socket.
 *  - Nhận tin nhắn từ Client -> Server theo thời gian thực.
 *  - Hiển thị lịch sử chat trực quan kèm nhãn thời gian (Timestamp HH:mm:ss).
 *  - Xử lý mất kết nối: hiển thị cảnh báo ngắt mạng, khóa nút gửi và thông báo khi kết nối lại.
 */
public class ChatFrame extends JFrame implements ClientManager.ChatMessageListener, ClientManager.ClientEventListener {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final String TARGET_ALL = "Tất cả các máy (Broadcast)";

    private static ChatFrame instance;

    private JComboBox<String> clientComboBox;
    private JLabel statusLabel;
    private JTextPane chatHistoryPane;
    private HTMLEditorKit htmlKit;
    private HTMLDocument htmlDoc;
    private JTextField inputField;
    private JButton btnSend;
    private JButton btnClear;

    private String currentTargetIp = null; // null biểu thị Broadcast

    public static synchronized ChatFrame getInstance(String preselectedIp) {
        if (instance == null || !instance.isDisplayable()) {
            instance = new ChatFrame(preselectedIp);
        } else {
            if (preselectedIp != null) {
                instance.selectClient(preselectedIp);
            }
        }
        return instance;
    }

    public ChatFrame() {
        this(null);
    }

    public ChatFrame(String preselectedIp) {
        this.currentTargetIp = preselectedIp;
        initComponents();
        populateClientList(preselectedIp);

        // Đăng ký nhận sự kiện tin nhắn và sự kiện kết nối client
        ClientManager.getInstance().addChatMessageListener(this);
        ClientManager.getInstance().addListener(this);

        appendSystemMessage("Cửa sổ Chat 2 chiều Server ↔ Client đã sẵn sàng.");
        updateConnectionStatus();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                ClientManager.getInstance().removeChatMessageListener(ChatFrame.this);
                ClientManager.getInstance().removeListener(ChatFrame.this);
                instance = null;
            }
        });
    }

    private void initComponents() {
        setTitle("💬 Chat 2 Chiều — Server ↔ Client");
        setSize(650, 560);
        setMinimumSize(new Dimension(500, 420));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(new Color(30, 30, 46));
        setContentPane(root);

        root.add(buildHeaderPanel(), BorderLayout.NORTH);
        root.add(buildChatHistoryPanel(), BorderLayout.CENTER);
        root.add(buildInputPanel(), BorderLayout.SOUTH);
    }

    private JPanel buildHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout(10, 8));
        header.setBackground(new Color(49, 50, 68));
        header.setBorder(new EmptyBorder(12, 16, 12, 16));

        // Phía trái: Tiêu đề và chọn mục tiêu
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        leftPanel.setOpaque(false);

        JLabel lblTarget = new JLabel("Gửi tới:");
        lblTarget.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblTarget.setForeground(new Color(203, 166, 247));

        clientComboBox = new JComboBox<>();
        clientComboBox.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        clientComboBox.setBackground(new Color(69, 71, 90));
        clientComboBox.setForeground(new Color(205, 214, 244));
        clientComboBox.setPreferredSize(new Dimension(280, 30));
        clientComboBox.addActionListener(e -> onTargetChanged());

        JButton btnRefreshClients = new JButton("🔄");
        btnRefreshClients.setToolTipText("Làm mới danh sách máy Client");
        btnRefreshClients.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btnRefreshClients.setBackground(new Color(69, 71, 90));
        btnRefreshClients.setForeground(new Color(205, 214, 244));
        btnRefreshClients.setFocusPainted(false);
        btnRefreshClients.addActionListener(e -> populateClientList(currentTargetIp));

        leftPanel.add(lblTarget);
        leftPanel.add(clientComboBox);
        leftPanel.add(btnRefreshClients);

        // Phía phải: Trạng thái kết nối
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        rightPanel.setOpaque(false);

        statusLabel = new JLabel("● Đang kết nối");
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        statusLabel.setForeground(new Color(166, 227, 161));
        rightPanel.add(statusLabel);

        header.add(leftPanel, BorderLayout.WEST);
        header.add(rightPanel, BorderLayout.EAST);
        return header;
    }

    private JPanel buildChatHistoryPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(30, 30, 46));
        panel.setBorder(new EmptyBorder(10, 16, 10, 16));

        chatHistoryPane = new JTextPane();
        chatHistoryPane.setEditable(false);
        chatHistoryPane.setBackground(new Color(24, 24, 37));
        chatHistoryPane.setContentType("text/html");

        htmlKit = new HTMLEditorKit();
        htmlDoc = (HTMLDocument) htmlKit.createDefaultDocument();
        chatHistoryPane.setEditorKit(htmlKit);
        chatHistoryPane.setDocument(htmlDoc);

        // Thiết lập CSS cho giao diện tin nhắn
        htmlDoc.getStyleSheet().addRule("body { font-family: 'Segoe UI', Tahoma, sans-serif; font-size: 13px; color: #cdd6f4; margin: 8px; }");
        htmlDoc.getStyleSheet().addRule(".msg-box { margin-bottom: 8px; padding: 6px 10px; border-radius: 6px; }");
        htmlDoc.getStyleSheet().addRule(".server-msg { background-color: #313244; border-left: 4px solid #cba6f7; }");
        htmlDoc.getStyleSheet().addRule(".client-msg { background-color: #363a4f; border-left: 4px solid #a6e3a1; }");
        htmlDoc.getStyleSheet().addRule(".system-msg { background-color: #24273a; border-left: 4px solid #f9e2af; color: #f9e2af; font-style: italic; }");
        htmlDoc.getStyleSheet().addRule(".error-msg { background-color: #2e1e28; border-left: 4px solid #f38ba8; color: #f38ba8; }");
        htmlDoc.getStyleSheet().addRule(".time { color: #9399b2; font-size: 11px; margin-right: 6px; }");
        htmlDoc.getStyleSheet().addRule(".sender-server { color: #cba6f7; font-weight: bold; }");
        htmlDoc.getStyleSheet().addRule(".sender-client { color: #a6e3a1; font-weight: bold; }");

        JScrollPane scrollPane = new JScrollPane(chatHistoryPane);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(69, 71, 90)));
        scrollPane.setBackground(new Color(24, 24, 37));
        scrollPane.getViewport().setBackground(new Color(24, 24, 37));

        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildInputPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        panel.setBackground(new Color(49, 50, 68));
        panel.setBorder(new EmptyBorder(10, 16, 12, 16));

        inputField = new JTextField();
        inputField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        inputField.setBackground(new Color(30, 30, 46));
        inputField.setForeground(new Color(205, 214, 244));
        inputField.setCaretColor(new Color(203, 166, 247));
        inputField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(88, 91, 112)),
                new EmptyBorder(6, 10, 6, 10)
        ));
        inputField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    performSendMessage();
                }
            }
        });

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        btnPanel.setOpaque(false);

        btnSend = new JButton("Gửi 📤");
        btnSend.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnSend.setBackground(new Color(137, 180, 250));
        btnSend.setForeground(new Color(30, 30, 46));
        btnSend.setFocusPainted(false);
        btnSend.setBorderPainted(false);
        btnSend.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnSend.setBorder(new EmptyBorder(8, 16, 8, 16));
        btnSend.addActionListener(e -> performSendMessage());

        btnClear = new JButton("Xóa lịch sử");
        btnClear.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btnClear.setBackground(new Color(69, 71, 90));
        btnClear.setForeground(new Color(205, 214, 244));
        btnClear.setFocusPainted(false);
        btnClear.setBorderPainted(false);
        btnClear.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnClear.setBorder(new EmptyBorder(8, 12, 8, 12));
        btnClear.addActionListener(e -> clearChatHistory());

        btnPanel.add(btnClear);
        btnPanel.add(btnSend);

        panel.add(inputField, BorderLayout.CENTER);
        panel.add(btnPanel, BorderLayout.EAST);
        return panel;
    }

    private void populateClientList(String selectIp) {
        clientComboBox.removeAllItems();
        clientComboBox.addItem(TARGET_ALL);

        List<ClientInfo> clients = ClientManager.getInstance().getAllClients();
        int selectIdx = 0;
        int idx = 1;

        for (ClientInfo client : clients) {
            String item = client.getHostName() + " (" + client.getIpAddress() + ")";
            clientComboBox.addItem(item);
            if (selectIp != null && selectIp.equalsIgnoreCase(client.getIpAddress())) {
                selectIdx = idx;
            }
            idx++;
        }

        if (selectIdx < clientComboBox.getItemCount()) {
            clientComboBox.setSelectedIndex(selectIdx);
        }
    }

    public void selectClient(String ip) {
        if (ip == null) {
            clientComboBox.setSelectedIndex(0);
            return;
        }
        for (int i = 0; i < clientComboBox.getItemCount(); i++) {
            String item = clientComboBox.getItemAt(i);
            if (item.contains("(" + ip + ")")) {
                clientComboBox.setSelectedIndex(i);
                return;
            }
        }
        // Nếu client chưa có trong combo (mới kết nối hoặc offline), nạp lại danh sách
        populateClientList(ip);
    }

    private void onTargetChanged() {
        int selectedIndex = clientComboBox.getSelectedIndex();
        if (selectedIndex <= 0) {
            currentTargetIp = null;
        } else {
            String item = (String) clientComboBox.getSelectedItem();
            if (item != null && item.contains("(") && item.contains(")")) {
                int start = item.lastIndexOf("(") + 1;
                int end = item.lastIndexOf(")");
                currentTargetIp = item.substring(start, end).trim();
            }
        }
        updateConnectionStatus();
    }

    private void updateConnectionStatus() {
        if (currentTargetIp == null) {
            int online = ClientManager.getInstance().getOnlineCount();
            statusLabel.setText("● Đang online: " + online + " máy");
            statusLabel.setForeground(online > 0 ? new Color(166, 227, 161) : new Color(243, 139, 168));
            btnSend.setEnabled(online > 0);
        } else {
            ClientInfo info = ClientManager.getInstance().getClientInfo(currentTargetIp);
            boolean isOnline = (info != null && ClientInfo.STATUS_ONLINE.equalsIgnoreCase(info.getStatus()));
            if (isOnline) {
                statusLabel.setText("● ONLINE (Đã kết nối)");
                statusLabel.setForeground(new Color(166, 227, 161));
                btnSend.setEnabled(true);
            } else {
                statusLabel.setText("○ OFFLINE (Mất kết nối)");
                statusLabel.setForeground(new Color(243, 139, 168));
                btnSend.setEnabled(false);
            }
        }
    }

    /**
     * Gửi tin nhắn từ Server tới Client đã chọn hoặc Broadcast.
     */
    private void performSendMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty()) {
            return;
        }

        String time = LocalDateTime.now().format(TIME_FORMATTER);

        if (currentTargetIp == null) {
            // Gửi Broadcast tới tất cả Client
            MessagePacket packet = MessagePacket.createChat("SERVER", "ALL", text);
            ClientManager.getInstance().broadcast(packet);
            appendChatMessage("Server (Broadcast)", text, time, true);
            inputField.setText("");
        } else {
            // Gửi riêng cho Client được chọn
            ClientInfo info = ClientManager.getInstance().getClientInfo(currentTargetIp);
            boolean isOnline = (info != null && ClientInfo.STATUS_ONLINE.equalsIgnoreCase(info.getStatus()));

            if (!isOnline) {
                appendErrorMessage("Không thể gửi: Máy [" + currentTargetIp + "] đã mất kết nối hoặc ngắt mạng!", time);
                JOptionPane.showMessageDialog(this,
                        "Client [" + currentTargetIp + "] đang ngắt kết nối.\nKhông thể gửi tin nhắn!",
                        "Mất kết nối", JOptionPane.WARNING_MESSAGE);
                return;
            }

            MessagePacket packet = MessagePacket.createChat("SERVER", currentTargetIp, text);
            boolean sent = ClientManager.getInstance().sendTo(currentTargetIp, packet);
            if (sent) {
                String targetName = (info != null && info.getHostName() != null) ? info.getHostName() : currentTargetIp;
                appendChatMessage("Server -> " + targetName, text, time, true);
                inputField.setText("");
            } else {
                appendErrorMessage("Gửi thất bại tới " + currentTargetIp + " do lỗi Socket hoặc Client ngắt kết nối đột ngột!", time);
            }
        }
    }

    /**
     * Nhận tin nhắn chat từ Client gửi lên Server.
     */
    @Override
    public void onChatMessageReceived(MessagePacket packet) {
        SwingUtilities.invokeLater(() -> {
            String time = formatTimestamp(packet.getTimestamp());
            String sender = packet.getSender();
            String payload = packet.getPayload();

            // Nếu người gửi là một IP, lấy thêm Hostname cho thân thiện
            ClientInfo info = ClientManager.getInstance().getClientInfo(sender);
            String displayName = sender;
            if (info != null && info.getHostName() != null && !info.getHostName().equalsIgnoreCase(sender)) {
                displayName = info.getHostName() + " (" + sender + ")";
            }

            appendChatMessage(displayName, payload, time, false);
            toFront();
        });
    }

    // Sự kiện kết nối / ngắt kết nối mạng
    @Override
    public void onClientConnected(ClientInfo clientInfo) {
        SwingUtilities.invokeLater(() -> {
            populateClientList(currentTargetIp);
            String time = LocalDateTime.now().format(TIME_FORMATTER);
            appendSystemMessage("[" + time + "] ℹ️ Client [" + clientInfo.getHostName() + " (" + clientInfo.getIpAddress() + ")] đã kết nối vào hệ thống.");
            updateConnectionStatus();
        });
    }

    @Override
    public void onClientDisconnected(String clientIp) {
        SwingUtilities.invokeLater(() -> {
            populateClientList(currentTargetIp);
            String time = LocalDateTime.now().format(TIME_FORMATTER);
            appendErrorMessage("Client [" + clientIp + "] đã mất kết nối hoặc thoát khỏi mạng!", time);
            updateConnectionStatus();
        });
    }

    @Override
    public void onClientUpdated(ClientInfo clientInfo) {
        SwingUtilities.invokeLater(this::updateConnectionStatus);
    }

    private void appendChatMessage(String sender, String message, String time, boolean isSelf) {
        String boxClass = isSelf ? "server-msg" : "client-msg";
        String senderClass = isSelf ? "sender-server" : "sender-client";
        String escapedMsg = escapeHtml(message);

        String html = "<div class='msg-box " + boxClass + "'>"
                + "<span class='time'>[" + time + "]</span> "
                + "<span class='" + senderClass + "'>" + escapeHtml(sender) + ":</span> "
                + "<span>" + escapedMsg + "</span>"
                + "</div>";

        insertHtml(html);
    }

    private void appendSystemMessage(String text) {
        String time = LocalDateTime.now().format(TIME_FORMATTER);
        String html = "<div class='msg-box system-msg'>"
                + "<span class='time'>[" + time + "]</span> "
                + "<span>" + escapeHtml(text) + "</span>"
                + "</div>";
        insertHtml(html);
    }

    private void appendErrorMessage(String text, String time) {
        String html = "<div class='msg-box error-msg'>"
                + "<span class='time'>[" + time + "]</span> "
                + "<span>⚠️ " + escapeHtml(text) + "</span>"
                + "</div>";
        insertHtml(html);
    }

    private void insertHtml(String html) {
        SwingUtilities.invokeLater(() -> {
            try {
                htmlKit.insertHTML(htmlDoc, htmlDoc.getLength(), html, 0, 0, null);
                chatHistoryPane.setCaretPosition(htmlDoc.getLength());
            } catch (Exception e) {
                System.err.println("[ChatFrame] Lỗi chèn HTML: " + e.getMessage());
            }
        });
    }

    private void clearChatHistory() {
        chatHistoryPane.setText("");
        appendSystemMessage("Đã xóa toàn bộ lịch sử tin nhắn.");
    }

    private String formatTimestamp(long timestamp) {
        if (timestamp <= 0) {
            return LocalDateTime.now().format(TIME_FORMATTER);
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault()).format(TIME_FORMATTER);
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("\n", "<br/>");
    }
}
