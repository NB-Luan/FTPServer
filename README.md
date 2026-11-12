# BÀI LAB LẬP TRÌNH MẠNG: XÂY DỰNG FTP SERVER & CLIENT (JAVA SOCKET, THREAD & CSDL MYSQL)

Chương trình **FTP Server & FTP Client** hoàn chỉnh viết bằng **Java thuần** (sử dụng `ServerSocket`, `Socket`, đa luồng `Thread` và giao diện đồ họa `Java Swing`), tích hợp **100% Cơ sở dữ liệu MySQL XAMPP**.

> [!IMPORTANT]
> **Đặc điểm nổi bật**: Toàn bộ dữ liệu (tài khoản người dùng, nội dung tệp tin nhị phân và nhật ký giao dịch) được lưu trữ **100% trong Cơ sở dữ liệu MySQL** (`ftp_db`), hoàn toàn không sử dụng thư mục đĩa cục bộ (`ftp_root` đã được loại bỏ hoàn toàn). Tệp tin được lưu trực tiếp dưới dạng `LONGBLOB`.

---

## 1. Cấu trúc dự án

```text
FTPServer/
├── run.bat                 # Script khởi động FTP Server
├── run_client.bat          # Script khởi động FTP Client
├── lib/
│   └── mysql-connector-j.jar  # Driver JDBC kết nối MySQL XAMPP
├── src/
│   ├── FtpServer.java      # Giao diện Server, ServerSocket listener, quản lý luồng
│   ├── ClientHandler.java  # Xử lý giao thức FTP (RFC 959), đọc/ghi tệp trực tiếp từ MySQL
│   ├── FtpClient.java      # Giao diện Client trực quan: Đăng nhập/Đăng ký gộp, Xem file, Tải, Xóa, Đổi tên
│   └── Database.java       # Quản lý kết nối JDBC, bảng users, files (LONGBLOB) và ftp_logs
└── bin/                    # Thư mục chứa các file .class sau khi biên dịch
```

---

## 2. Cấu trúc Cơ sở dữ liệu MySQL (`ftp_db`)

Khi khởi động Server, hệ thống tự động kiểm tra và tạo cơ sở dữ liệu `ftp_db` cùng 3 bảng:

1. **`users`** (Quản lý tài khoản):
   - `id INT AUTO_INCREMENT PRIMARY KEY`
   - `username VARCHAR(50) UNIQUE`
   - `password VARCHAR(255)`
   - `created_at TIMESTAMP`
   - *Tài khoản mặc định:* `admin / 123456`, `user / 123456`. Có hỗ trợ đăng ký tài khoản mới trực tiếp từ giao diện Client qua lệnh `REGISTER`.

2. **`files`** (Lưu trữ toàn bộ tệp tin dạng nhị phân):
   - `id INT AUTO_INCREMENT PRIMARY KEY`
   - `filename VARCHAR(255) UNIQUE`
   - `filesize BIGINT`
   - `filedata LONGBLOB` (Chứa toàn bộ dữ liệu byte của tệp tin lên tới 64MB)
   - `uploader VARCHAR(50)`
   - `uploaded_at TIMESTAMP`

3. **`ftp_logs`** (Nhật ký giao dịch thời gian thực):
   - `id INT AUTO_INCREMENT PRIMARY KEY`
   - `client_ip VARCHAR(50)`
   - `username VARCHAR(50)`
   - `action VARCHAR(50)` (LOGIN, REGISTER, UPLOAD, DOWNLOAD, DELETE, RENAME, QUIT)
   - `details TEXT`
   - `created_at TIMESTAMP`

---

## 3. Cách khởi chạy chương trình

### Yêu cầu tiên quyết:
- Bật **MySQL** trong **XAMPP Control Panel** (cổng mặc định `3306`, user `root`, mật khẩu rỗng `""`).
- Máy đã cài JDK (Java Development Kit).

### Khởi chạy nhanh bằng file script `.bat`:
1. **Chạy Server:** Nhấp đúp vào `run.bat` (hoặc mở terminal gõ `.\run.bat`). Bấm nút **"Start"** để bắt đầu lắng nghe tại cổng `2121`.
2. **Chạy Client:** Nhấp đúp vào `run_client.bat` (hoặc mở terminal gõ `.\run_client.bat`).

---

## 4. Tính năng của FTP Client

- **Giao diện sáng / màu trắng thanh lịch**: Thiết kế trực quan, dễ thao tác với 2 nút cơ bản **"Đăng nhập"** và **"Đăng ký"** trên cùng một màn hình (không cần chuyển trang phức tạp).
- **Hỗ trợ đầy đủ thao tác tệp:**
  - **Xem danh sách tệp (`LIST`):** Hiển thị rõ tên file, dung lượng (bytes) và thời gian tải lên.
  - **Mở xem trực tiếp (Double-click / Nút "Xem file"):** Tải tạm vào thư mục bộ nhớ đệm và tự động mở bằng ứng dụng mặc định của Windows (Notepad, Trình xem ảnh, Acrobat Reader,...).
  - **Tải lên (`STOR`):** Chọn file từ máy tính để lưu thẳng vào bảng `files` dạng `LONGBLOB` trong MySQL.
  - **Tải về (`RETR`):** Trích xuất dữ liệu `LONGBLOB` từ MySQL và lưu thành tệp tin trên máy tính.
  - **Xóa tệp (`DELE`) & Đổi tên tệp (`RNFR` -> `RNTO`):** Thao tác trực tiếp trên cơ sở dữ liệu.
  - **Nhật ký tiếng Việt chi tiết:** Toàn bộ lệnh gửi nhận và trạng thái kết nối hiển thị rõ ràng trên giao diện.

