# Dự án: Remote-Computer-Control-LAN (Hệ thống Quản lý Máy tính Phòng ban)

## Thành viên nhóm & Nhiệm vụ:

- **TV1 (Phát):** Network & Server Core (TCP Socket, đa luồng, quản lý client).
- **TV2 (Hương):** Client Agent (Thu thập thông tin, chụp màn hình, chặn web).
- **TV3 (Hợp):** Giao diện quản trị (Java Swing, Dashboard, Chat UI).
- **TV4 (Nhung):** Cơ sở dữ liệu, Bảo mật & Telegram Bot (SQLite, JDBC, thông báo).

## Hướng dẫn chạy thử Server Core:

1. Mở project bằng Apache NetBeans (chọn dạng Maven Project).
2. Vào gói `com.mycompany.remoteservercore.core` mở file `MainServer.java`.
3. Chạy file (`Shift + F6`) để khởi động Server lắng nghe tại cổng 9999.
