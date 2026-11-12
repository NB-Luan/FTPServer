import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ClientHandler implements Runnable {

    private final Socket controlSocket;
    private final File rootDirectory;
    private File currentDirectory;
    private final String serverIp;
    private final FtpServer serverUI;
    private final String clientAddress;

    private BufferedReader reader;
    private BufferedWriter writer;
    private boolean isLoggedIn = false;
    private String currentUsername = null;
    private ServerSocket dataServerSocket = null;
    private File renameFrom = null;

    public ClientHandler(Socket socket, File rootDir, String serverIp, FtpServer serverUI) {
        this.controlSocket = socket;
        this.rootDirectory = rootDir;
        this.currentDirectory = rootDir;
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

            sendResponse("220 Welcome to Java Custom FTP Server\r\n", "220 Chào mừng Client kết nối");

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

        // 2. Các lệnh quản lý thư mục & tệp tin
        switch (cmd) {
            case "PWD":
                logCommand("Hỏi thư mục hiện tại: PWD");
                sendResponse("257 \"" + getFtpPath(currentDirectory) + "\" is current directory.\r\n", "257 Thư mục hiện tại: " + getFtpPath(currentDirectory));
                break;

            case "CWD":
                logCommand("Yêu cầu chuyển thư mục: " + arg);
                if (arg.equals("..")) {
                    handleCdup();
                } else if (arg.equals(".")) {
                    sendResponse("250 Directory unchanged.\r\n", "250 Thư mục không đổi");
                } else {
                    File target = resolvePath(arg);
                    if (target != null && target.exists() && target.isDirectory()) {
                        currentDirectory = target;
                        sendResponse("250 Directory changed to \"" + getFtpPath(currentDirectory) + "\".\r\n", "250 Đã vào thư mục: " + getFtpPath(currentDirectory));
                    } else {
                        sendResponse("550 Directory not found.\r\n", "550 Không tìm thấy thư mục: " + arg);
                    }
                }
                break;

            case "CDUP":
                logCommand("Lên thư mục cha: CDUP");
                handleCdup();
                break;

            case "MKD":
                logCommand("Tạo thư mục mới: " + arg);
                File newDir = resolvePath(arg);
                if (newDir != null && !newDir.exists() && newDir.mkdir()) {
                    Database.logActivity(clientAddress, currentUsername, "MKDIR", "Đã tạo thư mục: " + getFtpPath(newDir));
                    sendResponse("257 \"" + getFtpPath(newDir) + "\" directory created.\r\n", "257 Đã tạo thư mục: " + getFtpPath(newDir));
                } else {
                    sendResponse("550 Failed to create directory.\r\n", "550 Tạo thư mục thất bại");
                }
                break;

            case "RMD":
                logCommand("Xóa thư mục: " + arg);
                File dirToRemove = resolvePath(arg);
                if (dirToRemove != null && dirToRemove.exists() && dirToRemove.isDirectory()
                        && !dirToRemove.equals(rootDirectory) && dirToRemove.delete()) {
                    Database.logActivity(clientAddress, currentUsername, "RMDIR", "Đã xóa thư mục: " + arg);
                    sendResponse("250 Directory removed.\r\n", "250 Đã xóa thư mục: " + arg);
                } else {
                    sendResponse("550 Failed to remove directory.\r\n", "550 Xóa thư mục thất bại");
                }
                break;

            case "DELE":
                logCommand("Xóa tệp tin: " + arg);
                File fileToDelete = resolvePath(arg);
                if (fileToDelete != null && fileToDelete.exists() && fileToDelete.isFile() && fileToDelete.delete()) {
                    Database.logFileDeleted(fileToDelete.getName());
                    Database.logActivity(clientAddress, currentUsername, "DELETE", "Đã xóa tệp tin: " + arg);
                    sendResponse("250 File deleted.\r\n", "250 Đã xóa tệp tin: " + arg);
                } else {
                    sendResponse("550 Failed to delete file.\r\n", "550 Xóa tệp tin thất bại");
                }
                break;

            case "RNFR":
                logCommand("Chọn tệp để đổi tên: " + arg);
                File src = resolvePath(arg);
                if (src != null && src.exists()) {
                    renameFrom = src;
                    sendResponse("350 File exists, ready for destination name.\r\n", "350 Đã tìm thấy tệp, chờ tên mới");
                } else {
                    renameFrom = null;
                    sendResponse("550 File not found.\r\n", "550 Không tìm thấy tệp cần đổi tên");
                }
                break;

            case "RNTO":
                logCommand("Đổi sang tên mới: " + arg);
                if (renameFrom == null) {
                    sendResponse("503 Bad sequence of commands.\r\n", "503 Lệnh sai thứ tự (cần gửi RNFR trước)");
                } else {
                    File dst = resolvePath(arg);
                    if (dst != null && renameFrom.renameTo(dst)) {
                        Database.logActivity(clientAddress, currentUsername, "RENAME", "Đổi tên từ '" + renameFrom.getName() + "' sang '" + dst.getName() + "'");
                        sendResponse("250 Rename successful.\r\n", "250 Đổi tên thành công: " + arg);
                    } else {
                        sendResponse("550 Rename failed.\r\n", "550 Đổi tên thất bại");
                    }
                    renameFrom = null;
                }
                break;

            // 3. Các lệnh truyền dữ liệu (Passive Mode)
            case "PASV":
                logCommand("Yêu cầu chế độ Bị động: PASV");
                handlePasv();
                break;

            case "LIST":
                logCommand("Yêu cầu danh sách tệp tin: LIST");
                handleList(arg);
                break;

            case "RETR":
                logCommand("Yêu cầu tải file về (Download): " + arg);
                handleRetr(arg);
                break;

            case "STOR":
                logCommand("Yêu cầu tải file lên (Upload): " + arg);
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
                File sf = resolvePath(arg);
                if (sf != null && sf.exists() && sf.isFile()) {
                    sendResponse("213 " + sf.length() + "\r\n", "213 Kích thước file: " + sf.length() + " bytes");
                } else {
                    sendResponse("550 File not found.\r\n", "550 Không tìm thấy file");
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

    private void handleCdup() {
        if (currentDirectory.equals(rootDirectory)) {
            sendResponse("250 Directory unchanged (at root).\r\n", "250 Thư mục không đổi (đang ở Root)");
            return;
        }
        File parent = currentDirectory.getParentFile();
        currentDirectory = (parent != null && isUnderRoot(parent)) ? parent : rootDirectory;
        sendResponse("250 Directory changed to \"" + getFtpPath(currentDirectory) + "\".\r\n", "250 Đã trở về thư mục cha: " + getFtpPath(currentDirectory));
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

    // LIST: Gửi danh sách file định dạng ls -l qua Data Socket
    private void handleList(String arg) {
        if (dataServerSocket == null || dataServerSocket.isClosed()) {
            sendResponse("425 Use PASV first.\r\n", "425 Hãy mở PASV trước");
            return;
        }

        File targetDir = currentDirectory;
        if (!arg.isEmpty() && !arg.startsWith("-")) {
            File specified = resolvePath(arg);
            if (specified != null && specified.exists() && specified.isDirectory()) targetDir = specified;
        }

        sendResponse("150 Here comes the directory listing.\r\n", "150 Bắt đầu gửi danh sách tệp qua Data Port");
        try (Socket dataSocket = dataServerSocket.accept();
             BufferedWriter dw = new BufferedWriter(new OutputStreamWriter(dataSocket.getOutputStream(), StandardCharsets.UTF_8))) {

            File[] files = targetDir.listFiles();
            int count = 0;
            if (files != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd HH:mm", Locale.ENGLISH);
                for (File f : files) {
                    String perms = f.isDirectory() ? "drwxr-xr-x" : "-rw-r--r--";
                    long size = f.isDirectory() ? 4096 : f.length();
                    String line = String.format(Locale.ENGLISH, "%s 1 ftp ftp %10d %s %s\r\n",
                            perms, size, sdf.format(new Date(f.lastModified())), f.getName());
                    dw.write(line);
                    count++;
                }
            }
            dw.flush();
            dataSocket.close();
            sendResponse("226 Directory send OK.\r\n", "226 Đã gửi xong danh sách (" + count + " mục)");
        } catch (IOException e) {
            sendResponse("426 Connection closed; transfer aborted.\r\n", "426 Lỗi ngắt kết nối truyền dữ liệu");
        } finally {
            closeDataServer();
        }
    }

    // RETR: Đọc file từ đĩa Server và gửi qua Data Socket
    private void handleRetr(String arg) {
        if (dataServerSocket == null || dataServerSocket.isClosed()) {
            sendResponse("425 Use PASV first.\r\n", "425 Hãy mở PASV trước");
            return;
        }

        File file = resolvePath(arg);
        if (file == null || !file.exists() || file.isDirectory()) {
            sendResponse("550 File not found.\r\n", "550 Không tìm thấy tệp để tải về");
            closeDataServer();
            return;
        }

        sendResponse("150 Opening BINARY mode data connection for " + file.getName() + " (" + file.length() + " bytes).\r\n",
                "150 Đang gửi tệp '" + file.getName() + "' (" + file.length() + " bytes) qua Data Port");
        try (Socket dataSocket = dataServerSocket.accept();
             FileInputStream fis = new FileInputStream(file);
             OutputStream dos = dataSocket.getOutputStream()) {

            byte[] buffer = new byte[4096];
            int n;
            while ((n = fis.read(buffer)) != -1) {
                dos.write(buffer, 0, n);
            }
            dos.flush();
            dataSocket.close();
            sendResponse("226 Transfer complete.\r\n", "226 Gửi tệp '" + file.getName() + "' hoàn tất");
            Database.logActivity(clientAddress, currentUsername, "DOWNLOAD", "Tải về tệp: " + file.getName() + " (" + file.length() + " bytes)");
        } catch (IOException e) {
            sendResponse("426 Connection closed; transfer aborted.\r\n", "426 Lỗi truyền tệp");
        } finally {
            closeDataServer();
        }
    }

    // STOR: Nhận dữ liệu từ Data Socket và ghi thành file trên Server
    private void handleStor(String arg) {
        if (dataServerSocket == null || dataServerSocket.isClosed()) {
            sendResponse("425 Use PASV first.\r\n", "425 Hãy mở PASV trước");
            return;
        }

        File file = resolvePath(arg);
        if (file == null) {
            sendResponse("550 Access denied.\r\n", "550 Từ chối truy cập đường dẫn");
            closeDataServer();
            return;
        }

        sendResponse("150 Opening BINARY mode data connection for " + file.getName() + ".\r\n",
                "150 Đang nhận dữ liệu tệp '" + file.getName() + "' qua Data Port");
        try (Socket dataSocket = dataServerSocket.accept();
             InputStream dis = dataSocket.getInputStream();
             FileOutputStream fos = new FileOutputStream(file)) {

            byte[] buffer = new byte[4096];
            int n;
            while ((n = dis.read(buffer)) != -1) {
                fos.write(buffer, 0, n);
            }
            fos.flush();
            dataSocket.close();
            sendResponse("226 Transfer complete.\r\n", "226 Lưu tệp '" + file.getName() + "' lên Server thành công");
            Database.logFileUploaded(file.getName(), file.length(), currentUsername);
            Database.logActivity(clientAddress, currentUsername, "UPLOAD", "Tải lên tệp: " + file.getName() + " (" + file.length() + " bytes)");
        } catch (IOException e) {
            sendResponse("426 Connection closed; transfer aborted.\r\n", "426 Lỗi nhận dữ liệu tệp");
        } finally {
            closeDataServer();
        }
    }

    private boolean isUnderRoot(File file) {
        try {
            String root = rootDirectory.getCanonicalPath();
            String f = file.getCanonicalPath();
            return f.equals(root) || f.startsWith(root + File.separator);
        } catch (IOException e) {
            return false;
        }
    }

    private File resolvePath(String path) {
        if (path == null || path.trim().isEmpty()) return currentDirectory;
        path = path.trim();
        File target = path.startsWith("/") ? new File(rootDirectory, path.substring(1)) : new File(currentDirectory, path);
        if (isUnderRoot(target)) {
            try {
                return target.getCanonicalFile();
            } catch (IOException ignored) {}
        }
        return null;
    }

    private String getFtpPath(File file) {
        try {
            String root = rootDirectory.getCanonicalPath();
            String f = file.getCanonicalPath();
            if (f.equals(root)) return "/";
            String rel = f.substring(root.length()).replace(File.separatorChar, '/');
            return rel.startsWith("/") ? rel : "/" + rel;
        } catch (IOException e) {
            return "/";
        }
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
