# Remote Computer Control LAN

Ứng dụng điều khiển máy tính từ xa dựa trên mô hình **Client-Server** trong **mạng LAN nội bộ**, xây dựng bằng Java. Hệ thống cho phép quản trị viên giám sát, điều khiển và giao tiếp với nhiều máy nhân sự trong cùng mạng nội bộ theo thời gian thực.

---

## 📌 Giới thiệu

Trong môi trường văn phòng/phòng máy, việc quản lý và hỗ trợ nhiều máy tính nhân sự thường tốn nhiều thời gian nếu phải thao tác trực tiếp trên từng máy. Dự án xây dựng một hệ thống **Server quản trị tập trung** kết nối với nhiều **Client (Agent)** cài trên các máy nhân sự, giao tiếp qua **TCP Socket** với dữ liệu định dạng **JSON**, cho phép:

- Theo dõi trạng thái máy (online/offline) theo thời gian thực
- Gửi lệnh điều khiển từ xa (khóa máy, đăng xuất, khởi động lại)
- Chụp màn hình theo yêu cầu
- Chat 2 chiều giữa quản trị viên và nhân sự
- Quản lý danh sách website bị chặn
- Ghi log toàn bộ hoạt động hệ thống
- Gửi thông báo sự kiện quan trọng qua Telegram

---

## 🏗️ Kiến trúc hệ thống

```
                     ┌─────────────────────────┐
                     │   SERVER (Máy quản trị)  │
                     │   - Java Swing UI         │
                     │   - SQLite + JDBC         │
                     │   - Xác thực & phân quyền │
                     └────────────┬────────────┘
                                  │
                        TCP Socket + JSON
                        (Mạng LAN nội bộ)
                                  │
        ┌─────────────┬──────────┼──────────┬─────────────┐
        │             │          │          │             │
   ┌────▼────┐   ┌────▼────┐┌────▼────┐┌────▼────┐   ┌────▼────┐
   │ Client 1│   │ Client 2││ Client 3││ Client N│   │  ...    │
   │  Agent  │   │  Agent  ││  Agent  ││  Agent  │   │         │
   └─────────┘   └─────────┘└─────────┘└─────────┘   └─────────┘
```

Mô hình **1 Server – Nhiều Client**, trong đó Server quản lý tập trung và mỗi Client chạy một Agent nền để nhận lệnh, gửi thông tin định kỳ (heartbeat) và tự kết nối lại khi mất mạng.

---

## 🚀 Chức năng chính

### Tại máy quản trị (Server)
| # | Chức năng |
|---|---|
| 1 | Đăng nhập và phân quyền người dùng |
| 2 | Quản lý danh sách máy nhân sự (online/offline) |
| 3 | Xem thông tin hệ thống Client (CPU, RAM, IP, OS...) |
| 4 | Gửi tin nhắn / chat 2 chiều với máy nhân sự |
| 5 | Yêu cầu chụp màn hình |
| 6 | Gửi lệnh điều khiển (khóa máy, đăng xuất, restart) |
| 7 | Quản lý danh sách website bị chặn |
| 8 | Xem log và lịch sử hoạt động |
| 9 | Nhận thông báo sự kiện qua Telegram |

### Tại máy nhân sự (Client)
| # | Chức năng |
|---|---|
| 1 | Kết nối và duy trì kết nối với Server |
| 2 | Gửi thông tin hệ thống định kỳ (heartbeat) |
| 3 | Nhận và thực thi lệnh từ Server |
| 4 | Chụp màn hình theo yêu cầu |
| 5 | Chat với Server |
| 6 | Áp dụng danh sách website bị chặn |
| 7 | Tự động kết nối lại khi bị gián đoạn mạng |

---

## 🛠️ Công nghệ sử dụng

| Thành phần | Công nghệ |
|---|---|
| Ngôn ngữ | Java |
| Giao diện | Java Swing |
| Cơ sở dữ liệu | SQLite + JDBC |
| Giao tiếp mạng | TCP Socket, định dạng dữ liệu JSON |
| Thông báo | Telegram Bot API (tùy chọn, cần Server có internet) |
| IDE | NetBeans |

---

## 📂 Cấu trúc thư mục