---

## 5. Danh sách các lệnh FTP cài đặt trong hệ thống

| Lệnh | Ý nghĩa chuẩn FTP | Thao tác tương ứng với CSDL MySQL |
| :--- | :--- | :--- |
| `USER <username>` | Gửi tên đăng nhập | Ghi nhận tài khoản vào phiên làm việc |
| `PASS <password>` | Nhập mật khẩu | Kiểm tra `SELECT * FROM users WHERE username=? AND password=?` |
| `REGISTER <u> <p>` | Đăng ký tài khoản mới | `INSERT INTO users (username, password) VALUES (?, ?)` |
| `PASV` | Mở chế độ Bị động | Mở cổng ngẫu nhiên `ServerSocket(0)`, trả về IP và Port cho Data Socket |
| `LIST` | Lấy danh sách tệp | `SELECT filename, filesize, uploaded_at FROM files`, gửi định dạng `ls -l` |
| `RETR <filename>` | Tải tệp về | `SELECT filedata FROM files WHERE filename=?`, truyền qua Data Socket |
| `STOR <filename>` | Tải tệp lên | Đọc dữ liệu từ Data Socket, lưu vào `INSERT INTO files (...) VALUES (...)` |
| `DELE <filename>` | Xóa tệp tin | `DELETE FROM files WHERE filename=?` |
| `RNFR <filename>` | Chọn tệp đổi tên | Kiểm tra sự tồn tại trong CSDL |
| `RNTO <filename>` | Đổi sang tên mới | `UPDATE files SET filename=? WHERE filename=?` |
| `SIZE <filename>` | Lấy dung lượng tệp | `SELECT filesize FROM files WHERE filename=?` |
| `PWD`, `CWD`, `CDUP` | Điều hướng thư mục ảo | Trả về thư mục gốc `/` |
| `QUIT` | Đóng kết nối | Ghi log đăng xuất vào bảng `ftp_logs` và đóng Socket |

---

## 6. Tài liệu ôn thi vấn đáp môn Lập trình mạng

### Câu 1: Tại sao FTP lại dùng 2 kết nối (2 Port) thay vì 1 kết nối như HTTP?
- **Kênh điều khiển (Control Connection - Cổng 21/2121):** Duy trì liên tục trong suốt phiên làm việc để gửi nhận các chuỗi lệnh dạng text (USER, PASS, PWD,...) và mã phản hồi (220, 230, 250,...).
- **Kênh dữ liệu (Data Connection):** Chỉ được mở ra khi cần truyền dữ liệu (xem danh sách file `LIST`, tải file `RETR`, tải lên `STOR`). Sau khi truyền xong các byte dữ liệu, kênh này sẽ được đóng lại ngay lập tức.
- Việc tách biệt giúp việc truyền file dung lượng lớn không làm nghẽn các lệnh điều khiển.

### Câu 2: Sự khác nhau giữa Active Mode (`PORT`) và Passive Mode (`PASV`)?
- **Active Mode:** Client mở một cổng, gửi lệnh `PORT` báo cho Server. Server sẽ chủ động kết nối tới cổng của Client. Thường bị Tường lửa (Firewall) hoặc NAT phía Client chặn kết nối vào.
- **Passive Mode (Chế độ Bị động - chuẩn hiện đại):** Client gửi lệnh `PASV`. Server mở 1 cổng ngẫu nhiên (`new ServerSocket(0)`), trả về địa chỉ IP và Port qua mã `227`. Client sẽ chủ động kết nối tới cổng này của Server. Giúp vượt qua hoàn toàn Tường lửa phía Client.

### Câu 3: Cách tính số cổng từ phản hồi `227 Entering Passive Mode (h1,h2,h3,h4,p1,p2)`?
- Công thức: `Port = (p1 * 256) + p2` (tương đương phép dịch bit `(p1 << 8) | p2`).
- Ví dụ: `227 Entering Passive Mode (127,0,0,1,215,148)`
  - IP: `127.0.0.1`
  - Port: `(215 * 256) + 148 = 55040 + 148 = 55188`.

### Câu 4: Làm thế nào chương trình phục vụ nhiều Client đồng thời mà không bị treo?
- Sử dụng mô hình **Đa luồng (Multi-threading)**:
  - Vòng lặp `serverSocket.accept()` chạy trên một luồng nền riêng biệt để không gây đơ giao diện Swing (`Event Dispatch Thread - EDT`).
  - Mỗi khi có client mới kết nối, `serverSocket.accept()` trả về một `Socket` riêng. Chương trình khởi tạo đối tượng `ClientHandler(socket, ...)` và kích hoạt `new Thread(handler).start()`.
  - Nhờ đó, thao tác tải/đẩy tệp của Client này hoàn toàn độc lập và không làm nghẽn Client khác.

### Câu 5: Việc lưu trữ tệp tin trong Cơ sở dữ liệu MySQL (LONGBLOB) có ưu điểm gì so với lưu trữ trên đĩa cứng?
- **Tính toàn vẹn & Tập trung:** Toàn bộ người dùng, quyền truy cập, tệp tin và nhật ký đều nằm tập trung trong 1 cơ sở dữ liệu duy nhất (`ftp_db`), dễ dàng sao lưu (backup) và phục hồi (restore) bằng MySQL dump.
- **Bảo mật tuyệt đối:** Tránh hoàn toàn các lỗ hổng bảo mật liên quan đến hệ thống tệp đĩa cứng như Path Traversal (tấn công `../` để truy cập vào các thư mục hệ thống Windows/Linux).
