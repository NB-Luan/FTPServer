import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ClientHandler implements Runnable {

    private final Socket controlSocket;
    private final String serverIp;
    private final FtpServer serverUI;
    private final String clientAddress;

    private BufferedReader reader;
    private BufferedWriter writer;
    private boolean isLoggedIn = false;
    private String currentUsername = null;
    private ServerSocket dataServerSocket = null;
    private String renameFromStr = null;

    public ClientHandler(Socket socket, String serverIp, FtpServer serverUI) {
        this.controlSocket = socket;
        this.serverIp = serverIp;
        this.serverUI = serverUI;
        this.clientAddress = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
    }

    @Override
    public void run() {
        serverUI.log("[+ KẾT NỐI]  Client [" + clientAddress + "] vừa kết nối vào Server.");
        try {
            reader = new BufferedReader(new InputStreamReader(controlSocket.getInputStream(), StandardCharsets.UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(controlSocket.getOutputStream(), StandardCharsets.UTF_8));

            sendResponse("220 Welcome to Java Custom FTP Server (MySQL Database Mode)\r\n", "220 Chào mừng Client kết nối (Chế độ CSDL MySQL)");

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                String command = line;
                String argument = "";
                int spaceIndex = line.indexOf(' ');
                if (spaceIndex != -1) {
                    command = line.substring(0, spaceIndex).toUpperCase(Locale.ENGLISH);
                    argument = line.substring(spaceIndex + 1).trim();
                } else {
                    command = line.toUpperCase(Locale.ENGLISH);
                }

                if (handleCommand(command, argument)) break;
            }
        } catch (IOException e) {
            serverUI.log("[- NGẮT NỐI] Client [" + clientAddress + "] đã thoát (" + e.getMessage() + ")");
        } finally {
            closeConnection();
            serverUI.removeClient(this);
            serverUI.log("[- ĐÃ ĐÓNG]  Đã giải phóng tài nguyên cho Client [" + clientAddress + "].");
        }
    }

    private boolean handleCommand(String cmd, String arg) throws IOException {
        // 1. Các lệnh không cần đăng nhập trước
        if (cmd.equals("USER")) {
            currentUsername = arg;
            logCommand("Gửi tên đăng nhập: " + arg);
            sendResponse("331 User name okay, need password.\r\n", "331 Yêu cầu mật khẩu cho tài khoản '" + arg + "'");
            return false;
        } else if (cmd.equals("PASS")) {
            logCommand("Nhập mật khẩu: ******");
            if (currentUsername != null && FtpServer.checkLogin(currentUsername, arg)) {
                isLoggedIn = true;
                Database.logActivity(clientAddress, currentUsername, "LOGIN", "Đăng nhập thành công với quyền " + currentUsername);
                sendResponse("230 User logged in, proceed.\r\n", "230 Đăng nhập thành công với quyền '" + currentUsername + "'");
            } else {
                isLoggedIn = false;
                Database.logActivity(clientAddress, currentUsername, "LOGIN_FAILED", "Đăng nhập thất bại: sai user hoặc pass");
                sendResponse("530 Not logged in, user or password incorrect.\r\n", "530 Đăng nhập thất bại: Sai tài khoản hoặc mật khẩu");
            }
            return false;
        } else if (cmd.equals("REGISTER")) {
            String[] parts = arg.split("\\s+");
            if (parts.length >= 2) {
                String regUser = parts[0].trim();
                String regPass = parts[1].trim();
                logCommand("Yêu cầu đăng ký tài khoản mới: " + regUser);
                if (FtpServer.registerUser(regUser, regPass)) {
                    Database.logActivity(clientAddress, regUser, "REGISTER", "Đăng ký thành công tài khoản mới");
                    sendResponse("200 Registration successful.\r\n", "200 Đăng ký thành công tài khoản '" + regUser + "' vào MySQL");
                } else {
                    Database.logActivity(clientAddress, regUser, "REGISTER_FAILED", "Đăng ký thất bại: tên tài khoản đã tồn tại");
                    sendResponse("550 Registration failed: Username already exists.\r\n", "550 Đăng ký thất bại: Tài khoản '" + regUser + "' đã tồn tại");
                }
            } else {
                sendResponse("501 Syntax error in parameters.\r\n", "501 Lỗi cú pháp đăng ký");
            }
            return false;
        } else if (cmd.equals("QUIT")) {
            logCommand("Yêu cầu thoát: QUIT");
            Database.logActivity(clientAddress, currentUsername, "QUIT", "Client ngắt kết nối");
            sendResponse("221 Goodbye.\r\n", "221 Tạm biệt và đóng kết nối");
            return true;
        }

        // Kiểm tra đã xác thực tài khoản chưa
        if (!isLoggedIn) {
            sendResponse("530 Please login with USER and PASS first.\r\n", "530 Chưa đăng nhập, vui lòng đăng nhập trước");
            return false;
        }

        // 2. Các lệnh quản lý tệp tin (Lưu trữ 100% trong Database MySQL bảng 'files')
        switch (cmd) {
            case "PWD":
                logCommand("Hỏi thư mục hiện tại: PWD");
                sendResponse("257 \"/\" is current directory.\r\n", "257 Thư mục hiện tại: / (MySQL Database)");
                break;

            case "CWD":
                logCommand("Yêu cầu chuyển thư mục: " + arg);
                sendResponse("250 Directory changed to \"/\".\r\n", "250 Đã vào thư mục: /");
                break;

            case "CDUP":
                logCommand("Lên thư mục cha: CDUP");
                sendResponse("250 Directory unchanged (at root).\r\n", "250 Thư mục không đổi (đang ở Root)");
                break;

            case "MKD":
                logCommand("Tạo thư mục mới: " + arg);
                sendResponse("257 \"/" + cleanFileName(arg) + "\" directory created.\r\n", "257 Đã tạo thư mục: " + cleanFileName(arg));
                break;

            case "RMD":
                logCommand("Xóa thư mục: " + arg);
                sendResponse("250 Directory removed.\r\n", "250 Đã xóa thư mục: " + cleanFileName(arg));
                break;

            case "DELE":
                String fileToDelete = cleanFileName(arg);
                logCommand("Xóa tệp tin khỏi CSDL: " + fileToDelete);
                if (Database.deleteFile(fileToDelete)) {
                    Database.logActivity(clientAddress, currentUsername, "DELETE", "Đã xóa tệp tin khỏi MySQL: " + fileToDelete);
                    sendResponse("250 File deleted.\r\n", "250 Đã xóa tệp tin '" + fileToDelete + "' khỏi CSDL MySQL");
                } else {
                    sendResponse("550 Failed to delete file.\r\n", "550 Không tìm thấy hoặc không thể xóa tệp '" + fileToDelete + "' trong CSDL");
                }
                break;

            case "RNFR":
                String srcFile = cleanFileName(arg);
                logCommand("Chọn tệp trong CSDL để đổi tên: " + srcFile);
                if (Database.getFileSize(srcFile) >= 0) {
                    renameFromStr = srcFile;
                    sendResponse("350 File exists, ready for destination name.\r\n", "350 Đã tìm thấy tệp '" + srcFile + "' trong CSDL, chờ tên mới");
                } else {
                    renameFromStr = null;
                    sendResponse("550 File not found.\r\n", "550 Không tìm thấy tệp '" + srcFile + "' trong CSDL");
                }
                break;

            case "RNTO":
                String dstFile = cleanFileName(arg);
                logCommand("Đổi sang tên mới trong CSDL: " + dstFile);
                if (renameFromStr == null) {
                    sendResponse("503 Bad sequence of commands.\r\n", "503 Lệnh sai thứ tự (cần gửi RNFR trước)");
                } else {
                    if (Database.renameFile(renameFromStr, dstFile)) {
                        Database.logActivity(clientAddress, currentUsername, "RENAME", "Đổi tên tệp từ '" + renameFromStr + "' sang '" + dstFile + "'");
                        sendResponse("250 Rename successful.\r\n", "250 Đổi tên thành công trong CSDL: " + dstFile);
                    } else {
                        sendResponse("550 Rename failed.\r\n", "550 Đổi tên thất bại trong CSDL MySQL");
                    }
                    renameFromStr = null;
                }
                break;

            // 3. Các lệnh truyền dữ liệu (Passive Mode)
            case "PASV":
                logCommand("Yêu cầu chế độ Bị động: PASV");
                handlePasv();
                break;

            case "LIST":
                logCommand("Yêu cầu danh sách tệp tin từ MySQL: LIST");
                handleList();
                break;

            case "RETR":
                logCommand("Yêu cầu tải file từ MySQL về (Download): " + arg);
                handleRetr(arg);
                break;

            case "STOR":
                logCommand("Yêu cầu tải file lên MySQL (Upload): " + arg);
                handleStor(arg);
                break;

            // 4. Các lệnh cấu hình phụ
            case "SYST":
                sendResponse("215 UNIX Type: L8\r\n", "215 Hệ điều hành UNIX Type: L8");
                break;

            case "FEAT":
                sendResponse("211-Features:\r\n UTF8\r\n SIZE\r\n211 End\r\n", "211 Hỗ trợ UTF8, SIZE");
                break;

            case "OPTS":
                sendResponse("200 OK\r\n", "200 Đồng ý thiết lập UTF-8");
                break;

            case "TYPE":
                sendResponse("200 Type set to " + (arg.isEmpty() ? "I" : arg) + ".\r\n", "200 Chuyển chế độ truyền nhị phân (Binary)");
                break;

            case "SIZE":
                String targetSizeFile = cleanFileName(arg);
                long sz = Database.getFileSize(targetSizeFile);
                if (sz >= 0) {
                    sendResponse("213 " + sz + "\r\n", "213 Kích thước file trong CSDL: " + sz + " bytes");
                } else {
                    sendResponse("550 File not found.\r\n", "550 Không tìm thấy file trong CSDL");
                }
                break;

            case "NOOP":
                sendResponse("200 OK\r\n", "200 Giữ kết nối (NOOP)");
                break;

            default:
                sendResponse("502 Command not implemented.\r\n", "502 Lệnh chưa hỗ trợ");
                break;
        }
        return false;
    }

    // PASV: Mở ServerSocket(0) ngẫu nhiên, trả về mã 227
    private void handlePasv() {
        closeDataServer();
        try {
            dataServerSocket = new ServerSocket(0);
            dataServerSocket.setSoTimeout(30000);
            int port = dataServerSocket.getLocalPort();
            int p1 = port / 256;
            int p2 = port % 256;

            String ip = "localhost".equalsIgnoreCase(serverIp.trim()) ? "127.0.0.1" : serverIp.trim();
            sendResponse("227 Entering Passive Mode (" + ip.replace('.', ',') + "," + p1 + "," + p2 + ").\r\n",
                    "227 Chế độ PASV: Mở cổng Data Port " + port + " (" + ip + ")");
        } catch (IOException e) {
            sendResponse("425 Can't open passive connection.\r\n", "425 Không thể mở cổng dữ liệu PASV");
        }
    }

    // LIST: Lấy danh sách tệp trực tiếp từ bảng files trong MySQL và gửi qua Data Socket
    private void handleList() {
        if (dataServerSocket == null || dataServerSocket.isClosed()) {
            sendResponse("425 Use PASV first.\r\n", "425 Hãy mở PASV trước");
            return;
        }

        sendResponse("150 Here comes the directory listing.\r\n", "150 Bắt đầu gửi danh sách tệp từ MySQL qua Data Port");
        try (Socket dataSocket = dataServerSocket.accept();
             BufferedWriter dw = new BufferedWriter(new OutputStreamWriter(dataSocket.getOutputStream(), StandardCharsets.UTF_8))) {

            List<Database.FileItem> files = Database.listFiles();
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd HH:mm", Locale.ENGLISH);
            for (Database.FileItem f : files) {
                String line = String.format(Locale.ENGLISH, "-rw-r--r-- 1 ftp ftp %10d %s %s\r\n",
                        f.size,
                        sdf.format(f.uploadedAt != null ? f.uploadedAt : new Date()),
                        f.name);
                dw.write(line);
            }
            dw.flush();
            sendResponse("226 Directory send OK.\r\n", "226 Đã gửi xong danh sách (" + files.size() + " tệp từ MySQL)");
        } catch (IOException e) {
            sendResponse("426 Connection closed; transfer aborted.\r\n", "426 Lỗi ngắt kết nối truyền dữ liệu");
        } finally {
            closeDataServer();
        }
    }

    // RETR: Đọc nội dung tệp tin từ MySQL LONGBLOB và gửi qua Data Socket (Download)
    private void handleRetr(String arg) {
        if (dataServerSocket == null || dataServerSocket.isClosed()) {
            sendResponse("425 Use PASV first.\r\n", "425 Hãy mở PASV trước");
            return;
        }

        String fileName = cleanFileName(arg);
        byte[] fileData = Database.getFileData(fileName);
        if (fileData == null) {
            sendResponse("550 File not found.\r\n", "550 Không tìm thấy tệp '" + fileName + "' trong MySQL để tải về");
            closeDataServer();
            return;
        }

        sendResponse("150 Opening BINARY mode data connection for " + fileName + " (" + fileData.length + " bytes).\r\n",
                "150 Đang gửi tệp '" + fileName + "' (" + fileData.length + " bytes) từ MySQL qua Data Port");
        try (Socket dataSocket = dataServerSocket.accept();
             OutputStream dos = dataSocket.getOutputStream()) {

            dos.write(fileData);
            dos.flush();
            sendResponse("226 Transfer complete.\r\n", "226 Gửi tệp '" + fileName + "' từ MySQL hoàn tất");
            Database.logActivity(clientAddress, currentUsername, "DOWNLOAD", "Tải về tệp từ MySQL: " + fileName + " (" + fileData.length + " bytes)");
        } catch (IOException e) {
            sendResponse("426 Connection closed; transfer aborted.\r\n", "426 Lỗi truyền tệp từ MySQL");
        } finally {
            closeDataServer();
        }
    }

    // STOR: Nhận dữ liệu từ Data Socket và lưu trực tiếp vào bảng files trong MySQL LONGBLOB (Upload)
    private void handleStor(String arg) {
        if (dataServerSocket == null || dataServerSocket.isClosed()) {
            sendResponse("425 Use PASV first.\r\n", "425 Hãy mở PASV trước");
            return;
        }

        String fileName = cleanFileName(arg);
        if (fileName.isEmpty()) {
            sendResponse("550 Invalid file name.\r\n", "550 Tên tệp không hợp lệ");
            closeDataServer();
            return;
        }

        sendResponse("150 Opening BINARY mode data connection for " + fileName + ".\r\n",
                "150 Đang nhận dữ liệu tệp '" + fileName + "' để nạp vào MySQL qua Data Port");
        try (Socket dataSocket = dataServerSocket.accept();
             InputStream dis = dataSocket.getInputStream()) {

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = dis.read(buffer)) != -1) {
                baos.write(buffer, 0, n);
            }

            byte[] data = baos.toByteArray();
            boolean saved = Database.saveFile(fileName, data, currentUsername);
            if (saved) {
                sendResponse("226 Transfer complete.\r\n", "226 Lưu tệp '" + fileName + "' (" + data.length + " bytes) vào MySQL thành công");
                Database.logActivity(clientAddress, currentUsername, "UPLOAD", "Tải lên tệp vào MySQL: " + fileName + " (" + data.length + " bytes)");
            } else {
                sendResponse("451 Local error in processing.\r\n", "451 Lỗi không thể lưu tệp vào MySQL Database");
            }
        } catch (IOException e) {
            sendResponse("426 Connection closed; transfer aborted.\r\n", "426 Lỗi nhận dữ liệu tệp");
        } finally {
            closeDataServer();
        }
    }

    private String cleanFileName(String path) {
        if (path == null) return "";
        path = path.trim().replace('\\', '/');
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash != -1) {
            path = path.substring(lastSlash + 1);
        }
        return path.trim();
    }

    private void logCommand(String vietnameseDesc) {
        serverUI.log("[LỆNH ĐẾN]   [" + clientAddress + "] --> " + vietnameseDesc);
    }

    private void sendResponse(String rawResponse, String vietnameseLog) {
        try {
            writer.write(rawResponse);
            writer.flush();
            serverUI.log("[PHẢN HỒI]   [" + clientAddress + "] <-- " + vietnameseLog);
        } catch (IOException e) {
            serverUI.log("[LỖI GỬI]    " + e.getMessage());
        }
    }

    private void sendResponse(String rawResponse) {
        sendResponse(rawResponse, rawResponse.replace("\r", "").replace("\n", " ").trim());
    }

    private void closeDataServer() {
        if (dataServerSocket != null && !dataServerSocket.isClosed()) {
            try {
                dataServerSocket.close();
            } catch (IOException ignored) {}
            dataServerSocket = null;
        }
    }

    public void closeConnection() {
        closeDataServer();
        try {
            if (controlSocket != null && !controlSocket.isClosed()) {
                controlSocket.close();
            }
        } catch (IOException ignored) {}
    }
}