```
Remote-Computer-Control-LAN/
├── server/                # Mã nguồn phía Server
│   ├── ui/                 # Giao diện Java Swing
│   ├── network/            # Xử lý TCP Socket phía Server
│   ├── database/           # Kết nối SQLite, DAO
│   └── service/             # Logic nghiệp vụ (log, notification...)
├── client/                # Mã nguồn phía Client (Agent)
│   ├── network/             # Kết nối tới Server, heartbeat
│   └── handler/             # Thực thi lệnh nhận được
├── common/                 # Model/DTO dùng chung (JSON protocol)
├── docs/                   # Tài liệu thiết kế (ERD, sơ đồ, báo cáo)
└── README.md
```

---

## ⚙️ Cài đặt & Chạy thử

### Yêu cầu
- JDK 17+ (hoặc phiên bản nhóm thống nhất)
- NetBeans (hoặc IDE Java khác)
- Thư viện SQLite JDBC Driver

### Các bước

1. **Clone repository**
   ```bash
   git clone https://github.com/NgoNguyenThuHuong/Remote-Computer-Control-LAN.git
   cd Remote-Computer-Control-LAN
   ```

2. **Mở project bằng NetBeans** và đợi IDE tải các thư viện phụ thuộc.

3. **Khởi tạo cơ sở dữ liệu**
   - Chạy script tạo database trong `server/database/init.sql` (hoặc theo hướng dẫn trong thư mục `database/`).

4. **Chạy Server**
   - Mở project `server`, chạy file `Main` (hoặc `ServerApp`) để khởi động Server.
   - Server sẽ lắng nghe kết nối trên cổng cấu hình sẵn (mặc định trong file config).

5. **Chạy Client (Agent)**
   - Trên (các) máy nhân sự trong cùng LAN, chạy project `client`.
   - Client sẽ tự động kết nối tới Server theo địa chỉ IP cấu hình trong file config.

6. **(Tùy chọn) Cấu hình Telegram Notification**
   - Tạo Bot qua [BotFather](https://t.me/BotFather), lấy Token và Chat ID.
   - Điền vào file cấu hình, đặt `telegram.enabled=true`.
   - Nếu môi trường không có internet, đặt `telegram.enabled=false` — hệ thống vẫn hoạt động bình thường, chỉ không gửi thông báo qua Telegram.

---

## 👥 Thành viên nhóm & Phân công

| Thành viên | Vai trò | Phụ trách chính | Nhánh Git |
|---|---|---|---|
| **Phát** | Leader / Backend & Database | Server core, CSDL, điều khiển, sửa lỗi | `feature/server-core-sqlite` |
| **Hương** | Full-stack hỗ trợ | Phân tích yêu cầu, JSON protocol, đăng nhập, gửi lệnh, screenshot, báo cáo | `feature/client-agent-core` |
| **Hợp** | Client Agent Developer | CSDL, Client Agent, chặn website, Activity Log | `feature/ui-swing-chat-blockweb` |
| **Nhung** | UI/UX & Test | Giao diện, Dashboard, chat, kiểm thử, tối ưu | `feature/testing-docs-optimization` |

---

## 🗓️ Lộ trình thực hiện (6 tuần)

| Tuần | Nội dung |
|---|---|
| Tuần 1 | Phân tích & Thiết kế |
| Tuần 2 | Thiết lập môi trường & Giao thức |
| Tuần 3 | Phát triển Server |
| Tuần 4 | Phát triển Client & Điều khiển |
| Tuần 5 | Tích hợp các chức năng chính |
| Tuần 6 | Kiểm thử, hoàn thiện & Báo cáo |

Chi tiết công việc từng tuần được quản lý qua **Issues** và **Milestones** trên GitHub.

---

## 🌳 Quy trình Git

Dự án dùng mô hình nhánh `main` (ổn định) — `dev` (tích hợp chung) — `feature/*` (mỗi thành viên 1 nhánh riêng). Mọi thay đổi vào `dev` đều phải qua **Pull Request** và được review bởi ít nhất 1 thành viên khác trước khi merge.

Xem chi tiết quy trình tại: `docs/Huong-dan-Git-cho-thanh-vien.md`

---

## ⚠️ Giới hạn phạm vi

- Hệ thống chỉ hoạt động trong mạng LAN nội bộ, không hỗ trợ điều khiển qua internet công cộng.
- Chức năng chặn website chỉ triển khai trong phạm vi demo/đồ án, chưa tối ưu cho môi trường sản xuất thực tế.
- Tính năng Telegram Notification yêu cầu máy Server có kết nối internet; nếu không, hệ thống vẫn hoạt động đầy đủ các chức năng LAN cốt lõi.

---

## 📄 Giấy phép

Dự án phục vụ mục đích học tập / đồ án môn học.
