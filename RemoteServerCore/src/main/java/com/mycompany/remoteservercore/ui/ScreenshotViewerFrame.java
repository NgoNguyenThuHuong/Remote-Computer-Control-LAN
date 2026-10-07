package com.mycompany.remoteservercore.ui;

import com.mycompany.remoteservercore.features.ScreenCapturer;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

/**
 * Giao diện xem ảnh chụp màn hình nhận được từ Client (Issue 17 / Issue #38).
 * Thiết kế giao diện hiện đại theo chuẩn màu Catppuccin Dark Theme:
 *   - Hiển thị ảnh màn hình máy trạm trực quan, hỗ trợ cuộn hoặc vừa khung hình.
 *   - Hiển thị thông số máy trạm: Tên máy, IP, độ phân giải ảnh, thời điểm chụp.
 *   - Chức năng: Lưu ảnh về đĩa (Save PNG/JPG), Chụp lại (Refresh).
 */
public class ScreenshotViewerFrame extends JFrame {

    private final String clientIp;
    private final String clientHost;
    private final Consumer<String> refreshCallback;

    private BufferedImage currentImage;
    private JLabel imageDisplayLabel;
    private JLabel infoLabel;
    private JScrollPane imageScrollPane;
    private boolean fitToWindow = true;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public ScreenshotViewerFrame(String clientIp, String clientHost, BufferedImage initialImage, Consumer<String> refreshCallback) {
        this.clientIp = clientIp;
        this.clientHost = (clientHost != null && !clientHost.isEmpty()) ? clientHost : clientIp;
        this.currentImage = initialImage;
        this.refreshCallback = refreshCallback;

        initUI();
        if (initialImage != null) {
            updateImage(initialImage);
        }
    }

    private void initUI() {
        setTitle("📷 Chụp màn hình máy trạm - " + clientHost + " (" + clientIp + ")");
        setSize(980, 680);
        setMinimumSize(new Dimension(640, 480));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(new Color(30, 30, 46));

        // Header Panel
        mainPanel.add(buildHeaderPanel(), BorderLayout.NORTH);

        // Center Panel - Scrollable Image Display
        imageDisplayLabel = new JLabel("", SwingConstants.CENTER);
        imageDisplayLabel.setBackground(new Color(24, 24, 37));
        imageDisplayLabel.setOpaque(true);

        imageScrollPane = new JScrollPane(imageDisplayLabel);
        imageScrollPane.setBorder(BorderFactory.createLineBorder(new Color(69, 71, 90)));
        imageScrollPane.getViewport().setBackground(new Color(24, 24, 37));
        mainPanel.add(imageScrollPane, BorderLayout.CENTER);

        // Footer / Action Bar
        mainPanel.add(buildFooterPanel(), BorderLayout.SOUTH);

        setContentPane(mainPanel);
    }

