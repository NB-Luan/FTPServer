# BÀI LAB LẬP TRÌNH MẠNG: XÂY DỰNG FTP SERVER (JAVA SOCKET & THREAD)

Chương trình **FTP Server** hoàn chỉnh viết bằng **Java thuần** (sử dụng `ServerSocket`, `Socket`, đa luồng `Thread` và giao diện đồ họa `Java Swing`). Toàn bộ mã nguồn được gói gọn trong **1 file duy nhất `src/FtpServer.java`**, không dùng thư viện ngoài, không chia package phức tạp, rất thuận tiện để nộp bài và bảo vệ đồ án/vấn đáp môn Lập trình mạng.

---

## 1. Cấu trúc thư mục

```text
FTPServer/
├── run.bat                 # File script click đúp để biên dịch và chạy Server ngay
├── ftp_root/               # Thư mục gốc chia sẻ file mặc định
│   └── welcome.txt         # File mẫu chào mừng
├── src/
│   └── FtpServer.java      # Toàn bộ mã nguồn (Giao diện Swing + ServerSocket + ClientHandler)
└── bin/                    # Thư mục chứa file .class sau khi biên dịch
```

---

## 2. Cách biên dịch và chạy Server

### Cách 1: Click đúp vào file `run.bat`
Chỉ cần nhấp đúp chuột vào file `run.bat` trong thư mục gốc. Script sẽ tự động kiểm tra JDK, biên dịch file `src/FtpServer.java` vào thư mục `bin` và mở giao diện Server.

### Cách 2: Chạy bằng dòng lệnh (Terminal / PowerShell / CMD)
```powershell
# Biên dịch file mã nguồn
javac -encoding UTF-8 -d bin src/FtpServer.java

# Khởi chạy chương trình
java -cp bin FtpServer
```

---

## 3. Hướng dẫn kết nối bằng FileZilla Client

1. **Khởi động FTP Server trên giao diện:**
   - **IP Server (PASV):** Chọn hoặc điền IP của máy (ví dụ `127.0.0.1` nếu test trên cùng một máy, hoặc IP Wi-Fi/LAN `192.168.x.x`, hoặc IP Tailscale `100.x.x.x` nếu test từ xa).
   - **Port:** Mặc định `2121`.
   - **Thư mục gốc:** Mặc định trỏ đến thư mục `ftp_root`. Có thể bấm nút **"Chọn thư mục..."** để đổi sang thư mục bất kỳ.
   - Nhấn nút **"Start Server"**.

2. **Cấu hình trên FileZilla Client:**
   - Mở FileZilla Client.
   - Nhập thông tin vào thanh **Quickconnect**:
     - **Host:** `127.0.0.1` (hoặc IP Server bạn đã cấu hình)
     - **Username:** `admin` (hoặc `user`)
     - **Password:** `123456`
     - **Port:** `2121`
   - Bấm **Quickconnect**.
   - *Lưu ý:* Nếu kết nối qua Site Manager (Quản lý trang web): Chọn Giao thức **FTP - File Transfer Protocol**, Mã hóa **Only use plain FTP (insecure)**, Chế độ truyền **Bị động (Passive)**.

3. **Kiểm tra các chức năng trên FileZilla:**
   - Xem danh sách file/thư mục (`LIST`).
   - Tải file từ Server về máy (`RETR`).
   - Tải file từ máy lên Server (`STOR`).
   - Tạo thư mục mới (`MKD`).
   - Đổi tên file hoặc thư mục (`RNFR` -> `RNTO`).
   - Xóa file (`DELE`) và xóa thư mục (`RMD`).
   - Di chuyển qua lại giữa các thư mục (`CWD`, `CDUP`).

---

## 4. Danh sách các lệnh FTP cốt lõi đã cài đặt

