package com.mycompany.remoteservercore.ui;

import com.mycompany.remoteservercore.core.AuthService;
import com.mycompany.remoteservercore.model.User;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;

/**
 * Màn hình đăng nhập của Server.
 *
 * <p>Tính năng:
 * <ul>
 *   <li>Ô nhập username và password.</li>
 *   <li>Nhấn Enter hoặc nút Login để đăng nhập.</li>
 *   <li>Sai thông tin → hiện JOptionPane, xóa ô password, ở lại form.</li>
 *   <li>Sai 5 lần liên tiếp → khóa tạm 30 giây.</li>
 *   <li>Đăng nhập thành công → mở {@link DashboardFrame}.</li>
 * </ul>
 */
public class LoginFrame extends JFrame {

    // ─── Giới hạn số lần sai trước khi khóa ────────────────────────────────────
    private static final int    MAX_FAILED_ATTEMPTS = 5;
    private static final long   LOCKOUT_DURATION_MS = 30_000L; // 30 giây

    // ─── Components ─────────────────────────────────────────────────────────────
    private JTextField     usernameField;
    private JPasswordField passwordField;
    private JButton        loginButton;
    private JLabel         statusLabel;

    // ─── Trạng thái đăng nhập ───────────────────────────────────────────────────
    private int  failedAttempts = 0;
    private long lockoutUntil   = 0L;

    // ─── Service xác thực ───────────────────────────────────────────────────────
    private final AuthService authService;

    public LoginFrame() {
        this.authService = new AuthService();
        initComponents();
    }

    // ─── Khởi tạo giao diện ─────────────────────────────────────────────────────
    private void initComponents() {
        setTitle("Đăng nhập — Hệ thống Quản lý Phòng Ban");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);

        // ── Panel chứa form ──
        JPanel mainPanel = new JPanel(new BorderLayout(0, 0));
        mainPanel.setBackground(new Color(30, 30, 46));

        // Header
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        headerPanel.setBackground(new Color(30, 30, 46));
        headerPanel.setBorder(new EmptyBorder(30, 20, 10, 20));

