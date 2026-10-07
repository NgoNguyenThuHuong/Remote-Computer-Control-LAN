package com.mycompany.remoteservercore.features;

import java.awt.AWTException;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Base64;
import javax.imageio.ImageIO;

/**
 * Xử lý tác vụ chụp ảnh màn hình (Screenshot) cho Client và chuyển đổi dữ liệu.
 * Hỗ trợ nén ảnh sang định dạng JPEG Base64 để truyền qua Socket TCP với độ trễ thấp,
 * đồng thời tương thích cả môi trường kiểm thử không có đồ họa (Headless).
 */
public class ScreenCapturer {

    private static final String DEFAULT_FORMAT = "jpg";

    /**
     * Chụp ảnh toàn bộ màn hình máy trạm.
     * Tự động sinh ảnh mô phỏng nếu chạy trong môi trường Headless (CI / Unit Test).
     *
     * @return BufferedImage ảnh chụp màn hình
     * @throws AWTException nếu Robot không thể khởi tạo
     */
    public static BufferedImage captureScreen() throws AWTException {
        if (GraphicsEnvironment.isHeadless()) {
            BufferedImage testImage = new BufferedImage(1280, 720, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = testImage.createGraphics();
            g.setColor(new Color(30, 30, 46));
            g.fillRect(0, 0, 1280, 720);
            g.setColor(new Color(203, 166, 247));
            g.setFont(new Font("Segoe UI", Font.BOLD, 28));
            g.drawString("MÀN HÌNH CLIENT (MÔ PHỎNG HEADLESS)", 350, 340);
            g.setColor(new Color(166, 227, 161));
            g.setFont(new Font("Segoe UI", Font.PLAIN, 18));
            g.drawString("Chụp lúc: " + System.currentTimeMillis(), 480, 390);
            g.dispose();
            return testImage;
        }

        Robot robot = new Robot();
        Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        return robot.createScreenCapture(screenRect);
    }

    /**
     * Chụp màn hình và mã hóa thành chuỗi Base64 định dạng JPG.
     *
     * @return Chuỗi Base64 đại diện cho ảnh màn hình
     * @throws Exception nếu xảy ra lỗi trong quá trình chụp hoặc nén ảnh
     */
    public static String captureScreenAsBase64() throws Exception {
        return captureScreenAsBase64(DEFAULT_FORMAT);
    }

    /**
     * Chụp màn hình và mã hóa thành chuỗi Base64 theo định dạng chỉ định (jpg, png).
     *
     * @param format định dạng ảnh ("jpg" hoặc "png")
     * @return Chuỗi Base64
     * @throws Exception nếu lỗi
     */
    public static String captureScreenAsBase64(String format) throws Exception {
        BufferedImage image = captureScreen();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        boolean written = ImageIO.write(image, format, baos);
        if (!written) {
            // Thử lại với định dạng PNG dự phòng
            ImageIO.write(image, "png", baos);
        }
        byte[] bytes = baos.toByteArray();
        return Base64.getEncoder().encodeToString(bytes);
    }

    /**
     * Giải mã chuỗi Base64 thành đối tượng BufferedImage để hiển thị trên UI Server.
     *
     * @param base64Data Chuỗi dữ liệu Base64
     * @return BufferedImage ảnh giải mã
     * @throws IOException nếu dữ liệu ảnh hỏng hoặc không đúng chuẩn
     */
    public static BufferedImage base64ToImage(String base64Data) throws IOException {
        if (base64Data == null || base64Data.trim().isEmpty()) {
            throw new IOException("Dữ liệu ảnh Base64 rỗng");
        }
        byte[] imageBytes = Base64.getDecoder().decode(base64Data.trim());
        try (ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes)) {
            BufferedImage image = ImageIO.read(bais);
            if (image == null) {
                throw new IOException("Không thể đọc định dạng ảnh từ dữ liệu giải mã");
            }
            return image;
        }
    }

    /**
     * Lưu đối tượng BufferedImage ra file trên đĩa.
     *
     * @param image BufferedImage cần lưu
     * @param targetFile File đích
     * @param format Định dạng ("png", "jpg")
     * @return true nếu lưu thành công, false nếu thất bại
     */
    public static boolean saveImageToFile(BufferedImage image, File targetFile, String format) {
        if (image == null || targetFile == null) {
            return false;
        }
        try {
            return ImageIO.write(image, format, targetFile);
        } catch (IOException e) {
            System.err.println("[ScreenCapturer] Lỗi lưu file ảnh: " + e.getMessage());
            return false;
        }
    }
}
