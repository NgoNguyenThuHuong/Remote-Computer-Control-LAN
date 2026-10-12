package com.mycompany.remoteservercore.ui;

import com.mycompany.remoteservercore.core.ClientManager;
import com.mycompany.remoteservercore.features.ScreenCapturer;
import com.mycompany.remoteservercore.model.MessagePacket;
import com.mycompany.remoteservercore.model.RemoteFrameData;
import com.mycompany.remoteservercore.model.RemoteInputEvent;
import com.mycompany.remoteservercore.protocol.JsonUtils;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Giao diện Điều khiển máy tính từ xa phong cách UltraViewer / TeamViewer (Remote Desktop).
 * Hỗ trợ:
 *  - Truyền phát luồng màn hình trực tiếp theo thời gian thực (Live Screen Streaming).
 *  - Chế độ điều khiển toàn diện Chuột (Click, Move, Drag, Scroll) và Bàn phím với java.awt.Robot.
 *  - Nút chuyển đổi nhanh chế độ Chỉ xem (View-Only) hoặc Điều khiển trực tiếp (Control Mode).
 *  - Menu phím tắt nhanh (Windows Key, Task Manager, Alt+Tab, Enter, Esc...).
 *  - Tự động co giãn màn hình giữ nguyên tỷ lệ (Aspect-Ratio Scaled Canvas).
 *  - Đo đạc và hiển thị FPS, độ phân giải thực của máy trạm.
 */
public class ScreenViewFrame extends JFrame implements ClientManager.RemoteFrameListener {

    private static final Map<String, ScreenViewFrame> activeInstances = new ConcurrentHashMap<>();

    private final String clientIp;
    private final String clientHostName;

    // Trạng thái điều khiển
    private volatile boolean controlEnabled = true;
    private volatile boolean fitToWindow = true;
    private volatile boolean isReceivingStream = false;

    // Thông số hiển thị & FPS
    private BufferedImage currentFrame = null;
    private int originalWidth = 1920;
    private int originalHeight = 1080;
    private int frameCount = 0;
    private int currentFps = 0;
    private long lastFpsTime = System.currentTimeMillis();

    // Throttle gửi sự kiện di chuột (tối đa 30 lần / giây)
    private long lastMouseMoveTime = 0L;
    private static final long MOUSE_MOVE_THROTTLE_MS = 33L;

    // Các thành phần UI
    private RemoteCanvas canvasPanel;
    private JLabel lblStatus;
    private JLabel lblResolution;
    private JLabel lblFps;
    private JToggleButton btnToggleControl;
    private JToggleButton btnToggleFit;
    private JButton btnQuickKeys;
    private JButton btnSnapshot;
    private JButton btnStop;

    public static synchronized ScreenViewFrame openSession(String clientIp, String clientHostName) {
        ScreenViewFrame existing = activeInstances.get(clientIp);
        if (existing != null && existing.isDisplayable()) {
            existing.setVisible(true);
            existing.toFront();
            return existing;
        }

        ScreenViewFrame frame = new ScreenViewFrame(clientIp, clientHostName);
        activeInstances.put(clientIp, frame);
        frame.setVisible(true);
        frame.toFront();
        return frame;
    }

    public ScreenViewFrame(String clientIp, String clientHostName) {
        this.clientIp = clientIp;
        this.clientHostName = (clientHostName != null && !clientHostName.isEmpty()) ? clientHostName : clientIp;

        initComponents();
        startRemoteSession();
    }