| Nhóm chức năng | Lệnh | Mã trả về chuẩn | Ý nghĩa & Luồng hoạt động |
| :--- | :--- | :--- | :--- |
| **Đăng nhập & Khởi tạo** | `USER <username>` | `331` | Tiếp nhận tên tài khoản, yêu cầu nhập mật khẩu |
| | `PASS <password>` | `230` hoặc `530` | Xác thực `admin/123456` hoặc `user/123456` |
| | `SYST` | `215 UNIX Type: L8` | Khai báo hệ điều hành chuẩn UNIX cho FileZilla |
| | `FEAT` | `211 End` | Liệt kê các tính năng mở rộng hỗ trợ (UTF8, SIZE, MDTM) |
| | `OPTS UTF8 ON` | `200 OK` | Bật mã hóa UTF-8 hỗ trợ tiếng Việt có dấu |
| | `TYPE I / A` | `200 OK` | Chọn chế độ truyền: Binary (nhị phân) hoặc ASCII |
| | `NOOP` | `200 OK` | Giữ kết nối không bị timeout |
| | `QUIT` | `221 Goodbye` | Đóng kết nối điều khiển và giải phóng tài nguyên |
| **Quản lý Thư mục & File** | `PWD` | `257 "/path"` | Lấy đường dẫn thư mục hiện tại của client |
| | `CWD <dir>` | `250 OK` / `550 Error` | Đổi thư mục làm việc (Change Working Directory) |
| | `CDUP` | `250 OK` | Lên thư mục cha (không được vượt ra khỏi Root) |
| | `MKD <dir>` | `257 Created` | Tạo thư mục mới |
| | `RMD <dir>` | `250 Removed` | Xóa thư mục |
| | `DELE <file>` | `250 Deleted` | Xóa file |
| | `RNFR <old>` | `350 Ready for RNTO` | Chọn file cần đổi tên (Rename From) |
| | `RNTO <new>` | `250 Renamed` | Đổi sang tên mới (Rename To) |
| **Truyền Dữ liệu (PASV)** | `PASV` | `227 Entering Passive Mode` | Mở ServerSocket data ngẫu nhiên, tính `p1 = port/256`, `p2 = port%256` |
| | `LIST` | `150` -> `226` | Gửi danh sách file định dạng Unix `ls -l` qua Data Socket |
| | `RETR <file>` | `150` -> `226` | Đọc file từ ổ đĩa và ghi vào Data Socket (Download) |
| | `STOR <file>` | `150` -> `226` | Đọc từ Data Socket và ghi xuống đĩa (Upload) |

---

## 5. Tài liệu ôn thi vấn đáp môn Lập trình mạng

### Câu 1: Tại sao FTP lại dùng 2 kết nối (2 Port) thay vì 1 kết nối như HTTP?
- **Trả lời:**
  - **Kênh điều khiển (Control Connection - Cổng 21/2121):** Duy trì liên tục trong suốt phiên làm việc của người dùng để trao đổi lệnh dạng văn bản (USER, PASS, PWD,...) và mã phản hồi (220, 230, 250,...).
  - **Kênh dữ liệu (Data Connection):** Chỉ được mở ra khi có yêu cầu truyền dữ liệu (như xem danh sách file `LIST`, tải file `RETR`, đẩy file `STOR`). Khi truyền xong, kênh này được đóng ngay lập tức.
  - Việc tách rời 2 kênh giúp việc truyền dữ liệu dung lượng lớn không làm nghẽn các lệnh điều khiển (ví dụ client có thể gửi lệnh `ABOR` để hủy tiến trình tải file đang diễn ra).

### Câu 2: Sự khác nhau giữa Active Mode (`PORT`) và Passive Mode (`PASV`)?
- **Active Mode:** Client mở một cổng nhận dữ liệu, gửi lệnh `PORT h1,h2,h3,h4,p1,p2` báo cho Server. Server sẽ chủ động tạo kết nối tới cổng của Client. **Nhược điểm:** Dễ bị tường lửa (Firewall) hoặc NAT của Client chặn vì đây là kết nối từ ngoài vào trong Client (Inbound).
- **Passive Mode (Chế độ Bị động - chuẩn hiện nay):** Client gửi lệnh `PASV`. Server mở 1 cổng ngẫu nhiên (`new ServerSocket(0)`), gửi trả về mã `227 (h1,h2,h3,h4,p1,p2)`. Client sẽ chủ động kết nối tới cổng này của Server. **Ưu điểm:** Vượt qua hoàn toàn Firewall/NAT phía Client.

### Câu 3: Cách tính số cổng từ phản hồi `227 Entering Passive Mode (h1,h2,h3,h4,p1,p2)`?
- Công thức: `Port = (p1 * 256) + p2` (tương đương phép dịch bit `(p1 << 8) | p2`).
- Ví dụ: `227 Entering Passive Mode (127,0,0,1,215,148)`
  - IP: `127.0.0.1`
  - Port: `(215 * 256) + 148 = 55040 + 148 = 55188`.

### Câu 4: Làm thế nào chương trình phục vụ nhiều Client đồng thời mà không bị treo?
- Sử dụng mô hình **Đa luồng (Multi-threading)**:
  - Vòng lặp `serverSocket.accept()` chạy trên một luồng nền riêng để không làm đơ giao diện Swing (`EDT`).
  - Mỗi khi có client mới kết nối, `serverSocket.accept()` trả về một `Socket` riêng biệt. Chương trình khởi tạo đối tượng `ClientHandler(socket, ...)` và bọc vào một `new Thread(handler).start()`.
  - Nhờ đó, thao tác tải/đẩy file của client A không ảnh hưởng tới client B.

### Câu 5: Chương trình bảo vệ máy chủ chống tấn công Path Traversal (`../../`) như thế nào?
- Sử dụng phương thức `File.getCanonicalPath()`:
  - Khi client gửi đường dẫn (ví dụ `CWD ../../Windows`), `getCanonicalPath()` sẽ giải quyết tất cả dấu `..` thành đường dẫn thực tế tuyệt đối trên hệ điều hành.
  - Server kiểm tra: `targetPath.startsWith(rootPath)`. Nếu không bắt đầu bằng thư mục gốc thì từ chối truy cập ngay lập tức (`550 Access denied`).

