# 🏢 Hệ thống Quản lý và Giám sát Máy tính Phòng ban (Client-Server LAN)

Dự án phát triển bằng **Java (Maven)**, sử dụng **TCP Socket** đa luồng, **SQLite** và giao diện **Java Swing** nhằm quản lý nội bộ trong mạng LAN.

---

## 👥 Phân công thành viên & Nhánh phát triển (Git Branches)

- **TV1 (Phát):** Network & Server Core (`feature/phat-server-core`) - Xử lý kết nối Socket đa luồng, điều phối lệnh.
- **TV2 (Hương):** Client Agent (`feature/huong-agent`) - Thu thập thông tin máy nhân sự, chụp màn hình, chặn web.
- **TV3 (Hợp):** Giao diện quản trị (`feature/hop-ui`) - Xây dựng Dashboard Java Swing, giao diện điều khiển.
- **TV4 (Nhung):** Cơ sở dữ liệu & Bảo mật (`feature/nhung-database`) - Quản lý SQLite JDBC, log hoạt động, thông báo.

---

## 📂 Toàn bộ Cấu trúc Thư mục & Khung xương Dự án (Full Package & File Architecture)

Toàn bộ mã nguồn nằm trong gói gốc `com.mycompany.remoteservercore`:

```text
src/main/java/com/mycompany/remoteservercore/
│
├── core/                        📌 Phụ trách mạng & tiến trình kết nối
│   ├── MainServer.java                 # Khởi tạo ServerSocket, lắng nghe cổng 9999, quản lý ExecutorService
│   ├── ClientHandler.java              # Luồng xử lý độc lập cho từng Agent kết nối vào Server
│   ├── ClientManager.java              # Quản lý danh sách các máy nhân sự online/offline
│   └── AgentCore.java                  # Tiến trình chạy phía Client (Agent): heartbeat, tự động kết nối lại
│
├── model/                       📌 Định nghĩa các đối tượng dữ liệu dùng chung (POJO)
│   ├── ClientInfo.java                 # Thông tin phần cứng, IP, tên máy, CPU, RAM, OS
│   ├── MessagePacket.java              # Khuôn mẫu gói tin truyền tải chung qua JSON
│   ├── User.java                       # Thông tin tài khoản đăng nhập và phân quyền
│   └── LogEntry.java                   # Cấu trúc lưu vết lịch sử hoạt động phòng ban
│
├── protocol/                    📌 Xử lý giao thức truyền tin
│   ├── PacketRouter.java               # Bộ điều hướng mã lệnh từ gói tin JSON đến các chức năng
│   └── JsonUtils.java                  # Thư viện hỗ trợ serialize/deserialize JSON
│
├── database/                    📌 Quản lý lưu trữ dữ liệu (SQLite + JDBC)
│   ├── DatabaseManager.java            # Khởi tạo và quản lý kết nối cơ sở dữ liệu SQLite
│   ├── UserDAO.java                    # Thao tác truy vấn dữ liệu tài khoản và phân quyền
│   └── LogDAO.java                     # Ghi nhận log và lịch sử hoạt động vào CSDL
│
├── ui/                          📌 Giao diện quản trị phía Server (Java Swing)
│   ├── LoginFrame.java                 # Màn hình đăng nhập hệ thống
│   ├── DashboardFrame.java             # Màn hình chính quản lý danh sách máy nhân sự
│   ├── ChatFrame.java                  # Giao diện nhắn tin/chat 2 chiều với nhân sự
│   ├── ScreenViewFrame.java            # Giao diện xem màn hình máy nhân sự theo thời gian thực
│   ├── BlockedWebFrame.java            # Giao diện quản lý danh sách website bị chặn
│   └── LogFrame.java                   # Giao diện xem lịch sử hoạt động và log sự kiện
│
└── features/                    📌 Các module tính năng chuyên sâu hệ thống
    ├── ScreenCapturer.java             # Xử lý chụp ảnh màn hình máy nhân sự
    ├── WebBlocker.java                 # Áp dụng và quản lý danh sách website bị chặn trên Agent
    └── SystemControl.java              # Thực thi lệnh điều khiển từ xa (khóa máy, tắt máy, restart)