    private void initComponents() {
        setTitle("🖥️ UltraViewer Remote Desktop — " + clientHostName + " (" + clientIp + ")");
        setSize(1150, 750);
        setMinimumSize(new Dimension(800, 550));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(new Color(24, 24, 37));
        setContentPane(root);

        root.add(buildTopToolbar(), BorderLayout.NORTH);

        canvasPanel = new RemoteCanvas();
        root.add(canvasPanel, BorderLayout.CENTER);

        // Đóng cửa sổ an toàn
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                stopRemoteSession();
            }
        });
    }

    private JPanel buildTopToolbar() {
        JPanel toolbar = new JPanel(new BorderLayout(10, 0));
        toolbar.setBackground(new Color(49, 50, 68));
        toolbar.setBorder(new EmptyBorder(8, 14, 8, 14));

        // Bên trái: Thông tin máy trạm + Badge
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        leftPanel.setOpaque(false);

        JLabel lblTitle = new JLabel("🔴 UltraControl: " + clientHostName);
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblTitle.setForeground(new Color(203, 166, 247));

        lblStatus = new JLabel("● Đang kết nối...");
        lblStatus.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblStatus.setForeground(new Color(249, 226, 175));

        lblResolution = new JLabel("[Độ phân giải: ---]");
        lblResolution.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblResolution.setForeground(new Color(186, 194, 222));

        lblFps = new JLabel("[FPS: 0]");
        lblFps.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblFps.setForeground(new Color(166, 227, 161));

        leftPanel.add(lblTitle);
        leftPanel.add(lblStatus);
        leftPanel.add(lblResolution);
        leftPanel.add(lblFps);

        // Bên phải: Các nút điều khiển
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightPanel.setOpaque(false);

        // Nút Toggle Quyền điều khiển chuột & phím
        btnToggleControl = new JToggleButton("🟢 Điều khiển: BẬT");
        btnToggleControl.setSelected(true);
        btnToggleControl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnToggleControl.setBackground(new Color(166, 227, 161));
        btnToggleControl.setForeground(new Color(30, 30, 46));
        btnToggleControl.setFocusPainted(false);
        btnToggleControl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnToggleControl.addActionListener(e -> {
            controlEnabled = btnToggleControl.isSelected();
            if (controlEnabled) {
                btnToggleControl.setText("🟢 Điều khiển: BẬT");
                btnToggleControl.setBackground(new Color(166, 227, 161));
                btnToggleControl.setForeground(new Color(30, 30, 46));
            } else {
                btnToggleControl.setText("⚪ Chỉ xem: TẮT");
                btnToggleControl.setBackground(new Color(88, 91, 112));
                btnToggleControl.setForeground(new Color(205, 214, 244));
            }
            canvasPanel.requestFocusInWindow();
        });

        // Nút Phím tắt nhanh (Special Keys)
        btnQuickKeys = new JButton("⚡ Phím tắt ▼");
        btnQuickKeys.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btnQuickKeys.setBackground(new Color(69, 71, 90));
        btnQuickKeys.setForeground(new Color(205, 214, 244));
        btnQuickKeys.setFocusPainted(false);
        btnQuickKeys.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnQuickKeys.addActionListener(e -> showQuickKeysMenu());

        // Nút Toggle Fit màn hình
        btnToggleFit = new JToggleButton("📐 Vừa khung");
        btnToggleFit.setSelected(true);
        btnToggleFit.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btnToggleFit.setBackground(new Color(69, 71, 90));
        btnToggleFit.setForeground(new Color(205, 214, 244));
        btnToggleFit.setFocusPainted(false);
        btnToggleFit.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnToggleFit.addActionListener(e -> {
            fitToWindow = btnToggleFit.isSelected();
            canvasPanel.repaint();
            canvasPanel.requestFocusInWindow();
        });

        // Nút Lưu ảnh nhanh
        btnSnapshot = new JButton("📸 Lưu ảnh");
        btnSnapshot.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btnSnapshot.setBackground(new Color(69, 71, 90));
        btnSnapshot.setForeground(new Color(205, 214, 244));
        btnSnapshot.setFocusPainted(false);
        btnSnapshot.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnSnapshot.addActionListener(e -> saveSnapshot());

        // Nút Dừng kết nối
        btnStop = new JButton("⏹ Dừng");
        btnStop.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnStop.setBackground(new Color(243, 139, 168));
        btnStop.setForeground(new Color(30, 30, 46));
        btnStop.setFocusPainted(false);
        btnStop.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnStop.addActionListener(e -> stopRemoteSession());

        rightPanel.add(btnToggleControl);
        rightPanel.add(btnQuickKeys);
        rightPanel.add(btnToggleFit);
        rightPanel.add(btnSnapshot);
        rightPanel.add(btnStop);

        toolbar.add(leftPanel, BorderLayout.WEST);
        toolbar.add(rightPanel, BorderLayout.EAST);
        return toolbar;
    }

    private void showQuickKeysMenu() {
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(new Color(49, 50, 68));

        addMenuItem(menu, "Phím Windows (Start Menu)", KeyEvent.VK_WINDOWS);
        addMenuItem(menu, "Mở Task Manager (Ctrl + Shift + Esc)", -1);
        addMenuItem(menu, "Alt + Tab", -2);
        addMenuItem(menu, "Phím Escape (ESC)", KeyEvent.VK_ESCAPE);
        addMenuItem(menu, "Phím Enter", KeyEvent.VK_ENTER);
        addMenuItem(menu, "Phím Space", KeyEvent.VK_SPACE);

        menu.show(btnQuickKeys, 0, btnQuickKeys.getHeight());
    }

    private void addMenuItem(JPopupMenu menu, String title, int specialCode) {
        JMenuItem item = new JMenuItem(title);
        item.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        item.setBackground(new Color(49, 50, 68));
        item.setForeground(new Color(205, 214, 244));
        item.addActionListener(e -> {
            if (specialCode == -1) {
                // Ctrl + Shift + Esc
                sendKeyStroke(KeyEvent.VK_CONTROL, true);
                sendKeyStroke(KeyEvent.VK_SHIFT, true);
                sendKeyStroke(KeyEvent.VK_ESCAPE, true);
                sendKeyStroke(KeyEvent.VK_ESCAPE, false);
                sendKeyStroke(KeyEvent.VK_SHIFT, false);
                sendKeyStroke(KeyEvent.VK_CONTROL, false);
            } else if (specialCode == -2) {
                // Alt + Tab
                sendKeyStroke(KeyEvent.VK_ALT, true);
                sendKeyStroke(KeyEvent.VK_TAB, true);
                sendKeyStroke(KeyEvent.VK_TAB, false);
                sendKeyStroke(KeyEvent.VK_ALT, false);
            } else if (specialCode > 0) {
                sendKeyStroke(specialCode, true);
                sendKeyStroke(specialCode, false);
            }
            canvasPanel.requestFocusInWindow();
        });
        menu.add(item);
    }

    /**
     * Bắt đầu phiên điều khiển từ xa: Đăng ký nhận frame và gửi lệnh START tới Client.
     */
    private void startRemoteSession() {
        ClientManager.getInstance().addRemoteFrameListener(this);
        MessagePacket startPacket = MessagePacket.createRemoteStart(clientIp);
        boolean sent = ClientManager.getInstance().sendTo(clientIp, startPacket);

        if (!sent) {
            lblStatus.setText("❌ Không thể kết nối tới máy trạm!");
            lblStatus.setForeground(new Color(243, 139, 168));
            JOptionPane.showMessageDialog(this,
                    "Không thể bắt đầu phiên điều khiển!\nClient [" + clientHostName + " (" + clientIp + ")] có thể đã mất kết nối.",
                    "Lỗi kết nối", JOptionPane.ERROR_MESSAGE);
        } else {
            lblStatus.setText("● Đang yêu cầu luồng màn hình...");
        }
    }

    /**
     * Dừng phiên điều khiển và đóng cửa sổ.
     */
    private void stopRemoteSession() {
        try {
            MessagePacket stopPacket = MessagePacket.createRemoteStop(clientIp);
            ClientManager.getInstance().sendTo(clientIp, stopPacket);
        } catch (Exception ignored) {}

        ClientManager.getInstance().removeRemoteFrameListener(this);
        activeInstances.remove(clientIp);
        dispose();
    }

    /**
     * Nhận khung hình mới từ Client gửi qua Socket.
     */
    @Override
    public void onRemoteFrameReceived(String senderIp, RemoteFrameData frameData) {
        if (!clientIp.equalsIgnoreCase(senderIp) || frameData == null) {
            return;
        }

        try {
            BufferedImage image = ScreenCapturer.base64ToImage(frameData.getBase64Image());
            if (image != null) {
                this.currentFrame = image;
                this.originalWidth = frameData.getWidth() > 0 ? frameData.getWidth() : image.getWidth();
                this.originalHeight = frameData.getHeight() > 0 ? frameData.getHeight() : image.getHeight();

                // Cập nhật FPS
                frameCount++;
                long now = System.currentTimeMillis();
                if (now - lastFpsTime >= 1000L) {
                    currentFps = frameCount;
                    frameCount = 0;
                    lastFpsTime = now;
                }

                SwingUtilities.invokeLater(() -> {
                    if (!isReceivingStream) {
                        isReceivingStream = true;
                        lblStatus.setText("● Đang phát trực tiếp");
                        lblStatus.setForeground(new Color(166, 227, 161));
                    }
                    lblResolution.setText("[" + originalWidth + "x" + originalHeight + " px]");
                    lblFps.setText("[FPS: " + currentFps + "]");
                    canvasPanel.repaint();
                });
            }
        } catch (Exception e) {
            System.err.println("[ScreenViewFrame] Lỗi giải mã frame: " + e.getMessage());
        }
    }

    /**
     * Lưu ảnh hiện tại thành file PNG về máy tính.
     */
    private void saveSnapshot() {
        if (currentFrame == null) {
            JOptionPane.showMessageDialog(this, "Chưa có hình ảnh màn hình để lưu!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        String timeStr = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        fileChooser.setSelectedFile(new File("RemoteCapture_" + clientHostName + "_" + timeStr + ".png"));

        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                File target = fileChooser.getSelectedFile();
                if (!target.getName().toLowerCase().endsWith(".png")) {
                    target = new File(target.getAbsolutePath() + ".png");
                }
                ImageIO.write(currentFrame, "png", target);
                JOptionPane.showMessageDialog(this, "Đã lưu ảnh chụp thành công:\n" + target.getAbsolutePath(), "Thành công", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Lỗi khi lưu ảnh: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Gửi gói tin RemoteInputEvent tới Client qua Socket.
     */
    private void sendInputEvent(RemoteInputEvent event) {
        if (!controlEnabled) return;
        try {
            MessagePacket packet = MessagePacket.createRemoteInput(clientIp, JsonUtils.toJson(event));
            ClientManager.getInstance().sendTo(clientIp, packet);
        } catch (Exception ignored) {}
    }

    private void sendKeyStroke(int keyCode, boolean isPressed) {
        RemoteInputEvent ev = isPressed ? RemoteInputEvent.keyPress(keyCode) : RemoteInputEvent.keyRelease(keyCode);
        sendInputEvent(ev);
    }

    /**
     * Panel tùy biến hiển thị màn hình và bắt tương tác Chuột/Phím.
     */
    private class RemoteCanvas extends JPanel implements MouseListener, MouseMotionListener, MouseWheelListener, KeyListener {

        public RemoteCanvas() {
            setBackground(new Color(17, 17, 27));
            setFocusable(true);
            addMouseListener(this);
            addMouseMotionListener(this);
            addMouseWheelListener(this);
            addKeyListener(this);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            if (currentFrame == null) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setColor(new Color(205, 214, 244));
                g2.setFont(new Font("Segoe UI", Font.BOLD, 18));
                String text = "Đang kết nối tới máy trạm " + clientHostName + " và nạp luồng màn hình...";
                FontMetrics fm = g2.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(text)) / 2;
                int y = getHeight() / 2;
                g2.drawString(text, x, y);
                return;
            }

            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            Rectangle dest = calculateDrawBounds();
            g2.drawImage(currentFrame, dest.x, dest.y, dest.width, dest.height, null);

            // Nếu đang bật chế độ điều khiển, vẽ viền chỉ báo nhẹ
            if (controlEnabled) {
                g2.setColor(new Color(166, 227, 161, 80));
                g2.drawRect(dest.x, dest.y, dest.width - 1, dest.height - 1);
            }
        }

        private Rectangle calculateDrawBounds() {
            int panelW = getWidth();
            int panelH = getHeight();
            if (currentFrame == null || panelW <= 0 || panelH <= 0) {
                return new Rectangle(0, 0, panelW, panelH);
            }

            int imgW = currentFrame.getWidth();
            int imgH = currentFrame.getHeight();

            if (!fitToWindow) {
                return new Rectangle(0, 0, imgW, imgH);
            }

            double scale = Math.min((double) panelW / imgW, (double) panelH / imgH);
            int drawW = Math.max(1, (int) (imgW * scale));
            int drawH = Math.max(1, (int) (imgH * scale));
            int drawX = (panelW - drawW) / 2;
            int drawY = (panelH - drawH) / 2;

            return new Rectangle(drawX, drawY, drawW, drawH);
        }

        private Point mapToClientCoordinates(int mouseX, int mouseY) {
            Rectangle bounds = calculateDrawBounds();
            if (currentFrame == null || bounds.width <= 0 || bounds.height <= 0) {
                return null;
            }

            if (mouseX < bounds.x || mouseX > bounds.x + bounds.width ||
                mouseY < bounds.y || mouseY > bounds.y + bounds.height) {
                return null;
            }

            double relX = (double) (mouseX - bounds.x) / bounds.width;
            double relY = (double) (mouseY - bounds.y) / bounds.height;

            int clientX = (int) Math.round(relX * originalWidth);
            int clientY = (int) Math.round(relY * originalHeight);

            clientX = Math.max(0, Math.min(originalWidth - 1, clientX));
            clientY = Math.max(0, Math.min(originalHeight - 1, clientY));

            return new Point(clientX, clientY);
        }

        private int convertButtonMask(int button) {
            switch (button) {
                case MouseEvent.BUTTON1:
                    return InputEvent.BUTTON1_DOWN_MASK;
                case MouseEvent.BUTTON2:
                    return InputEvent.BUTTON2_DOWN_MASK;
                case MouseEvent.BUTTON3:
                    return InputEvent.BUTTON3_DOWN_MASK;
                default:
                    return InputEvent.BUTTON1_DOWN_MASK;
            }
        }

        // Mouse Events
        @Override
        public void mousePressed(MouseEvent e) {
            requestFocusInWindow();
            Point p = mapToClientCoordinates(e.getX(), e.getY());
            if (p != null) {
                int mask = convertButtonMask(e.getButton());
                sendInputEvent(RemoteInputEvent.mousePress(p.x, p.y, mask));
            }
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            Point p = mapToClientCoordinates(e.getX(), e.getY());
            if (p != null) {
                int mask = convertButtonMask(e.getButton());
                sendInputEvent(RemoteInputEvent.mouseRelease(p.x, p.y, mask));
            }
        }

        @Override
        public void mouseMoved(MouseEvent e) {
            long now = System.currentTimeMillis();
            if (now - lastMouseMoveTime < MOUSE_MOVE_THROTTLE_MS) {
                return;
            }
            lastMouseMoveTime = now;

            Point p = mapToClientCoordinates(e.getX(), e.getY());
            if (p != null) {
                sendInputEvent(RemoteInputEvent.mouseMove(p.x, p.y));
            }
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            long now = System.currentTimeMillis();
            if (now - lastMouseMoveTime < MOUSE_MOVE_THROTTLE_MS) {
                return;
            }
            lastMouseMoveTime = now;

            Point p = mapToClientCoordinates(e.getX(), e.getY());
            if (p != null) {
                sendInputEvent(RemoteInputEvent.mouseMove(p.x, p.y));
            }
        }

        @Override
        public void mouseWheelMoved(MouseWheelEvent e) {
            Point p = mapToClientCoordinates(e.getX(), e.getY());
            if (p != null) {
                sendInputEvent(RemoteInputEvent.mouseWheel(p.x, p.y, e.getWheelRotation()));
            }
        }

        // Key Events
        @Override
        public void keyPressed(KeyEvent e) {
            sendInputEvent(RemoteInputEvent.keyPress(e.getKeyCode()));
        }

        @Override
        public void keyReleased(KeyEvent e) {
            sendInputEvent(RemoteInputEvent.keyRelease(e.getKeyCode()));
        }

        @Override public void keyTyped(KeyEvent e) {}
        @Override public void mouseClicked(MouseEvent e) {}
        @Override public void mouseEntered(MouseEvent e) {}
        @Override public void mouseExited(MouseEvent e) {}
    }
}