        JLabel titleLabel = new JLabel("🖥  Remote Server Control");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 22));
        titleLabel.setForeground(new Color(203, 166, 247));
        headerPanel.add(titleLabel);

        JLabel subtitleLabel = new JLabel("Đăng nhập để tiếp tục");
        subtitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        subtitleLabel.setForeground(new Color(166, 173, 200));
        JPanel subPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        subPanel.setBackground(new Color(30, 30, 46));
        subPanel.add(subtitleLabel);

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(new Color(30, 30, 46));
        topPanel.add(headerPanel, BorderLayout.NORTH);
        topPanel.add(subPanel, BorderLayout.SOUTH);

        // ── Form panel ──
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBackground(new Color(49, 50, 68));
        formPanel.setBorder(new EmptyBorder(30, 40, 30, 40));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill      = GridBagConstraints.HORIZONTAL;
        gbc.insets    = new Insets(8, 0, 8, 0);
        gbc.gridwidth = 1;

        // Label username
        gbc.gridx = 0; gbc.gridy = 0;
        JLabel userLabel = createLabel("Tên đăng nhập:");
        formPanel.add(userLabel, gbc);

        // Ô username
        gbc.gridy = 1;
        usernameField = createTextField();
        usernameField.setName("usernameField");
        formPanel.add(usernameField, gbc);

        // Label password
        gbc.gridy = 2;
        JLabel passLabel = createLabel("Mật khẩu:");
        formPanel.add(passLabel, gbc);

        // Ô password
        gbc.gridy = 3;
        passwordField = new JPasswordField(20);
        styleTextField(passwordField);
        passwordField.setName("passwordField");
        formPanel.add(passwordField, gbc);

        // Nhãn trạng thái (hiện lỗi hoặc đang khóa)
        gbc.gridy = 4;
        statusLabel = new JLabel(" ");
        statusLabel.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        statusLabel.setForeground(new Color(243, 139, 168));
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        formPanel.add(statusLabel, gbc);

        // Nút Login
        gbc.gridy = 5;
        gbc.insets = new Insets(4, 0, 0, 0);
        loginButton = new JButton("Đăng nhập");
        loginButton.setName("loginButton");
        styleLoginButton(loginButton);
        formPanel.add(loginButton, gbc);

        // ── Ghép vào main ──
        JPanel centerWrapper = new JPanel(new GridBagLayout());
        centerWrapper.setBackground(new Color(30, 30, 46));
        centerWrapper.setBorder(new EmptyBorder(0, 40, 40, 40));
        centerWrapper.add(formPanel);

        mainPanel.add(topPanel,       BorderLayout.NORTH);
        mainPanel.add(centerWrapper,  BorderLayout.CENTER);

        setContentPane(mainPanel);
        pack();
        setMinimumSize(new Dimension(420, 380));
        setLocationRelativeTo(null); // Canh giữa màn hình

        // ── Sự kiện ──
        ActionListener loginAction = e -> performLogin();
        loginButton.addActionListener(loginAction);
        // Nhấn Enter trong bất kỳ ô nào cũng đăng nhập
        usernameField.addActionListener(loginAction);
        passwordField.addActionListener(loginAction);
    }

    // ─── Xử lý đăng nhập ────────────────────────────────────────────────────────
    private void performLogin() {
        // Kiểm tra đang bị khóa không
        if (System.currentTimeMillis() < lockoutUntil) {
            long remaining = (lockoutUntil - System.currentTimeMillis()) / 1000;
            statusLabel.setText("Tài khoản bị khóa. Thử lại sau " + remaining + "s.");
            return;
        }

        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());

        // Kiểm tra không để trống
        if (username.isEmpty() || password.isEmpty()) {
            statusLabel.setText("Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu.");
            passwordField.setText("");
            return;
        }

        // Gọi AuthService xác thực
        User user = authService.login(username, password);

        if (user != null) {
            // ── Đăng nhập thành công ──
            failedAttempts = 0;
            statusLabel.setText(" ");
            System.out.println("[Login] Đăng nhập thành công: " + user);

            // Mở Dashboard và truyền user đã xác thực
            SwingUtilities.invokeLater(() -> {
                DashboardFrame dashboard = new DashboardFrame(user);
                dashboard.setVisible(true);
                dispose(); // Đóng LoginFrame
            });

        } else {
            // ── Đăng nhập thất bại ──
            failedAttempts++;
            passwordField.setText(""); // Xóa ô password
            passwordField.requestFocus();

            if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
                // Khóa tạm thời
                lockoutUntil   = System.currentTimeMillis() + LOCKOUT_DURATION_MS;
                failedAttempts = 0; // Reset đếm sau khi khóa
                loginButton.setEnabled(false);
                statusLabel.setText("Sai quá 5 lần. Khóa 30 giây...");

                // Mở khóa sau 30s trên EDT
                Timer unlockTimer = new Timer((int) LOCKOUT_DURATION_MS, e -> {
                    loginButton.setEnabled(true);
                    statusLabel.setText("Bạn có thể thử lại.");
                });
                unlockTimer.setRepeats(false);
                unlockTimer.start();

            } else {
                int remaining = MAX_FAILED_ATTEMPTS - failedAttempts;
                statusLabel.setText("Sai tên đăng nhập hoặc mật khẩu. Còn " + remaining + " lần thử.");
                // Hiện thông báo JOptionPane
                JOptionPane.showMessageDialog(
                        this,
                        "Tên đăng nhập hoặc mật khẩu không đúng!\nCòn " + remaining + " lần thử trước khi bị khóa.",
                        "Đăng nhập thất bại",
                        JOptionPane.WARNING_MESSAGE
                );
            }
        }
    }

    // ─── Helpers tạo component ───────────────────────────────────────────────────

    private JLabel createLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        label.setForeground(new Color(205, 214, 244));
        return label;
    }

    private JTextField createTextField() {
        JTextField field = new JTextField(20);
        styleTextField(field);
        return field;
    }

    private void styleTextField(JTextField field) {
        field.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        field.setBackground(new Color(69, 71, 90));
        field.setForeground(new Color(205, 214, 244));
        field.setCaretColor(new Color(203, 166, 247));
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(88, 91, 112), 1),
                new EmptyBorder(6, 10, 6, 10)
        ));
        field.setPreferredSize(new Dimension(300, 36));
    }

    private void styleLoginButton(JButton button) {
        button.setFont(new Font("Segoe UI", Font.BOLD, 14));
        button.setBackground(new Color(137, 180, 250));
        button.setForeground(new Color(30, 30, 46));
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setPreferredSize(new Dimension(300, 40));

        // Hover effect
        button.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                if (button.isEnabled())
                    button.setBackground(new Color(180, 210, 255));
            }
            @Override public void mouseExited(MouseEvent e) {
                if (button.isEnabled())
                    button.setBackground(new Color(137, 180, 250));
            }
        });
    }
}