    private JPanel buildHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(24, 24, 37));
        header.setBorder(new EmptyBorder(12, 16, 12, 16));

        JLabel titleLabel = new JLabel("📷 ẢNH CHỤP MÀN HÌNH MÁY TRẠM: " + clientHost.toUpperCase());
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));
        titleLabel.setForeground(new Color(137, 180, 250));

        infoLabel = new JLabel("IP: " + clientIp + "  |  Đang tải dữ liệu ảnh...");
        infoLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        infoLabel.setForeground(new Color(166, 173, 200));

        header.add(titleLabel, BorderLayout.NORTH);
        header.add(infoLabel, BorderLayout.SOUTH);
        return header;
    }

    private JPanel buildFooterPanel() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(new Color(24, 24, 37));
        footer.setBorder(new EmptyBorder(10, 16, 10, 16));

        JPanel leftActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftActions.setOpaque(false);

        JButton btnFitToggle = createStyledButton("🔍 Vừa khung / Kích thước gốc", new Color(69, 71, 90), Color.WHITE);
        btnFitToggle.addActionListener(e -> {
            fitToWindow = !fitToWindow;
            if (currentImage != null) {
                renderImage();
            }
        });
        leftActions.add(btnFitToggle);

        JPanel rightActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightActions.setOpaque(false);

        JButton btnRefresh = createStyledButton("🔄 Chụp lại", new Color(137, 180, 250), new Color(17, 17, 27));
        btnRefresh.addActionListener(e -> {
            if (refreshCallback != null) {
                infoLabel.setText("IP: " + clientIp + "  |  Đang gửi yêu cầu chụp lại ảnh màn hình...");
                refreshCallback.accept(clientIp);
            }
        });

        JButton btnSave = createStyledButton("💾 Lưu ảnh về máy", new Color(166, 227, 161), new Color(17, 17, 27));
        btnSave.addActionListener(e -> saveImage());

        JButton btnClose = createStyledButton("Đóng", new Color(88, 91, 112), Color.WHITE);
        btnClose.addActionListener(e -> dispose());

        rightActions.add(btnRefresh);
        rightActions.add(btnSave);
        rightActions.add(btnClose);

        footer.add(leftActions, BorderLayout.WEST);
        footer.add(rightActions, BorderLayout.EAST);
        return footer;
    }

    private JButton createStyledButton(String text, Color bg, Color fg) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setFocusPainted(false);
        btn.setBorder(new EmptyBorder(7, 14, 7, 14));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    /**
     * Cập nhật và hiển thị ảnh mới nhận được từ Client.
     *
     * @param image BufferedImage ảnh mới
     */
    public void updateImage(BufferedImage image) {
        this.currentImage = image;
        String nowStr = LocalDateTime.now().format(TIME_FORMATTER);
        int w = (image != null) ? image.getWidth() : 0;
        int h = (image != null) ? image.getHeight() : 0;
        infoLabel.setText("IP: " + clientIp + "  |  Độ phân giải: " + w + "x" + h + " px  |  Thời điểm chụp: " + nowStr);

        renderImage();
    }

    private void renderImage() {
        if (currentImage == null) {
            imageDisplayLabel.setIcon(null);
            imageDisplayLabel.setText("Không có dữ liệu ảnh");
            imageDisplayLabel.setForeground(new Color(243, 139, 168));
            return;
        }

        imageDisplayLabel.setText("");
        if (fitToWindow) {
            int viewportW = Math.max(imageScrollPane.getWidth() - 20, 600);
            int viewportH = Math.max(imageScrollPane.getHeight() - 20, 400);

            double scaleX = (double) viewportW / currentImage.getWidth();
            double scaleY = (double) viewportH / currentImage.getHeight();
            double scale = Math.min(scaleX, scaleY);

            if (scale < 1.0) {
                int scaledW = (int) (currentImage.getWidth() * scale);
                int scaledH = (int) (currentImage.getHeight() * scale);
                Image scaled = currentImage.getScaledInstance(scaledW, scaledH, Image.SCALE_SMOOTH);
                imageDisplayLabel.setIcon(new ImageIcon(scaled));
                return;
            }
        }
        imageDisplayLabel.setIcon(new ImageIcon(currentImage));
    }

    private void saveImage() {
        if (currentImage == null) {
            JOptionPane.showMessageDialog(this, "Chưa có ảnh màn hình để lưu!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Lưu ảnh chụp màn hình Client");
        String defaultName = "screenshot_" + clientHost.replaceAll("[^a-zA-Z0-9_-]", "_") + "_" + System.currentTimeMillis() + ".png";
        fileChooser.setSelectedFile(new File(defaultName));
        fileChooser.setFileFilter(new FileNameExtensionFilter("PNG Images (*.png)", "png"));
        fileChooser.addChoosableFileFilter(new FileNameExtensionFilter("JPEG Images (*.jpg)", "jpg"));

        int choice = fileChooser.showSaveDialog(this);
        if (choice == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            String path = selectedFile.getAbsolutePath();
            String format = "png";
            if (path.toLowerCase().endsWith(".jpg") || path.toLowerCase().endsWith(".jpeg")) {
                format = "jpg";
            } else if (!path.toLowerCase().endsWith(".png")) {
                selectedFile = new File(path + ".png");
            }

            boolean saved = ScreenCapturer.saveImageToFile(currentImage, selectedFile, format);
            if (saved) {
                JOptionPane.showMessageDialog(this, "Đã lưu ảnh màn hình thành công tại:\n" + selectedFile.getAbsolutePath(),
                        "Lưu ảnh thành công", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, "Lỗi khi lưu ảnh ra tệp tin!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
