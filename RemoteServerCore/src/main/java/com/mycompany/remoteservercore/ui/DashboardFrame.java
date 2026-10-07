package com.mycompany.remoteservercore.ui;

import com.mycompany.remoteservercore.core.ClientManager;
import com.mycompany.remoteservercore.model.ClientInfo;
import com.mycompany.remoteservercore.model.MessagePacket;
import com.mycompany.remoteservercore.model.User;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Màn hình chính (Dashboard) sau khi đăng nhập thành công.
 * Hiện thực toàn diện tính năng Issue #34: Gửi lệnh điều khiển từ Server tới Client.
 *
 * <p>Luồng thực thi:
 * <ol>
 *   <li>Chọn Client từ bảng danh sách.</li>
 *   <li>Chọn Command (LOCK, LOGOUT, RESTART, SHUTDOWN).</li>
 *   <li>Tạo JSON gói tin MessagePacket.</li>
 *   <li>Gửi qua TCP Socket tới Client mục tiêu.</li>
 *   <li>Client thực thi và trả kết quả (ACK) hoặc lỗi (ERROR).</li>
 *   <li>Hiển thị kết quả và chi tiết lỗi trực tiếp trên giao diện Server.</li>
 * </ol>
 */
public class DashboardFrame extends JFrame implements ClientManager.ClientEventListener, ClientManager.CommandResponseListener {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final User currentUser;

    // ─── Table & Status ─────────────
    private JTable clientTable;
    private DefaultTableModel tableModel;
    private JLabel selectedClientLabel;
    private JLabel statusLabel;

    // ─── Console / Log kết quả thực thi ───
    private JTextArea logTextArea;

    // ─── Các nút điều khiển (ADMIN) ───────────────────────────────────────────────
    private JButton btnLock;
    private JButton btnLogoutClient;
    private JButton btnRestart;
    private JButton btnShutdown;
    private JButton btnScreenCapture;
    private JButton btnBlockWeb;
    private JButton btnSendMessage;

    public DashboardFrame(User user) {
        this.currentUser = user;
        initComponents();
        applyRolePermissions();

        // Đăng ký listener lắng nghe cập nhật danh sách Client kết nối và phản hồi lệnh
        ClientManager.getInstance().addListener(this);
        ClientManager.getInstance().addCommandListener(this);

        refreshClientTable();
        appendLog("Hệ thống Dashboard sẵn sàng. Đang kết nối với mạng LAN nội bộ.");
    }

