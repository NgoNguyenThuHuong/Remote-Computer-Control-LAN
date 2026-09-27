# 🏢 Hệ thống Quản lý và Giám sát Máy tính Phòng ban (Client-Server LAN)

Ứng dụng quản lý và giám sát máy tính nhân sự từ xa trong mạng LAN nội bộ, phát triển dựa trên mô hình Client-Server sử dụng **Java (Maven)**, **TCP Socket đa luồng**, **SQLite** và giao diện **Java Swing**.

---

## 🎯 Mục tiêu & Tiêu chuẩn Môi trường (Environment Setup)

Để đảm bảo 4 thành viên trong nhóm có thể phối hợp mượt mà, dự án tuân thủ các tiêu chuẩn chung sau:

- **JDK:** Thống nhất phiên bản **JDK 22**.
- **IDE:** Sử dụng chung **Apache NetBeans 22** (Quản lý dự án dạng Maven).
- **Cơ sở dữ liệu:** Sử dụng **SQLite + JDBC** (`sqlite-jdbc:3.45.2.0`), tự động tạo cấu trúc bảng khi khởi động.
- **Truyền tải dữ liệu:** TCP Socket giao tiếp theo dòng, định dạng dữ liệu **JSON** (thư viện `com.google.code.gson:gson:2.10.1`).
- **Quy trình Git:** Phát triển theo nhánh cá nhân (`feature/...`), gộp qua nhánh chung (`dev`) bằng Pull Request trước khi phát hành chính thức lên `main`.

---

## 👥 Phân công thành viên & Nhánh phát triển (Git Branches)

| Thành viên | Vai trò | Trọng tâm phụ trách | Nhánh Git | Trạng thái |
|---|---|---|---|---|
| **TV1 (Phát)** | Leader / Network & Server Core | ServerSocket, đa luồng, Dispatcher, điều phối Client | `feature/phat-server-core` | 🟢 Đang hoàn thiện Core & Protocol |
| **TV2 (Hương)** | Client Agent Developer | Agent thu thập phần cứng, chụp màn hình, áp dụng chặn web | `feature/huong-protocol-control` | 🟡 Tiếp nhận Protocol & xây dựng Agent |
| **TV3 (Hợp)** | Database & Security | Quản lý SQLite JDBC, DAO User & Log, bảo mật dữ liệu | `feature/hop-database-client` | 🟡 Tiếp nhận Model & kết nối CSDL |
| **TV4 (Nhung)** | UI/UX & Dashboard | Thiết kế Dashboard Swing, ChatFrame, ScreenViewer, kiểm thử | `feature/nhung-ui-testing` | 🟡 Thiết kế giao diện kết nối ClientManager |

---

## 🚀 Cập nhật Mới nhất (Latest Updates & Changelog)

Các cập nhật quan trọng vừa được thực hiện trong dự án:

1. **Khắc phục triệt để lỗi biên dịch (Compile Fix):**
   - Tách rời lớp `ClientHandler` thành file độc lập `ClientHandler.java` trong `core/`.
   - Loại bỏ khai báo trùng lặp `class ClientHandler` trong `MainServer.java`.
   - Đảm bảo dự án biên dịch sạch 100% bằng Maven với JDK 22 (`BUILD SUCCESS`).

2. **Cập nhật cấu hình Maven (`pom.xml`):**
   - Đã bổ sung dependency **Google Gson 2.10.1** phục vụ phân giải gói tin JSON mạng LAN.
   - Đã bổ sung dependency **SQLite JDBC 3.45.2.0** phục vụ lưu trữ tài khoản và nhật ký hệ thống.
   - Sửa thuộc tính `<exec.mainClass>` trỏ chính xác về `com.mycompany.remoteservercore.core.MainServer`.

3. **Hiện thực hóa toàn diện Tầng Dữ liệu (Model Layer):**
   - `MessagePacket.java`: Định dạng gói tin chuẩn đa năng (chứa các loại gói `HEARTBEAT`, `SYS_INFO`, `COMMAND`, `CHAT`, `SCREENSHOT_REQ/RES`, `BLOCK_WEB`, `LOG`) kèm các static factory helper.
   - `ClientInfo.java`: Mô hình thông tin chi tiết máy trạm (IP, Hostname, MAC, OS, CPU %, RAM %, Disk %, Tên người dùng, Phòng ban, Trạng thái Online/Offline, Timestamp).
   - `User.java`: Mô hình tài khoản quản trị và phân quyền (`ADMIN`, `MANAGER`, `OBSERVER`).
   - `LogEntry.java`: Cấu trúc ghi vết sự kiện bảo mật và thao tác quản trị theo thời gian thực.

4. **Hiện thực hóa Tầng Giao thức & Điều phối (Protocol & Core Management):**
   - `JsonUtils.java`: Tiện ích serialization/deserialization JSON tốc độ cao bằng Gson, hỗ trợ cả 1-line stream compact cho Socket và Pretty JSON cho Log/Debug.
   - `PacketRouter.java`: Bộ định tuyến gói tin theo cơ chế Event-driven Router. Cho phép các module đăng ký lắng nghe loại gói tin tương ứng (`registerHandler`).
   - `ClientManager.java`: Quản lý danh sách các Agent đang online trong LAN, hỗ trợ gửi lệnh đích danh (`sendTo`) hoặc phát sóng toàn bộ (`broadcast`), cung cấp callback listener cho giao diện Swing.
   - `MainServer.java`: Khởi tạo sẵn các Handler mặc định cho `SYS_INFO`, `HEARTBEAT`, và `CHAT`.

5. **Nâng cấp công cụ kiểm thử nhanh (`TestClient.java`):**
   - Tự động lấy cấu hình phần cứng máy thực tế, đóng gói `ClientInfo` vào `SYS_INFO` và gửi tin nhắn `CHAT` mẫu lên Server để kiểm tra giao tiếp 2 chiều.

