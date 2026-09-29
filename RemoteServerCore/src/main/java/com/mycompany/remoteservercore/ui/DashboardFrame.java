package com.mycompany.remoteservercore.ui;

import com.mycompany.remoteservercore.model.User;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Màn hình chính (Dashboard) sau khi đăng nhập thành công.
 *
 * <p>Phân quyền:
 * <ul>
 *   <li><b>ADMIN</b> — Tất cả nút điều khiển được kích hoạt.</li>
 *   <li><b>USER</b>  — Các nút điều khiển bị vô hiệu hóa (chỉ xem).</li>
 * </ul>
 *
 * <p>Nút Logout đóng Dashboard và mở lại {@link LoginFrame}.
 */
public class DashboardFrame extends JFrame {

    // User đã xác thực được truyền vào qua constructor
    private final User currentUser;

    // ─── Các nút điều khiển (chỉ ADMIN được dùng) ───────────────────────────────
    private JButton btnScreenCapture;
    private JButton btnRemoteInput;
    private JButton btnShutdown;
    private JButton btnRestart;
    private JButton btnBlockWeb;
    private JButton btnSendMessage;

    public DashboardFrame(User user) {
        this.currentUser = user;
        initComponents();
        applyRolePermissions(); // Phân quyền ngay sau khi khởi tạo UI
    }

    // ─── Khởi tạo giao diện ──────────────────────────────────────────────────────
    private void initComponents() {
        setTitle("Dashboard — " + currentUser.getUsername()
                + " [" + currentUser.getRole() + "]");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 560);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(new Color(30, 30, 46));
        setContentPane(root);

        // ── Header ──────────────────────────────────────────────────────────────
        root.add(buildHeader(), BorderLayout.NORTH);

        // ── Nội dung chính ──────────────────────────────────────────────────────
        root.add(buildMainContent(), BorderLayout.CENTER);

