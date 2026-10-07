package com.mycompany.remoteservercore.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Giao diện Chat phía Client (Agent Client) để nhắn tin 2 chiều với Server (Issue #35 / Issue 14).
 * Cung cấp:
 *  - Nhận tin nhắn từ Server và hiển thị lên lịch sử chat có Timestamp.
 *  - Soạn và gửi tin nhắn từ Client -> Server.
 *  - Hiển thị trạng thái kết nối tới Server.
 *  - Xử lý khi mất kết nối: khóa khung nhập/nút gửi và thông báo lỗi.
 */
public class ClientChatFrame extends JFrame {

    public interface MessageSender {
        boolean send(String message);
    }

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final String clientName;
    private final MessageSender sender;

    private JLabel statusLabel;
    private JTextPane chatHistoryPane;
    private HTMLEditorKit htmlKit;
    private HTMLDocument htmlDoc;
    private JTextField inputField;
    private JButton btnSend;
    private JButton btnClear;
    private volatile boolean connected = true;

    public ClientChatFrame(String clientName, MessageSender sender) {
        this.clientName = (clientName != null && !clientName.isEmpty()) ? clientName : "CLIENT";
        this.sender = sender;
        initComponents();
        appendSystemMessage("Đã mở kết nối Chat nội bộ tới Máy chủ Quản trị (Server).");
    }

    private void initComponents() {
        setTitle("💬 Tin Nhắn Nội Bộ — " + clientName + " ↔ Server");
        setSize(560, 500);
        setMinimumSize(new Dimension(450, 380));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(new Color(30, 30, 46));
        setContentPane(root);

        root.add(buildHeaderPanel(), BorderLayout.NORTH);
        root.add(buildChatHistoryPanel(), BorderLayout.CENTER);
        root.add(buildInputPanel(), BorderLayout.SOUTH);
    }

    private JPanel buildHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(49, 50, 68));
        header.setBorder(new EmptyBorder(12, 16, 12, 16));

        JLabel titleLabel = new JLabel("💬 Chat với Quản trị viên (Server)");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        titleLabel.setForeground(new Color(137, 180, 250));

        statusLabel = new JLabel("● Đang kết nối tới Server");
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        statusLabel.setForeground(new Color(166, 227, 161));

        header.add(titleLabel, BorderLayout.WEST);
        header.add(statusLabel, BorderLayout.EAST);
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

        // Thiết lập giao diện CSS tin nhắn
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
        btnSend.setBackground(new Color(166, 227, 161));
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

    private void performSendMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty()) {
            return;
        }

        String time = LocalDateTime.now().format(TIME_FORMATTER);

        if (!connected) {
            appendErrorMessage("Không thể gửi tin nhắn vì đã mất kết nối tới Server!", time);
            JOptionPane.showMessageDialog(this,
                    "Đã mất kết nối tới Server. Vui lòng kiểm tra lại kết nối mạng!",
                    "Mất kết nối", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (sender != null) {
            boolean success = sender.send(text);
            if (success) {
                appendChatMessage("Bạn", text, time, true);
                inputField.setText("");
            } else {
                appendErrorMessage("Gửi tin nhắn thất bại do lỗi Socket mạng!", time);
            }
        }
    }

    /**
     * Nhận tin nhắn từ Server.
     */
    public void onMessageReceived(String fromServer, String message, long timestamp) {
        SwingUtilities.invokeLater(() -> {
            String time = formatTimestamp(timestamp);
            String displayName = (fromServer != null && !fromServer.isEmpty()) ? fromServer : "Server";
            appendChatMessage(displayName, message, time, false);
            if (!isVisible()) {
                setVisible(true);
            }
            toFront();
        });
    }

    /**
     * Cập nhật trạng thái kết nối mạng tới Server.
     */
    public void setConnected(boolean isConnected) {
        this.connected = isConnected;
        SwingUtilities.invokeLater(() -> {
            String time = LocalDateTime.now().format(TIME_FORMATTER);
            if (isConnected) {
                statusLabel.setText("● Đang kết nối tới Server");
                statusLabel.setForeground(new Color(166, 227, 161));
                btnSend.setEnabled(true);
                inputField.setEnabled(true);
                appendSystemMessage("[" + time + "] ℹ️ Đã kết nối lại tới Server thành công.");
            } else {
                statusLabel.setText("○ ĐÃ MẤT KẾT NỐI TỚI SERVER");
                statusLabel.setForeground(new Color(243, 139, 168));
                btnSend.setEnabled(false);
                inputField.setEnabled(false);
                appendErrorMessage("Đã mất kết nối tới Server! Các tin nhắn sẽ không thể gửi đi.", time);
            }
        });
    }

    public boolean isConnected() {
        return connected;
    }

    private void appendChatMessage(String senderName, String message, String time, boolean isSelf) {
        String boxClass = isSelf ? "client-msg" : "server-msg";
        String senderClass = isSelf ? "sender-client" : "sender-server";

        String html = "<div class='msg-box " + boxClass + "'>"
                + "<span class='time'>[" + time + "]</span> "
                + "<span class='" + senderClass + "'>" + escapeHtml(senderName) + ":</span> "
                + "<span>" + escapeHtml(message) + "</span>"
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
                System.err.println("[ClientChatFrame] Lỗi chèn HTML: " + e.getMessage());
            }
        });
    }

    private void clearChatHistory() {
        chatHistoryPane.setText("");
        appendSystemMessage("Đã xóa lịch sử tin nhắn.");
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