---

## 📂 Toàn bộ Cấu trúc Thư mục & Khung xương Dự án

Toàn bộ mã nguồn nằm trong gói gốc `com.mycompany.remoteservercore`:

```text
RemoteServerCore/
├── pom.xml                                   # Cấu hình Maven (Gson, SQLite JDBC, JDK 22)
└── src/main/java/com/mycompany/remoteservercore/
    │
    ├── TestClient.java                       # Client giả lập kiểm thử gửi nhận JSON qua Socket
    │
    ├── core/                                 # Điều phối mạng & Quản lý kết nối
    │   ├── MainServer.java                   # Khởi tạo ServerSocket (cổng 9999), Thread Pool, Router
    │   ├── ClientHandler.java                # Luồng phục vụ 1-1 cho từng Client kết nối
    │   ├── ClientManager.java                # Quản lý danh sách máy online/offline, broadcast tin
    │   └── AgentCore.java                    # Tiến trình nền phía Client (heartbeat, auto-reconnect)
    │
    ├── model/                                # Đối tượng dữ liệu POJO truyền tải qua JSON
    │   ├── MessagePacket.java                # Khuôn mẫu gói tin giao tiếp Client-Server
    │   ├── ClientInfo.java                   # Thông số phần cứng, IP, CPU, RAM, phòng ban
    │   ├── User.java                         # Tài khoản đăng nhập & phân quyền
    │   └── LogEntry.java                     # Cấu trúc lưu vết nhật ký hoạt động
    │
    ├── protocol/                             # Xử lý giao thức truyền tin mạng LAN
    │   ├── JsonUtils.java                    # Chuyển đổi hai chiều Java Object <-> JSON
    │   └── PacketRouter.java                 # Bộ điều phối gói tin theo loại lệnh
    │
    ├── database/                             # Lưu trữ dữ liệu với SQLite + JDBC
    │   ├── DatabaseManager.java              # Quản lý kết nối SQLite connection pool
    │   ├── UserDAO.java                      # Truy vấn tài khoản và phân quyền
    │   └── LogDAO.java                       # Đọc/Ghi log thao tác hệ thống
    │
    ├── features/                             # Các module tính năng chuyên sâu
    │   ├── ScreenCapturer.java               # Chụp ảnh màn hình máy nhân sự
    │   ├── WebBlocker.java                   # Chặn truy cập website theo tên miền
    │   └── SystemControl.java                # Khóa máy, khởi động lại, đăng xuất từ xa
    │
    └── ui/                                   # Giao diện quản trị Java Swing
        ├── LoginFrame.java                   # Màn hình đăng nhập hệ thống
        ├── DashboardFrame.java               # Màn hình điều khiển trung tâm (danh sách máy)
        ├── ChatFrame.java                    # Cửa sổ nhắn tin nội bộ với nhân sự
        ├── ScreenViewFrame.java              # Cửa sổ giám sát màn hình từ xa
        ├── BlockedWebFrame.java              # Cửa sổ quản lý danh sách web cấm
        └── LogFrame.java                     # Cửa sổ theo dõi lịch sử và nhật ký
```

---

## 🛠️ Hướng dẫn Khởi chạy & Kiểm thử (Quick Start)

### 1. Mở dự án trong NetBeans
1. Mở **Apache NetBeans**, chọn `File -> Open Project...`
2. Trỏ đến thư mục `RemoteServerCore` (biểu tượng dự án Maven).
3. Đợi NetBeans nạp và tự động đồng bộ dependencies từ Maven.

### 2. Chạy thử nghiệm Server & Client giả lập
1. **Khởi động Server:**
   - Trong NetBeans, mở file `com.mycompany.remoteservercore.core.MainServer.java`.
   - Nhấn chuột phải chọn **Run File** (hoặc `Shift + F6`).
   - Server sẽ lắng nghe tại cổng `9999`.
2. **Chạy Test Client:**
   - Mở file `com.mycompany.remoteservercore.TestClient.java`.
   - Nhấn chuột phải chọn **Run File** (hoặc `Shift + F6`).
   - Quan sát cửa sổ Output: TestClient sẽ gửi gói tin cấu hình phần cứng `SYS_INFO` và tin nhắn `CHAT`. Server sẽ ghi nhận, in thông số máy trạm và phản hồi thành công.

---

## 📌 Hướng dẫn tiếp theo cho từng thành viên

1. **Hương (Client Agent - `feature/huong-protocol-control`):**
   - Kế thừa `MessagePacket` và `JsonUtils` để hoàn thiện `AgentCore.java`: Định kỳ gửi `HEARTBEAT` và `SYS_INFO`.
   - Viết tính năng chụp màn hình trong `ScreenCapturer.java` (chuyển `BufferedImage` sang Base64 đưa vào `MessagePacket`).
2. **Hợp (Database & Security - `feature/hop-database-client`):**
   - Viết mã khởi tạo cơ sở dữ liệu `app.db` trong `DatabaseManager.java`.
   - Hoàn thiện `UserDAO.java` và `LogDAO.java` để lưu trữ dữ liệu từ `LogEntry` và `User`.
3. **Nhung (UI/UX - `feature/nhung-ui-testing`):**
   - Thiết kế giao diện `DashboardFrame.java` (sử dụng bảng `JTable` hiển thị danh sách máy lấy từ `ClientManager.getInstance().getAllClients()`).
   - Đăng ký listener với `ClientManager` để tự động cập nhật bảng khi máy tính online/offline.
4. **Phát (Server Core - `feature/phat-server-core`):**
   - Hỗ trợ kết nối các Router handler của từng tính năng và chuẩn bị merge vào nhánh `dev`.