        // ── Footer (status bar) ─────────────────────────────────────────────────
        root.add(buildFooter(), BorderLayout.SOUTH);
    }

    // ─── Header ─────────────────────────────────────────────────────────────────
    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(49, 50, 68));
        header.setBorder(new EmptyBorder(14, 20, 14, 20));

        // Tiêu đề trái
        JLabel titleLabel = new JLabel("🖥  Remote Server Control  —  Dashboard");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        titleLabel.setForeground(new Color(203, 166, 247));

        // Thông tin user + nút Logout ở phải
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        rightPanel.setOpaque(false);

        // Nhãn thông tin user
        JLabel userInfoLabel = new JLabel(
                currentUser.getUsername() + "  (" + currentUser.getRole() + ")"
        );
        userInfoLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        userInfoLabel.setForeground(new Color(166, 227, 161));

        // Nút Logout
        JButton btnLogout = new JButton("Đăng xuất");
        btnLogout.setName("logoutButton");
        btnLogout.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnLogout.setBackground(new Color(243, 139, 168));
        btnLogout.setForeground(new Color(30, 30, 46));
        btnLogout.setFocusPainted(false);
        btnLogout.setBorderPainted(false);
        btnLogout.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnLogout.setBorder(new EmptyBorder(6, 16, 6, 16));

        // Hover effect logout
        btnLogout.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { btnLogout.setBackground(new Color(255, 160, 185)); }
            @Override public void mouseExited(MouseEvent e)  { btnLogout.setBackground(new Color(243, 139, 168)); }
        });

        // Sự kiện Logout
        btnLogout.addActionListener(e -> performLogout());

        rightPanel.add(userInfoLabel);
        rightPanel.add(btnLogout);

        header.add(titleLabel,  BorderLayout.WEST);
        header.add(rightPanel,  BorderLayout.EAST);
        return header;
    }

    // ─── Nội dung chính ─────────────────────────────────────────────────────────
    private JPanel buildMainContent() {
        JPanel content = new JPanel(new BorderLayout(16, 16));
        content.setBackground(new Color(30, 30, 46));
        content.setBorder(new EmptyBorder(20, 20, 10, 20));

        // Bảng client kết nối (giả lập, sẽ tích hợp với ClientManager sau)
        JPanel clientPanel = buildClientListPanel();
        content.add(clientPanel, BorderLayout.CENTER);

        // Panel nút điều khiển bên phải
        JPanel controlPanel = buildControlPanel();
        content.add(controlPanel, BorderLayout.EAST);

        return content;
    }

    // ─── Danh sách client kết nối ────────────────────────────────────────────────
    private JPanel buildClientListPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(49, 50, 68));
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(88, 91, 112)),
                "Danh sách Client kết nối",
                TitledBorder.LEFT, TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 13),
                new Color(137, 180, 250)
        ));

        // Bảng đơn giản
        String[] columns = {"#", "IP Address", "Tên máy", "Trạng thái", "Kết nối lúc"};
        Object[][] data   = {}; // Sẽ được điền bởi ClientManager

        JTable table = new JTable(data, columns) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
        table.setBackground(new Color(49, 50, 68));
        table.setForeground(new Color(205, 214, 244));
        table.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        table.setRowHeight(28);
        table.setGridColor(new Color(88, 91, 112));
        table.getTableHeader().setBackground(new Color(69, 71, 90));
        table.getTableHeader().setForeground(new Color(203, 166, 247));
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        table.setSelectionBackground(new Color(88, 91, 112));
        table.setName("clientTable");

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBackground(new Color(49, 50, 68));
        scrollPane.getViewport().setBackground(new Color(49, 50, 68));
        scrollPane.setBorder(BorderFactory.createEmptyBorder());

        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    // ─── Panel nút điều khiển ────────────────────────────────────────────────────
    private JPanel buildControlPanel() {
        JPanel panel = new JPanel(new GridLayout(7, 1, 0, 10));
        panel.setBackground(new Color(30, 30, 46));
        panel.setBorder(new EmptyBorder(0, 0, 0, 0));
        panel.setPreferredSize(new Dimension(200, 0));

        // Nhãn tiêu đề
        JLabel ctrlLabel = new JLabel("Điều khiển", SwingConstants.CENTER);
        ctrlLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        ctrlLabel.setForeground(new Color(137, 180, 250));
        panel.add(ctrlLabel);

        // Các nút điều khiển (chỉ ADMIN dùng được)
        btnScreenCapture = createControlButton("📷  Chụp màn hình",  "btnScreenCapture");
        btnRemoteInput   = createControlButton("🖱  Điều khiển chuột/bàn phím", "btnRemoteInput");
        btnShutdown      = createControlButton("⏻  Tắt máy client",  "btnShutdown");
        btnRestart       = createControlButton("🔄  Khởi động lại",   "btnRestart");
        btnBlockWeb      = createControlButton("🚫  Chặn web",        "btnBlockWeb");
        btnSendMessage   = createControlButton("💬  Gửi tin nhắn",    "btnSendMessage");

        panel.add(btnScreenCapture);
        panel.add(btnRemoteInput);
        panel.add(btnShutdown);
        panel.add(btnRestart);
        panel.add(btnBlockWeb);
        panel.add(btnSendMessage);

        return panel;
    }

    // ─── Footer ──────────────────────────────────────────────────────────────────
    private JPanel buildFooter() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT));
        footer.setBackground(new Color(49, 50, 68));
        footer.setBorder(new EmptyBorder(4, 12, 4, 12));

        JLabel statusLabel = new JLabel("Server đang chạy  |  Cổng: 9999");
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        statusLabel.setForeground(new Color(147, 153, 178));
        footer.add(statusLabel);
        return footer;
    }

    // ─── Phân quyền ─────────────────────────────────────────────────────────────
    /**
     * Áp dụng phân quyền dựa trên role của user đang đăng nhập.
     * USER chỉ được xem — tất cả nút điều khiển bị vô hiệu hóa.
     * ADMIN được dùng đầy đủ.
     */
    private void applyRolePermissions() {
        boolean isAdmin = currentUser.isAdmin();

        btnScreenCapture.setEnabled(isAdmin);
        btnRemoteInput.setEnabled(isAdmin);
        btnShutdown.setEnabled(isAdmin);
        btnRestart.setEnabled(isAdmin);
        btnBlockWeb.setEnabled(isAdmin);
        btnSendMessage.setEnabled(isAdmin);

        if (!isAdmin) {
            // Thêm tooltip để USER biết tại sao bị vô hiệu hóa
            String tooltip = "Chỉ ADMIN mới có quyền thực hiện chức năng này";
            btnScreenCapture.setToolTipText(tooltip);
            btnRemoteInput.setToolTipText(tooltip);
            btnShutdown.setToolTipText(tooltip);
            btnRestart.setToolTipText(tooltip);
            btnBlockWeb.setToolTipText(tooltip);
            btnSendMessage.setToolTipText(tooltip);

            System.out.println("[Dashboard] Role USER — đã vô hiệu hóa các nút điều khiển.");
        } else {
            System.out.println("[Dashboard] Role ADMIN — toàn quyền điều khiển.");
        }
    }

    // ─── Logout ──────────────────────────────────────────────────────────────────
    /**
     * Đăng xuất: xóa phiên, đóng Dashboard, mở lại LoginFrame.
     */
    private void performLogout() {
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Bạn có chắc muốn đăng xuất không?",
                "Xác nhận đăng xuất",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );

        if (confirm == JOptionPane.YES_OPTION) {
            System.out.println("[Dashboard] Đăng xuất user: " + currentUser.getUsername());
            SwingUtilities.invokeLater(() -> {
                LoginFrame loginFrame = new LoginFrame();
                loginFrame.setVisible(true);
                dispose(); // Đóng Dashboard
            });
        }
    }

    // ─── Helper tạo nút điều khiển ───────────────────────────────────────────────
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

        // Hover effect
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
}