    private void initComponents() {
        setTitle("Dashboard — " + currentUser.getUsername()
                + " [" + currentUser.getRole() + "]");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 720);
        setMinimumSize(new Dimension(880, 600));
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(new Color(30, 30, 46));
        setContentPane(root);

        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(buildMainContent(), BorderLayout.CENTER);
        root.add(buildFooter(), BorderLayout.SOUTH);
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(49, 50, 68));
        header.setBorder(new EmptyBorder(14, 20, 14, 20));

        JLabel titleLabel = new JLabel("🖥  Remote Server Control  —  Dashboard");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        titleLabel.setForeground(new Color(203, 166, 247));

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        rightPanel.setOpaque(false);

        JLabel userInfoLabel = new JLabel(
                currentUser.getUsername() + "  (" + currentUser.getRole() + ")"
        );
        userInfoLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        userInfoLabel.setForeground(new Color(166, 227, 161));

        JButton btnLogout = new JButton("Đăng xuất");
        btnLogout.setName("logoutButton");
        btnLogout.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnLogout.setBackground(new Color(243, 139, 168));
        btnLogout.setForeground(new Color(30, 30, 46));
        btnLogout.setFocusPainted(false);
        btnLogout.setBorderPainted(false);
        btnLogout.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnLogout.setBorder(new EmptyBorder(6, 16, 6, 16));

        btnLogout.addActionListener(e -> performLogout());

        rightPanel.add(userInfoLabel);
        rightPanel.add(btnLogout);

        header.add(titleLabel, BorderLayout.WEST);
        header.add(rightPanel, BorderLayout.EAST);
        return header;
    }

    private JPanel buildMainContent() {
        JPanel content = new JPanel(new BorderLayout(16, 16));
        content.setBackground(new Color(30, 30, 46));
        content.setBorder(new EmptyBorder(16, 20, 10, 20));

        // Khu vực trung tâm: Bảng Client phía trên, Nhật ký & Kết quả phía dưới
        JPanel centerPanel = new JPanel(new BorderLayout(0, 12));
        centerPanel.setBackground(new Color(30, 30, 46));

        centerPanel.add(buildClientListPanel(), BorderLayout.CENTER);
        centerPanel.add(buildExecutionLogPanel(), BorderLayout.SOUTH);

        content.add(centerPanel, BorderLayout.CENTER);
        content.add(buildControlPanel(), BorderLayout.EAST);

        return content;
    }

    private JPanel buildClientListPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBackground(new Color(49, 50, 68));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(new Color(88, 91, 112)),
                        "Danh sách Client kết nối LAN",
                        TitledBorder.LEFT, TitledBorder.TOP,
                        new Font("Segoe UI", Font.BOLD, 13),
                        new Color(137, 180, 250)
                ),
                new EmptyBorder(6, 8, 8, 8)
        ));

        // Nhãn chỉ báo Client đang được chọn
        selectedClientLabel = new JLabel("🎯 Chưa chọn Client nào (Nhấn chọn một máy trong danh sách để điều khiển)");
        selectedClientLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        selectedClientLabel.setForeground(new Color(249, 226, 175));
        selectedClientLabel.setBorder(new EmptyBorder(4, 4, 6, 4));

        String[] columns = {"IP Address", "Tên máy", "Người dùng", "CPU", "RAM", "Trạng thái"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };

        clientTable = new JTable(tableModel);
        clientTable.setBackground(new Color(49, 50, 68));
        clientTable.setForeground(new Color(205, 214, 244));
        clientTable.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        clientTable.setRowHeight(28);
        clientTable.setGridColor(new Color(88, 91, 112));
        clientTable.getTableHeader().setBackground(new Color(69, 71, 90));
        clientTable.getTableHeader().setForeground(new Color(203, 166, 247));
        clientTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        clientTable.setSelectionBackground(new Color(88, 91, 112));
        clientTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        clientTable.setName("clientTable");

        // Tô màu cột trạng thái
        DefaultTableCellRenderer statusRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setHorizontalAlignment(CENTER);
                String valStr = (value != null) ? value.toString() : "";
                if (valStr.equalsIgnoreCase(ClientInfo.STATUS_ONLINE)) {
                    setForeground(new Color(166, 227, 161));
                    setText("● ONLINE");
                } else {
                    setForeground(new Color(147, 153, 178));
                    setText("○ OFFLINE");
                }
                return c;
            }
        };
        clientTable.getColumnModel().getColumn(5).setCellRenderer(statusRenderer);

        // Sự kiện khi người dùng click chọn dòng trong bảng
        clientTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateSelectedClientDisplay();
            }
        });

        JScrollPane scrollPane = new JScrollPane(clientTable);
        scrollPane.setBackground(new Color(49, 50, 68));
        scrollPane.getViewport().setBackground(new Color(49, 50, 68));
        scrollPane.setBorder(BorderFactory.createEmptyBorder());

        panel.add(selectedClientLabel, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private void updateSelectedClientDisplay() {
        int selectedRow = clientTable.getSelectedRow();
        if (selectedRow >= 0) {
            String ip = (String) tableModel.getValueAt(selectedRow, 0);
            String host = (String) tableModel.getValueAt(selectedRow, 1);
            String status = (String) tableModel.getValueAt(selectedRow, 5);
            selectedClientLabel.setText("🎯 Đang chọn: " + host + " (" + ip + ")  |  Trạng thái: " + status);
            selectedClientLabel.setForeground(new Color(166, 227, 161));
        } else {
            selectedClientLabel.setText("🎯 Chưa chọn Client nào (Nhấn chọn một máy trong danh sách để điều khiển)");
            selectedClientLabel.setForeground(new Color(249, 226, 175));
        }
    }

    private JPanel buildExecutionLogPanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBackground(new Color(49, 50, 68));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(new Color(88, 91, 112)),
                        "📋 Kết quả thực thi & Phản hồi từ Client (Real-time Execution Log)",
                        TitledBorder.LEFT, TitledBorder.TOP,
                        new Font("Segoe UI", Font.BOLD, 13),
                        new Color(137, 180, 250)
                ),
                new EmptyBorder(4, 8, 8, 8)
        ));
        panel.setPreferredSize(new Dimension(0, 190));

        logTextArea = new JTextArea();
        logTextArea.setEditable(false);
        logTextArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        logTextArea.setBackground(new Color(30, 30, 46));
        logTextArea.setForeground(new Color(205, 214, 244));
        logTextArea.setCaretColor(new Color(203, 166, 247));
        logTextArea.setLineWrap(true);
        logTextArea.setWrapStyleWord(true);
        logTextArea.setName("logTextArea");

        JScrollPane logScroll = new JScrollPane(logTextArea);
        logScroll.setBorder(BorderFactory.createLineBorder(new Color(69, 71, 90)));
        logScroll.setBackground(new Color(30, 30, 46));

        // Thanh công cụ log
        JPanel toolBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        toolBar.setOpaque(false);

        JButton btnClearLog = new JButton("Xóa nhật ký");
        btnClearLog.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnClearLog.setBackground(new Color(69, 71, 90));
        btnClearLog.setForeground(new Color(205, 214, 244));
        btnClearLog.setFocusPainted(false);
        btnClearLog.setBorder(new EmptyBorder(4, 10, 4, 10));
        btnClearLog.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnClearLog.addActionListener(e -> logTextArea.setText(""));

        toolBar.add(btnClearLog);

        panel.add(toolBar, BorderLayout.NORTH);
        panel.add(logScroll, BorderLayout.CENTER);

        return panel;
    }

    private JPanel buildControlPanel() {
        JPanel panel = new JPanel(new GridLayout(8, 1, 0, 8));
        panel.setBackground(new Color(30, 30, 46));
        panel.setPreferredSize(new Dimension(230, 0));

        JLabel ctrlLabel = new JLabel("Điều khiển hệ thống", SwingConstants.CENTER);
        ctrlLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        ctrlLabel.setForeground(new Color(137, 180, 250));
        panel.add(ctrlLabel);

        // Các nút điều khiển theo yêu cầu Issue #34
        btnLock          = createControlButton("🔒  Khóa màn hình (LOCK)", "btnLock");
        btnLogoutClient  = createControlButton("🚪  Đăng xuất (LOGOUT)",    "btnLogoutClient");
        btnRestart       = createControlButton("🔄  Khởi động lại (RESTART)", "btnRestart");
        btnShutdown      = createControlButton("⏻  Tắt máy (SHUTDOWN)",     "btnShutdown");

        btnScreenCapture = createControlButton("📷  Chụp màn hình",        "btnScreenCapture");
        btnBlockWeb      = createControlButton("🚫  Chặn trang web",        "btnBlockWeb");
        btnSendMessage   = createControlButton("💬  Gửi tin nhắn",          "btnSendMessage");

        // Gắn sự kiện gửi lệnh từ Server UI tới Client
        btnLock.addActionListener(e -> sendCommandToClient(MessagePacket.CMD_LOCK));
        btnLogoutClient.addActionListener(e -> sendCommandToClient(MessagePacket.CMD_LOGOUT));
        btnRestart.addActionListener(e -> sendCommandToClient(MessagePacket.CMD_RESTART));
        btnShutdown.addActionListener(e -> sendCommandToClient(MessagePacket.CMD_SHUTDOWN));
        btnSendMessage.addActionListener(e -> sendChatMessage());

        panel.add(btnLock);
        panel.add(btnLogoutClient);
        panel.add(btnRestart);
        panel.add(btnShutdown);
        panel.add(btnScreenCapture);
        panel.add(btnBlockWeb);
        panel.add(btnSendMessage);

        return panel;
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT));
        footer.setBackground(new Color(49, 50, 68));
        footer.setBorder(new EmptyBorder(6, 14, 6, 14));

        statusLabel = new JLabel("Server đang lắng nghe  |  Cổng: 9999  |  Lệnh hỗ trợ: LOCK, LOGOUT, RESTART, SHUTDOWN");
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statusLabel.setForeground(new Color(147, 153, 178));
        footer.add(statusLabel);
        return footer;
    }

    private void applyRolePermissions() {
        boolean isAdmin = currentUser.isAdmin();

        btnLock.setEnabled(isAdmin);
        btnLogoutClient.setEnabled(isAdmin);
        btnRestart.setEnabled(isAdmin);
        btnShutdown.setEnabled(isAdmin);
        btnScreenCapture.setEnabled(isAdmin);
        btnBlockWeb.setEnabled(isAdmin);
        btnSendMessage.setEnabled(isAdmin);

        if (!isAdmin) {
            String tooltip = "Chỉ ADMIN mới có quyền gửi lệnh điều khiển hệ thống";
            btnLock.setToolTipText(tooltip);
            btnLogoutClient.setToolTipText(tooltip);
            btnRestart.setToolTipText(tooltip);
            btnShutdown.setToolTipText(tooltip);
            btnScreenCapture.setToolTipText(tooltip);
            btnBlockWeb.setToolTipText(tooltip);
            btnSendMessage.setToolTipText(tooltip);
        }
    }

    /**
     * Gửi lệnh điều khiển hệ thống (LOCK, LOGOUT, RESTART, SHUTDOWN) tới Client được chọn.
     * Hiện thực hóa đúng quy trình Issue #34:
     *   1. Chọn Client
     *   2. Chọn Command
     *   3. Tạo gói JSON (MessagePacket)
     *   4. Gửi qua TCP Socket
     *   5. Hiển thị thông báo gửi & chờ kết quả phản hồi
     */
    private void sendCommandToClient(String command) {
        int selectedRow = clientTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this,
                    "Vui lòng chọn một Client trong bảng danh sách trước khi gửi lệnh [" + command + "]!",
                    "Chưa chọn Client", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String targetIp  = (String) tableModel.getValueAt(selectedRow, 0);
        String hostName  = (String) tableModel.getValueAt(selectedRow, 1);
        String status    = (String) tableModel.getValueAt(selectedRow, 5);

        if (status != null && status.toUpperCase().contains("OFFLINE")) {
            JOptionPane.showMessageDialog(this,
                    "Client [" + hostName + " (" + targetIp + ")] đang OFFLINE!\nKhông thể gửi lệnh điều khiển.",
                    "Client Offline", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Hộp thoại xác nhận gửi lệnh
        int confirm = JOptionPane.showConfirmDialog(this,
                "Bạn có chắc chắn muốn gửi lệnh [" + command + "] tới máy trạm:\n"
                + "• Tên máy: " + hostName + "\n"
                + "• Địa chỉ IP: " + targetIp + "?",
                "Xác nhận gửi lệnh điều khiển",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        // Tạo gói tin JSON lệnh điều khiển (MessagePacket)
        MessagePacket packet = MessagePacket.createCommand(targetIp, command);

        // Gửi qua TCP Socket tới Client đã chọn
        boolean success = ClientManager.getInstance().sendTo(targetIp, packet);

        if (success) {
            appendLog("📤 [GỬI LỆNH] Đã gửi lệnh [" + command + "] tới Client " + hostName + " (" + targetIp + "). Đang chờ phản hồi...");
            if (statusLabel != null) {
                statusLabel.setText("Đã gửi lệnh [" + command + "] tới " + hostName + "... Đang chờ phản hồi từ Client.");
            }
        } else {
            appendLog("⚠️ [LỖI GỬI] Không thể gửi lệnh [" + command + "] tới Client " + hostName + " (" + targetIp + ") do mất kết nối Socket!");
            if (statusLabel != null) {
                statusLabel.setText("Lỗi gửi lệnh tới " + targetIp);
            }
            JOptionPane.showMessageDialog(this,
                    "Không thể gửi lệnh tới Client " + hostName + " (" + targetIp + ")!\nClient có thể đã mất kết nối hoặc ngắt mạng.",
                    "Lỗi gửi lệnh", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Nhận phản hồi kết quả (ACK) hoặc lỗi (ERROR) từ Client gửi về qua Socket (Issue #34).
     */
    @Override
    public void onCommandResponse(String clientIp, String type, String message) {
        SwingUtilities.invokeLater(() -> {
            ClientInfo info = ClientManager.getInstance().getClientInfo(clientIp);
            String clientName = (info != null && info.getHostName() != null) ? info.getHostName() : clientIp;

            if (MessagePacket.TYPE_ACK.equalsIgnoreCase(type)) {
                // Hiển thị kết quả thành công
                appendLog("✅ [KẾT QUẢ THÀNH CÔNG] từ Client " + clientName + " (" + clientIp + "): " + message);
                if (statusLabel != null) {
                    statusLabel.setText("✅ Client " + clientName + " thực thi thành công!");
                }
                JOptionPane.showMessageDialog(this,
                        "Client [" + clientName + " (" + clientIp + ")] đã thực thi THÀNH CÔNG:\n" + message,
                        "Kết quả thực thi lệnh", JOptionPane.INFORMATION_MESSAGE);
            } else if (MessagePacket.TYPE_ERROR.equalsIgnoreCase(type)) {
                // Hiển thị thông báo lỗi
                appendLog("❌ [KẾT QUẢ LỖI] từ Client " + clientName + " (" + clientIp + "): " + message);
                if (statusLabel != null) {
                    statusLabel.setText("❌ Client " + clientName + " báo lỗi khi thực thi!");
                }
                JOptionPane.showMessageDialog(this,
                        "Client [" + clientName + " (" + clientIp + ")] báo LỖI:\n" + message,
                        "Lỗi thực thi lệnh", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    /**
     * Ghi log ra khung hiển thị kết quả điều khiển (Real-time Execution Log).
     */
    public void appendLog(String message) {
        SwingUtilities.invokeLater(() -> {
            if (logTextArea != null) {
                String time = LocalTime.now().format(TIME_FORMATTER);
                logTextArea.append("[" + time + "] " + message + "\n");
                logTextArea.setCaretPosition(logTextArea.getDocument().getLength());
            }
        });
    }

    private void sendChatMessage() {
        String msg = JOptionPane.showInputDialog(this, "Nhập nội dung tin nhắn gửi tới Client:", "Gửi Tin Nhắn", JOptionPane.QUESTION_MESSAGE);
        if (msg != null && !msg.isBlank()) {
            MessagePacket chatPacket = MessagePacket.createChat("SERVER", "ALL", msg.trim());
            ClientManager.getInstance().broadcast(chatPacket);
            appendLog("💬 [CHAT] Đã gửi tin nhắn broadcast: \"" + msg.trim() + "\"");
            JOptionPane.showMessageDialog(this, "Đã gửi tin nhắn chat tới toàn bộ Client!");
        }
    }

    private void refreshClientTable() {
        SwingUtilities.invokeLater(() -> {
            tableModel.setRowCount(0);
            List<ClientInfo> clients = ClientManager.getInstance().getAllClients();
            for (ClientInfo info : clients) {
                tableModel.addRow(new Object[]{
                        info.getIpAddress(),
                        info.getHostName(),
                        info.getCurrentUser(),
                        info.getCpuUsage() + "%",
                        info.getFormattedRam(),
                        info.getStatus()
                });
            }
            updateSelectedClientDisplay();
        });
    }

    private void performLogout() {
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Bạn có chắc muốn đăng xuất không?",
                "Xác nhận đăng xuất",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );

        if (confirm == JOptionPane.YES_OPTION) {
            ClientManager.getInstance().removeListener(this);
            ClientManager.getInstance().removeCommandListener(this);
            SwingUtilities.invokeLater(() -> {
                LoginFrame loginFrame = new LoginFrame();
                loginFrame.setVisible(true);
                dispose();
            });
        }
    }

    private JButton createControlButton(String text, String name) {
        JButton btn = new JButton(text);
        btn.setName(name);
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btn.setBackground(new Color(69, 71, 90));
        btn.setForeground(new Color(205, 214, 244));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setBorder(new EmptyBorder(8, 12, 8, 12));

        btn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                if (btn.isEnabled()) btn.setBackground(new Color(88, 91, 112));
            }
            @Override public void mouseExited(MouseEvent e) {
                if (btn.isEnabled()) btn.setBackground(new Color(69, 71, 90));
            }
        });
        return btn;
    }

    // Listener Callbacks
    @Override public void onClientConnected(ClientInfo clientInfo) {
        appendLog("🔗 [KẾT NỐI] Client mới kết nối: " + clientInfo.getHostName() + " (" + clientInfo.getIpAddress() + ")");
        refreshClientTable();
    }

    @Override public void onClientDisconnected(String clientIp) {
        appendLog("🔌 [NGẮT KẾT NỐI] Client đã ngắt kết nối: " + clientIp);
        refreshClientTable();
    }

    @Override public void onClientUpdated(ClientInfo clientInfo) {
        refreshClientTable();
    }
}
