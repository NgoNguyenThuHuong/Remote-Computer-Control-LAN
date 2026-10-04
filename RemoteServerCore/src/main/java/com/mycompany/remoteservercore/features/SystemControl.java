package com.mycompany.remoteservercore.features;

import com.mycompany.remoteservercore.model.MessagePacket;

/**
 * Module thực thi lệnh điều khiển hệ thống (LOCK, LOGOUT, RESTART, SHUTDOWN)
 * trên máy Client Agent theo đúng yêu cầu Issue #33.
 */
public class SystemControl {

    // Biến cấu hình chế độ thử nghiệm an toàn (Safe Mode)
    // Khi safeMode = true: Giả lập thực thi mà không gây khóa hay khởi động lại máy thật của lập trình viên.
    private static boolean safeMode = false;

    public static void setSafeMode(boolean enabled) {
        safeMode = enabled;
    }

    public static boolean isSafeMode() {
        return safeMode;
    }

    /**
     * Kiểm tra command truyền vào có hợp lệ hay không.
     *
     * @param command Tên lệnh (LOCK, LOGOUT, RESTART, SHUTDOWN)
     * @return true nếu lệnh hợp lệ
     */
    public static boolean isValidCommand(String command) {
        if (command == null || command.isBlank()) {
            return false;
        }
        String cmd = command.trim().toUpperCase();
        return cmd.equals(MessagePacket.CMD_LOCK) ||
               cmd.equals(MessagePacket.CMD_LOGOUT) ||
               cmd.equals(MessagePacket.CMD_RESTART) ||
               cmd.equals(MessagePacket.CMD_SHUTDOWN);
    }

    /**
     * Thực thi lệnh hệ thống theo đúng yêu cầu Issue #33.
     *
     * @param command Tên lệnh cần thực thi (LOCK, LOGOUT, RESTART, SHUTDOWN)
     * @return Chuỗi thông điệp kết quả trả về Server
     * @throws IllegalArgumentException Nếu lệnh không hợp lệ
     * @throws Exception Nếu xảy ra lỗi ngoại lệ trong quá trình gọi hệ điều hành
     */
    public static String execute(String command) throws Exception {
        if (!isValidCommand(command)) {
            throw new IllegalArgumentException("Lệnh không hợp lệ: " + command);
        }

        String cmd = command.trim().toUpperCase();
        System.out.println("[SystemControl] Tiếp nhận yêu cầu thực thi lệnh: " + cmd + " (SafeMode=" + safeMode + ")");

        // Nếu đang ở chế độ an toàn Safe Mode, trả kết quả mô phỏng
        if (safeMode) {
            return "[SAFE_MODE] Giả lập thực thi thành công lệnh hệ thống: " + cmd;
        }

        String os = System.getProperty("os.name").toLowerCase();
        ProcessBuilder builder;

        if (os.contains("win")) {
            switch (cmd) {
                case MessagePacket.CMD_LOCK:
                    // Khóa màn hình trên Windows
                    builder = new ProcessBuilder("rundll32.exe", "user32.dll,LockWorkStation");
                    break;
                case MessagePacket.CMD_LOGOUT:
                    // Đăng xuất tài khoản trên Windows
                    builder = new ProcessBuilder("shutdown", "/l");
                    break;
                case MessagePacket.CMD_RESTART:
                    // Khởi động lại máy trên Windows ngay lập tức
                    builder = new ProcessBuilder("shutdown", "/r", "/t", "0");
                    break;
                case MessagePacket.CMD_SHUTDOWN:
                    // Tắt máy trên Windows ngay lập tức
                    builder = new ProcessBuilder("shutdown", "/s", "/t", "0");
                    break;
                default:
                    throw new IllegalArgumentException("Lệnh không hỗ trợ trên Windows: " + cmd);
            }
        } else if (os.contains("nix") || os.contains("nux") || os.contains("mac")) {
            switch (cmd) {
                case MessagePacket.CMD_LOCK:
                    builder = new ProcessBuilder("gnome-screensaver-command", "-l");
                    break;
                case MessagePacket.CMD_LOGOUT:
                    builder = new ProcessBuilder("pkill", "-KILL", "-u", System.getProperty("user.name"));
                    break;
                case MessagePacket.CMD_RESTART:
                    builder = new ProcessBuilder("shutdown", "-r", "now");
                    break;
                case MessagePacket.CMD_SHUTDOWN:
                    builder = new ProcessBuilder("shutdown", "-h", "now");
                    break;
                default:
                    throw new IllegalArgumentException("Lệnh không hỗ trợ trên Unix/Mac: " + cmd);
            }
        } else {
            throw new UnsupportedOperationException("Hệ điều hành không được hỗ trợ: " + os);
        }

        Process process = builder.start();
        return "Thực thi thành công lệnh " + cmd + " trên hệ điều hành " + System.getProperty("os.name");
    }
}
