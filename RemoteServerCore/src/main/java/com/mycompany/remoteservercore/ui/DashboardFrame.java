package com.mycompany.remoteservercore.ui;

import com.mycompany.remoteservercore.core.ClientManager;
import com.mycompany.remoteservercore.model.ClientInfo;
import com.mycompany.remoteservercore.model.User;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;

/**
 * Màn hình Dashboard chính sau khi đăng nhập thành công.
 *
 * <p><b>Chức năng (Issue #11):</b>
 * <ul>
 *   <li>Bảng danh sách Client: hostname, IP, Online/Offline, heartbeat, thời điểm kết nối.</li>
 *   <li>Tự động cập nhật khi client connect/disconnect/heartbeat (qua {@link ClientManager.ClientChangeListener}).</li>
 *   <li>Nút Refresh thủ công để load lại danh sách.</li>
 *   <li>Chọn client trong bảng để thực hiện điều khiển.</li>
 *   <li>Status bar: số client online/tổng số.</li>
 * </ul>
 *
 * <p><b>Phân quyền (Issue #31):</b>
 * <ul>
 *   <li>ADMIN — Tất cả nút điều khiển được kích hoạt.</li>
 *   <li>USER  — Các nút điều khiển bị vô hiệu hóa.</li>
 * </ul>
 */
public class DashboardFrame extends JFrame implements ClientManager.ClientChangeListener {

    // ─── Cột của bảng client ─────────────────────────────────────────────────
    private static final String[] COLUMNS = {
            "#", "Hostname", "Địa chỉ IP", "Trạng thái", "Heartbeat cuối", "Kết nối lúc"
    };

    // ─── User đã đăng nhập ───────────────────────────────────────────────────
    private final User currentUser;

    // ─── Table và model ──────────────────────────────────────────────────────
    private DefaultTableModel tableModel;
    private JTable            clientTable;

    // ─── Nút điều khiển (chỉ ADMIN dùng được) ───────────────────────────────
    private JButton btnScreenCapture;
    private JButton btnRemoteInput;
    private JButton btnShutdown;
    private JButton btnRestart;
    private JButton btnBlockWeb;
    private JButton btnSendMessage;

    // ─── Status bar ──────────────────────────────────────────────────────────
    private JLabel statusLabel;
    private JLabel onlineCountLabel;

    public DashboardFrame(User user) {
        this.currentUser = user;
        initComponents();
        applyRolePermissions(); // Phân quyền dựa trên role
        loadClientList();       // Load danh sách client hiện có
        // Đăng ký để nhận sự kiện connect/disconnect/heartbeat real-time
        ClientManager.getInstance().addListener(this);
    }

    // ─── Khởi tạo giao diện ──────────────────────────────────────────────────
    private void initComponents() {
        setTitle("Dashboard — " + currentUser.getUsername()
                + "  [" + currentUser.getRole() + "]"
                + "  |  Remote Computer Control LAN");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1050, 620);
        setLocationRelativeTo(null);
        setMinimumSize(new Dimension(800, 480));

