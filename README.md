# 🏢 Hệ thống Quản lý và Giám sát Máy tính Phòng ban (Client-Server LAN)

Ứng dụng quản lý và giám sát máy tính nhân sự từ xa trong mạng LAN nội bộ, phát triển dựa trên mô hình Client-Server sử dụng **Java (Maven)**, **TCP Socket đa luồng**, **SQLite** và giao diện **Java Swing**.

---

## 🎯 Mục tiêu & Tiêu chuẩn Môi trường (Environment Setup)

Để đảm bảo 4 thành viên trong nhóm có thể phối hợp mượt mà, dự án tuân thủ các tiêu chuẩn chung sau:

- **JDK:** Thống nhất cùng một phiên bản Java (JDK 22).
- **IDE:** Sử dụng chung **Apache NetBeans** (Quản lý dự án dạng Maven).
- **Cơ sở dữ liệu:** Sử dụng **SQLite + JDBC** (Tự động khởi tạo và cấu hình khi chạy chương trình).
- **Quy trình Git (Git Workflow):** Phát triển theo nhánh cá nhân (`feature/...`), gộp qua nhánh chung (`dev`) bằng Pull Request trước khi phát hành chính thức.

---

## 👥 Phân công thành viên & Nhánh phát triển (Git Branches)

- **TV1 (Phát):** Network & Server Core (`feature/phat-server-core`) - Xử lý kết nối Socket đa luồng, điều phối lệnh.
- **TV2 (Hương):** Client Agent (`feature/huong-agent`) - Thu thập thông tin máy nhân sự, chụp màn hình, chặn web.
- **TV3 (Hợp):** Giao diện quản trị (`feature/hop-ui`) - Xây dựng Dashboard Java Swing, giao diện điều khiển.
- **TV4 (Nhung):** Cơ sở dữ liệu & Bảo mật (`feature/nhung-database`) - Quản lý SQLite JDBC, log hoạt động, thông báo.

---

## 📂 Toàn bộ Cấu trúc Thư mục & Khung xương Dự án (Full Package Architecture)

Toàn bộ mã nguồn được quy hoạch bên trong gói gốc `com.mycompany.remoteservercore`:

```text

```
