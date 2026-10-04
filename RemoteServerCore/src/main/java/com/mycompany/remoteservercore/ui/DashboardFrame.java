package com.mycompany.remoteservercore.ui;

import com.mycompany.remoteservercore.core.ClientManager;
import com.mycompany.remoteservercore.model.ClientInfo;
import com.mycompany.remoteservercore.model.MessagePacket;
import com.mycompany.remoteservercore.model.User;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * Màn hình chính (Dashboard) sau khi đăng nhập thành công.
 *
 * <p>Phân quyền:
 * <ul>
 *   <li><b>ADMIN</b> — Tất cả nút điều khiển hệ thống (LOCK, LOGOUT, RESTART, SHUTDOWN) được kích hoạt.</li>
 *   <li><b>USER</b>  — Các nút điều khiển bị vô hiệu hóa (chỉ xem).</li>
 * </ul>
 */
public class DashboardFrame extends JFrame implements ClientManager.ClientEventListener {

    private final User currentUser;

    // ─── Table ─────────────
    private JTable clientTable;
    private DefaultTableModel tableModel;

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

        // Đăng ký listener lắng nghe cập nhật danh sách Client kết nối
        ClientManager.getInstance().addListener(this);
        refreshClientTable();
    }

    private void initComponents() {
        setTitle("Dashboard — " + currentUser.getUsername()
                + " [" + currentUser.getRole() + "]");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(880, 600);
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
        content.setBorder(new EmptyBorder(20, 20, 10, 20));

        content.add(buildClientListPanel(), BorderLayout.CENTER);
        content.add(buildControlPanel(), BorderLayout.EAST);

        return content;
    }

    private JPanel buildClientListPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(49, 50, 68));
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(88, 91, 112)),
                "Danh sách Client kết nối LAN",
                TitledBorder.LEFT, TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 13),
                new Color(137, 180, 250)
        ));

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
        clientTable.setName("clientTable");

        JScrollPane scrollPane = new JScrollPane(clientTable);
        scrollPane.setBackground(new Color(49, 50, 68));
        scrollPane.getViewport().setBackground(new Color(49, 50, 68));
        scrollPane.setBorder(BorderFactory.createEmptyBorder());

        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildControlPanel() {
        JPanel panel = new JPanel(new GridLayout(8, 1, 0, 8));
        panel.setBackground(new Color(30, 30, 46));
        panel.setPreferredSize(new Dimension(220, 0));

        JLabel ctrlLabel = new JLabel("Điều khiển hệ thống", SwingConstants.CENTER);
        ctrlLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        ctrlLabel.setForeground(new Color(137, 180, 250));
        panel.add(ctrlLabel);

        // Các nút điều khiển Issue #33
        btnLock          = createControlButton("🔒  Khóa màn hình (LOCK)", "btnLock");
        btnLogoutClient  = createControlButton("🚪  Đăng xuất (LOGOUT)",    "btnLogoutClient");
        btnRestart       = createControlButton("🔄  Khởi động lại (RESTART)", "btnRestart");
        btnShutdown      = createControlButton("⏻  Tắt máy (SHUTDOWN)",     "btnShutdown");

        btnScreenCapture = createControlButton("📷  Chụp màn hình",        "btnScreenCapture");
        btnBlockWeb      = createControlButton("🚫  Chặn trang web",        "btnBlockWeb");
        btnSendMessage   = createControlButton("💬  Gửi tin nhắn",          "btnSendMessage");

        // Gắn sự kiện cho các nút điều khiển
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
        footer.setBorder(new EmptyBorder(4, 12, 4, 12));

        JLabel statusLabel = new JLabel("Server đang lắng nghe  |  Cổng: 9999  |  Lệnh hỗ trợ: LOCK, LOGOUT, RESTART, SHUTDOWN");
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
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
     * Gửi lệnh hệ thống (LOCK, LOGOUT, RESTART, SHUTDOWN) tới Client được chọn
     * hoặc Broadcast cho tất cả Client.
     */
    private void sendCommandToClient(String command) {
        int selectedRow = clientTable.getSelectedRow();
        MessagePacket packet = MessagePacket.createCommand("ALL", command);

        if (selectedRow >= 0) {
            String targetIp = (String) tableModel.getValueAt(selectedRow, 0);
            packet.setTarget(targetIp);
            boolean success = ClientManager.getInstance().sendTo(targetIp, packet);
            if (success) {
                JOptionPane.showMessageDialog(this,
                        "Đã gửi thành công lệnh [" + command + "] tới Client " + targetIp,
                        "Thực thi lệnh", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this,
                        "Không thể gửi lệnh tới " + targetIp + " (Client offline)",
                        "Lỗi gửi lệnh", JOptionPane.ERROR_MESSAGE);
            }
        } else {
            // Không chọn dòng -> Phát sóng cho tất cả các máy online
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Bạn chưa chọn Client cụ thể. Bạn có muốn PHÁT SÓNG lệnh [" + command + "] tới TOÀN BỘ máy đang kết nối không?",
                    "Xác nhận Phát sóng Lệnh",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

            if (confirm == JOptionPane.YES_OPTION) {
                ClientManager.getInstance().broadcast(packet);
                JOptionPane.showMessageDialog(this,
                        "Đã phát sóng lệnh [" + command + "] tới tất cả Client đang online!",
                        "Phát sóng thành công", JOptionPane.INFORMATION_MESSAGE);
            }
        }
    }

    private void sendChatMessage() {
        String msg = JOptionPane.showInputDialog(this, "Nhập nội dung tin nhắn gửi tới Client:", "Gửi Tin Nhắn", JOptionPane.QUESTION_MESSAGE);
        if (msg != null && !msg.isBlank()) {
            MessagePacket chatPacket = MessagePacket.createChat("SERVER", "ALL", msg.trim());
            ClientManager.getInstance().broadcast(chatPacket);
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
    @Override public void onClientConnected(ClientInfo clientInfo) { refreshClientTable(); }
    @Override public void onClientDisconnected(String clientIp) { refreshClientTable(); }
    @Override public void onClientUpdated(ClientInfo clientInfo) { refreshClientTable(); }
}