        // Hủy listener khi đóng cửa sổ
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                ClientManager.getInstance().removeListener(DashboardFrame.this);
            }
        });

        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(new Color(30, 30, 46));
        setContentPane(root);

        root.add(buildHeader(),      BorderLayout.NORTH);
        root.add(buildMainContent(), BorderLayout.CENTER);
        root.add(buildFooter(),      BorderLayout.SOUTH);
    }

    // ─── Header ──────────────────────────────────────────────────────────────
    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(49, 50, 68));
        header.setBorder(new EmptyBorder(12, 20, 12, 20));

        // Tiêu đề trái
        JLabel titleLabel = new JLabel("🖥  Remote Server Control  —  Dashboard");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        titleLabel.setForeground(new Color(203, 166, 247));

        // Phải: thông tin user + Logout
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        rightPanel.setOpaque(false);

        onlineCountLabel = new JLabel("● 0 Online");
        onlineCountLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        onlineCountLabel.setForeground(new Color(166, 227, 161));

        JLabel userLabel = new JLabel(currentUser.getUsername() + "  (" + currentUser.getRole() + ")");
        userLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        userLabel.setForeground(new Color(205, 214, 244));

        JButton btnLogout = createActionButton("⏏  Đăng xuất", new Color(243, 139, 168));
        btnLogout.setName("logoutButton");
        btnLogout.addActionListener(e -> performLogout());

        rightPanel.add(onlineCountLabel);
        rightPanel.add(userLabel);
        rightPanel.add(btnLogout);

        header.add(titleLabel, BorderLayout.WEST);
        header.add(rightPanel, BorderLayout.EAST);
        return header;
    }

    // ─── Nội dung chính ──────────────────────────────────────────────────────
    private JPanel buildMainContent() {
        JPanel content = new JPanel(new BorderLayout(12, 0));
        content.setBackground(new Color(30, 30, 46));
        content.setBorder(new EmptyBorder(16, 16, 8, 16));

        content.add(buildClientPanel(),  BorderLayout.CENTER);
        content.add(buildControlPanel(), BorderLayout.EAST);
        return content;
    }

    // ─── Panel danh sách Client ──────────────────────────────────────────────
    private JPanel buildClientPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(new Color(30, 30, 46));

        // ── Toolbar: tiêu đề + nút Refresh ──
        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setOpaque(false);

        JLabel lbl = new JLabel("  📋  Danh sách Client kết nối");
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lbl.setForeground(new Color(137, 180, 250));

        JButton btnRefresh = createActionButton("🔄  Refresh", new Color(137, 180, 250));
        btnRefresh.setName("refreshButton");
        btnRefresh.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnRefresh.addActionListener(e -> loadClientList());

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        btnPanel.setOpaque(false);
        btnPanel.add(btnRefresh);

        toolbar.add(lbl, BorderLayout.WEST);
        toolbar.add(btnPanel, BorderLayout.EAST);
        panel.add(toolbar, BorderLayout.NORTH);

        // ── Bảng client ──
        tableModel = new DefaultTableModel(COLUMNS, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };

        clientTable = new JTable(tableModel);
        clientTable.setName("clientTable");
        clientTable.setBackground(new Color(49, 50, 68));
        clientTable.setForeground(new Color(205, 214, 244));
        clientTable.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        clientTable.setRowHeight(30);
        clientTable.setGridColor(new Color(69, 71, 90));
        clientTable.setSelectionBackground(new Color(88, 91, 112));
        clientTable.setSelectionForeground(Color.WHITE);
        clientTable.setShowVerticalLines(false);
        clientTable.setIntercellSpacing(new Dimension(0, 1));
        clientTable.setFillsViewportHeight(true);

        // Header style
        clientTable.getTableHeader().setBackground(new Color(69, 71, 90));
        clientTable.getTableHeader().setForeground(new Color(203, 166, 247));
        clientTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        clientTable.getTableHeader().setReorderingAllowed(false);
        clientTable.getTableHeader().setPreferredSize(new Dimension(0, 34));

        // Độ rộng cột
        int[] widths = {30, 180, 140, 90, 160, 160};
        for (int i = 0; i < widths.length; i++) {
            clientTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }

        // Renderer tô màu cột Trạng thái
        clientTable.getColumnModel().getColumn(3).setCellRenderer(new StatusCellRenderer());

        // Double-click chọn client để điều khiển
        clientTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    onClientSelected();
                }
            }
        });

        JScrollPane scroll = new JScrollPane(clientTable);
        scroll.setBackground(new Color(49, 50, 68));
        scroll.getViewport().setBackground(new Color(49, 50, 68));
        scroll.setBorder(BorderFactory.createLineBorder(new Color(69, 71, 90)));

        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    // ─── Panel điều khiển bên phải ───────────────────────────────────────────
    private JPanel buildControlPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 0));
        panel.setBackground(new Color(30, 30, 46));
        panel.setPreferredSize(new Dimension(210, 0));

        JLabel ctrlLabel = new JLabel("Điều khiển Client", SwingConstants.CENTER);
        ctrlLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        ctrlLabel.setForeground(new Color(137, 180, 250));
        ctrlLabel.setBorder(new EmptyBorder(0, 0, 10, 0));
        panel.add(ctrlLabel, BorderLayout.NORTH);

        JPanel btnPanel = new JPanel(new GridLayout(6, 1, 0, 8));
        btnPanel.setOpaque(false);

        btnScreenCapture = createControlButton("📷  Chụp màn hình",    "btnScreenCapture");
        btnRemoteInput   = createControlButton("🖱  Điều khiển chuột",  "btnRemoteInput");
        btnShutdown      = createControlButton("⏻  Tắt máy client",    "btnShutdown");
        btnRestart       = createControlButton("🔄  Khởi động lại",     "btnRestart");
        btnBlockWeb      = createControlButton("🚫  Chặn web",          "btnBlockWeb");
        btnSendMessage   = createControlButton("💬  Gửi tin nhắn",      "btnSendMessage");

        btnPanel.add(btnScreenCapture);
        btnPanel.add(btnRemoteInput);
        btnPanel.add(btnShutdown);
        btnPanel.add(btnRestart);
        btnPanel.add(btnBlockWeb);
        btnPanel.add(btnSendMessage);

        panel.add(btnPanel, BorderLayout.CENTER);
        return panel;
    }

    // ─── Footer / status bar ─────────────────────────────────────────────────
    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(new Color(49, 50, 68));
        footer.setBorder(new EmptyBorder(5, 14, 5, 14));

        statusLabel = new JLabel("Server đang chạy  |  Cổng: 9999");
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        statusLabel.setForeground(new Color(147, 153, 178));

        JLabel hintLabel = new JLabel("Double-click để điều khiển client");
        hintLabel.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        hintLabel.setForeground(new Color(108, 112, 134));

        footer.add(statusLabel,  BorderLayout.WEST);
        footer.add(hintLabel,    BorderLayout.EAST);
        return footer;
    }

    // ─── Tải danh sách client (Refresh) ──────────────────────────────────────
    /**
     * Load toàn bộ danh sách client từ ClientManager vào bảng.
     * Chạy trên EDT để thread-safe với Swing.
     */
    private void loadClientList() {
        SwingUtilities.invokeLater(() -> {
            tableModel.setRowCount(0); // Xóa dữ liệu cũ
            List<ClientInfo> list = ClientManager.getInstance().getAllClients();
            int idx = 1;
            for (ClientInfo c : list) {
                tableModel.addRow(new Object[]{
                        idx++,
                        c.getHostname(),
                        c.getIpAddress(),
                        c.getStatus().name(),
                        c.getLastHeartbeatFormatted(),
                        c.getConnectedAtFormatted()
                });
            }
            updateOnlineCount();
            System.out.println("[Dashboard] Refresh: " + list.size() + " client(s).");
        });
    }

    // ─── Cập nhật bộ đếm online ──────────────────────────────────────────────
    private void updateOnlineCount() {
        int online = ClientManager.getInstance().getOnlineCount();
        int total  = ClientManager.getInstance().getTotalCount();
        onlineCountLabel.setText("● " + online + " Online / " + total + " Tổng");
        onlineCountLabel.setForeground(online > 0 ? new Color(166, 227, 161) : new Color(147, 153, 178));
        statusLabel.setText("Server đang chạy  |  Cổng: 9999  |  " + online + " client online");
    }

    // ─── ClientChangeListener callbacks ──────────────────────────────────────

    /** Gọi khi có client mới kết nối */
    @Override
    public void onClientConnected(ClientInfo client) {
        System.out.println("[Dashboard] Client kết nối: " + client.getHostname());
        loadClientList(); // Refresh toàn bộ bảng
        // Thông báo nhỏ ở status bar
        SwingUtilities.invokeLater(() ->
            statusLabel.setText("✅  " + client.getHostname() + " (" + client.getIpAddress() + ") vừa kết nối!")
        );
    }

    /** Gọi khi client ngắt kết nối */
    @Override
    public void onClientDisconnected(ClientInfo client) {
        System.out.println("[Dashboard] Client ngắt kết nối: " + client.getHostname());
        loadClientList();
        SwingUtilities.invokeLater(() ->
            statusLabel.setText("❌  " + client.getHostname() + " (" + client.getIpAddress() + ") đã ngắt kết nối.")
        );
    }

    /** Gọi khi nhận heartbeat — chỉ cập nhật dòng tương ứng trong bảng */
    @Override
    public void onClientHeartbeat(ClientInfo client) {
        // Không refresh toàn bảng để tránh nhấp nháy — chỉ cập nhật cột heartbeat
        SwingUtilities.invokeLater(() -> {
            for (int row = 0; row < tableModel.getRowCount(); row++) {
                String ip = (String) tableModel.getValueAt(row, 2);
                if (ip != null && ip.equals(client.getIpAddress())) {
                    tableModel.setValueAt(client.getStatus().name(),            row, 3);
                    tableModel.setValueAt(client.getLastHeartbeatFormatted(),   row, 4);
                    break;
                }
            }
            updateOnlineCount();
        });
    }

    // ─── Chọn client để điều khiển ───────────────────────────────────────────
    private void onClientSelected() {
        int row = clientTable.getSelectedRow();
        if (row < 0) return;

        String hostname = (String) tableModel.getValueAt(row, 1);
        String ip       = (String) tableModel.getValueAt(row, 2);
        String status   = (String) tableModel.getValueAt(row, 3);

        if ("OFFLINE".equals(status)) {
            JOptionPane.showMessageDialog(this,
                    "Client \"" + hostname + "\" đang OFFLINE.\nVui lòng chọn client đang Online.",
                    "Client không khả dụng", JOptionPane.WARNING_MESSAGE);
            return;
        }

        System.out.println("[Dashboard] Đã chọn client: " + hostname + " (" + ip + ")");
        JOptionPane.showMessageDialog(this,
                "Đã chọn client: " + hostname + "\nIP: " + ip
                + "\n\n(Chức năng điều khiển sẽ được tích hợp ở các Issue tiếp theo)",
                "Client đã chọn", JOptionPane.INFORMATION_MESSAGE);
    }

    // ─── Phân quyền ──────────────────────────────────────────────────────────
    /**
     * Áp dụng quyền hạn theo role: USER chỉ xem, ADMIN toàn quyền.
     */
    private void applyRolePermissions() {
        boolean isAdmin = currentUser.isAdmin();
        String tooltip  = "Chỉ ADMIN mới có quyền thực hiện chức năng này";

        for (JButton btn : new JButton[]{
                btnScreenCapture, btnRemoteInput, btnShutdown,
                btnRestart, btnBlockWeb, btnSendMessage}) {
            btn.setEnabled(isAdmin);
            if (!isAdmin) btn.setToolTipText(tooltip);
        }

        if (!isAdmin) {
            System.out.println("[Dashboard] Role USER — các nút điều khiển bị vô hiệu hóa.");
        } else {
            System.out.println("[Dashboard] Role ADMIN — toàn quyền điều khiển.");
        }
    }

    // ─── Logout ──────────────────────────────────────────────────────────────
    private void performLogout() {
        int confirm = JOptionPane.showConfirmDialog(this,
                "Bạn có chắc muốn đăng xuất không?",
                "Xác nhận đăng xuất",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            ClientManager.getInstance().removeListener(this);
            System.out.println("[Dashboard] Đăng xuất: " + currentUser.getUsername());
            SwingUtilities.invokeLater(() -> {
                new LoginFrame().setVisible(true);
                dispose();
            });
        }
    }

    // ─── Renderer tô màu cột Trạng thái ──────────────────────────────────────
    /**
     * Tô màu xanh cho ONLINE, xám cho OFFLINE.
     */
    private static class StatusCellRenderer extends DefaultTableCellRenderer {
        private static final Color COLOR_ONLINE  = new Color(166, 227, 161);
        private static final Color COLOR_OFFLINE = new Color(147, 153, 178);

        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {

            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setHorizontalAlignment(CENTER);
            setFont(getFont().deriveFont(Font.BOLD));

            if ("ONLINE".equals(value)) {
                setForeground(isSelected ? COLOR_ONLINE : COLOR_ONLINE);
                setText("● ONLINE");
            } else {
                setForeground(isSelected ? COLOR_OFFLINE : COLOR_OFFLINE);
                setText("○ OFFLINE");
            }
            return this;
        }
    }

    // ─── Helper tạo nút điều khiển ───────────────────────────────────────────
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

    private JButton createActionButton(String text, Color bgColor) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setBackground(bgColor);
        btn.setForeground(new Color(30, 30, 46));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(new EmptyBorder(6, 14, 6, 14));
        btn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                btn.setBackground(bgColor.brighter());
            }
            @Override public void mouseExited(MouseEvent e) {
                btn.setBackground(bgColor);
            }
        });
        return btn;
    }
}
