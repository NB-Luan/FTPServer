import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
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
    private String currentDir = "/";
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
        serverUI.log("[" + clientAddress + "] Kết nối");
        try {
            reader = new BufferedReader(new InputStreamReader(controlSocket.getInputStream(), StandardCharsets.UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(controlSocket.getOutputStream(), StandardCharsets.UTF_8));

            sendRaw("220 FTP Server ready.\r\n");

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
        } catch (IOException ignored) {
        } finally {
            closeConnection();
            serverUI.removeClient(this);
            serverUI.log("[" + clientAddress + "] Ngắt kết nối");
        }
    }

    private boolean handleCommand(String cmd, String arg) throws IOException {
        // 1. Xác thực & Đăng nhập
        if (cmd.equals("USER")) {
            currentUsername = arg;
            sendRaw("331 Password required.\r\n");
            return false;
        } else if (cmd.equals("PASS")) {
            if (currentUsername != null && FtpServer.checkLogin(currentUsername, arg)) {
                isLoggedIn = true;
                serverUI.log("[" + clientAddress + "] Đăng nhập: " + currentUsername);
                Database.logActivity(clientAddress, currentUsername, "LOGIN", "Thành công");
                sendRaw("230 User logged in.\r\n");
            } else {
                isLoggedIn = false;
                serverUI.log("[" + clientAddress + "] Đăng nhập thất bại: " + currentUsername);
                Database.logActivity(clientAddress, currentUsername, "LOGIN_FAILED", "Sai mật khẩu");
                sendRaw("530 Login incorrect.\r\n");
            }
            return false;
        } else if (cmd.equals("REGISTER")) {
            String[] parts = arg.split("\\s+");
            if (parts.length >= 2) {
                String regUser = parts[0].trim();
                String regPass = parts[1].trim();
                if (FtpServer.registerUser(regUser, regPass)) {
                    serverUI.log("[" + clientAddress + "] Đăng ký: " + regUser);
                    Database.logActivity(clientAddress, regUser, "REGISTER", "Thành công");
                    sendRaw("200 Registration successful.\r\n");
                } else {
                    Database.logActivity(clientAddress, regUser, "REGISTER_FAILED", "Trùng tài khoản");
                    sendRaw("550 Username already exists.\r\n");
                }
            } else {
                sendRaw("501 Syntax error.\r\n");
            }
            return false;
        } else if (cmd.equals("QUIT")) {
            sendRaw("221 Goodbye.\r\n");
            return true;
        }

        if (!isLoggedIn) {
            sendRaw("530 Please login first.\r\n");
            return false;
        }

        // 2. Thao tác tệp tin & Thư mục
        switch (cmd) {
            case "PWD":
                sendRaw("257 \"" + currentDir + "\" is current directory.\r\n");
                break;

            case "CWD":
                String target = arg.trim();
                if (target.isEmpty() || target.equals(".")) {
                    sendRaw("250 Directory unchanged.\r\n");
                } else if (target.equals("..")) {
                    handleCdup();
                } else {
                    String newPath = resolvePath(currentDir, target);
                    if (Database.directoryExists(newPath)) {
                        currentDir = newPath;
                        sendRaw("250 Directory changed to \"" + currentDir + "\".\r\n");
                    } else {
                        sendRaw("550 Directory not found.\r\n");
                    }
                }
                break;

            case "CDUP":
                handleCdup();
                break;

            case "MKD":
                String newDirName = cleanFileName(arg);
                if (Database.createDirectory(currentDir, newDirName, currentUsername)) {
                    serverUI.log("[" + clientAddress + "] (" + currentUsername + ") Tạo thư mục: " + newDirName);
                    Database.logActivity(clientAddress, currentUsername, "MKDIR", newDirName);
                    sendRaw("257 \"/" + newDirName + "\" created.\r\n");
                } else {
                    sendRaw("550 Failed to create directory.\r\n");
                }
                break;

            case "RMD":
                String rmDirName = cleanFileName(arg);
                if (Database.deleteDirectory(currentDir, rmDirName)) {
                    serverUI.log("[" + clientAddress + "] (" + currentUsername + ") Xóa thư mục: " + rmDirName);
                    Database.logActivity(clientAddress, currentUsername, "RMDIR", rmDirName);
                    sendRaw("250 Directory removed.\r\n");
                } else {
                    sendRaw("550 Failed to remove directory.\r\n");
                }
                break;

            case "DELE":
                String fileToDelete = cleanFileName(arg);
                if (Database.deleteFile(currentDir, fileToDelete)) {
                    serverUI.log("[" + clientAddress + "] (" + currentUsername + ") Xóa: " + fileToDelete);
                    Database.logActivity(clientAddress, currentUsername, "DELETE", fileToDelete);
                    sendRaw("250 File deleted.\r\n");
                } else {
                    sendRaw("550 File not found.\r\n");
                }
                break;

            case "RNFR":
                String srcFile = cleanFileName(arg);
                if (Database.getFileSize(currentDir, srcFile) >= 0 || Database.directoryExists(resolvePath(currentDir, srcFile))) {
                    renameFromStr = srcFile;
                    sendRaw("350 Ready for destination name.\r\n");
                } else {
                    renameFromStr = null;
                    sendRaw("550 File not found.\r\n");
                }
                break;

            case "RNTO":
                String dstFile = cleanFileName(arg);
                if (renameFromStr == null) {
                    sendRaw("503 Bad sequence.\r\n");
                } else {
                    if (Database.renameFile(currentDir, renameFromStr, dstFile)) {
                        serverUI.log("[" + clientAddress + "] (" + currentUsername + ") Đổi tên: " + renameFromStr + " -> " + dstFile);
                        Database.logActivity(clientAddress, currentUsername, "RENAME", renameFromStr + " -> " + dstFile);
                        sendRaw("250 Rename successful.\r\n");
                    } else {
                        sendRaw("550 Rename failed.\r\n");
                    }
                    renameFromStr = null;
                }
                break;

            case "PASV":
                handlePasv();
                break;

            case "LIST":
                handleList();
                break;

            case "RETR":
                handleRetr(arg);
                break;

            case "STOR":
                handleStor(arg);
                break;

            case "SIZE":
                long sz = Database.getFileSize(currentDir, cleanFileName(arg));
                if (sz >= 0) sendRaw("213 " + sz + "\r\n");
                else sendRaw("550 File not found.\r\n");
                break;

            case "SYST":
                sendRaw("215 UNIX Type: L8\r\n");
                break;

            case "FEAT":
                sendRaw("211-Features:\r\n UTF8\r\n SIZE\r\n211 End\r\n");
                break;

            case "OPTS":
            case "NOOP":
                sendRaw("200 OK\r\n");
                break;

            case "TYPE":
                sendRaw("200 Type set to " + (arg.isEmpty() ? "I" : arg) + ".\r\n");
                break;

            default:
                sendRaw("502 Command not implemented.\r\n");
                break;
        }
        return false;
    }

    private void handleCdup() {
        if (currentDir.equals("/")) {
            sendRaw("250 Directory unchanged (at root).\r\n");
            return;
        }
        int lastSlash = currentDir.lastIndexOf('/');
        currentDir = lastSlash <= 0 ? "/" : currentDir.substring(0, lastSlash);
        sendRaw("250 Directory changed to \"" + currentDir + "\".\r\n");
    }

    private String resolvePath(String current, String target) {
        if (target == null || target.trim().isEmpty() || target.equals(".")) return current;
        target = target.trim().replace('\\', '/');
        if (target.equals("/")) return "/";

        String fullPath = target.startsWith("/") ? target : (current.equals("/") ? "/" + target : current + "/" + target);
        String[] parts = fullPath.split("/");
        List<String> stack = new ArrayList<>();
        for (String p : parts) {
            if (p.isEmpty() || p.equals(".")) continue;
            if (p.equals("..")) {
                if (!stack.isEmpty()) stack.remove(stack.size() - 1);
            } else {
                stack.add(p);
            }
        }
        if (stack.isEmpty()) return "/";
        return "/" + String.join("/", stack);
    }

    private void handlePasv() {
        closeDataServer();
        try {
            dataServerSocket = new ServerSocket(0);
            dataServerSocket.setSoTimeout(30000);
            int port = dataServerSocket.getLocalPort();
            int p1 = port / 256;
            int p2 = port % 256;

            String ip = "localhost".equalsIgnoreCase(serverIp.trim()) ? "127.0.0.1" : serverIp.trim();
            sendRaw("227 Entering Passive Mode (" + ip.replace('.', ',') + "," + p1 + "," + p2 + ").\r\n");
        } catch (IOException e) {
            sendRaw("425 Can't open passive connection.\r\n");
        }
    }

    private void handleList() {
        if (dataServerSocket == null || dataServerSocket.isClosed()) {
            sendRaw("425 Use PASV first.\r\n");
            return;
        }

        sendRaw("150 Opening data connection.\r\n");
        try (Socket dataSocket = dataServerSocket.accept();
             BufferedWriter dw = new BufferedWriter(new OutputStreamWriter(dataSocket.getOutputStream(), StandardCharsets.UTF_8))) {

            List<Database.FileItem> files = Database.listFiles(currentDir);
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd HH:mm", Locale.ENGLISH);
            for (Database.FileItem f : files) {
                String perms = f.isDir ? "drwxr-xr-x" : "-rw-r--r--";
                long size = f.isDir ? 4096 : f.size;
                String line = String.format(Locale.ENGLISH, "%s 1 ftp ftp %10d %s %s\r\n",
                        perms,
                        size,
                        sdf.format(f.uploadedAt != null ? f.uploadedAt : new Date()),
                        f.name);
                dw.write(line);
            }
            dw.flush();
            sendRaw("226 Transfer complete.\r\n");
        } catch (IOException e) {
            sendRaw("426 Transfer aborted.\r\n");
        } finally {
            closeDataServer();
        }
    }

    private void handleRetr(String arg) {
        if (dataServerSocket == null || dataServerSocket.isClosed()) {
            sendRaw("425 Use PASV first.\r\n");
            return;
        }

        String fileName = cleanFileName(arg);
        byte[] fileData = Database.getFileData(currentDir, fileName);
        if (fileData == null) {
            sendRaw("550 File not found.\r\n");
            closeDataServer();
            return;
        }

        sendRaw("150 Opening BINARY mode data connection for " + fileName + " (" + fileData.length + " bytes).\r\n");
        try (Socket dataSocket = dataServerSocket.accept();
             OutputStream dos = dataSocket.getOutputStream()) {

            dos.write(fileData);
            dos.flush();
            sendRaw("226 Transfer complete.\r\n");
            serverUI.log("[" + clientAddress + "] (" + currentUsername + ") Download: " + fileName + " (" + formatSize(fileData.length) + ")");
            Database.logActivity(clientAddress, currentUsername, "DOWNLOAD", fileName + " (" + fileData.length + " bytes)");
        } catch (IOException e) {
            sendRaw("426 Transfer aborted.\r\n");
        } finally {
            closeDataServer();
        }
    }

    private void handleStor(String arg) {
        if (dataServerSocket == null || dataServerSocket.isClosed()) {
            sendRaw("425 Use PASV first.\r\n");
            return;
        }

        String fileName = cleanFileName(arg);
        if (fileName.isEmpty()) {
            sendRaw("550 Invalid file name.\r\n");
            closeDataServer();
            return;
        }

        sendRaw("150 Opening BINARY mode data connection for " + fileName + ".\r\n");
        try (Socket dataSocket = dataServerSocket.accept();
             InputStream dis = dataSocket.getInputStream()) {

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = dis.read(buffer)) != -1) {
                baos.write(buffer, 0, n);
            }

            byte[] data = baos.toByteArray();
            if (Database.saveFile(currentDir, fileName, data, currentUsername)) {
                sendRaw("226 Transfer complete.\r\n");
                serverUI.log("[" + clientAddress + "] (" + currentUsername + ") Upload: " + fileName + " (" + formatSize(data.length) + ")");
                Database.logActivity(clientAddress, currentUsername, "UPLOAD", fileName + " (" + data.length + " bytes)");
            } else {
                sendRaw("451 Database write error.\r\n");
            }
        } catch (IOException e) {
            sendRaw("426 Transfer aborted.\r\n");
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

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.ENGLISH, "%.1f KB", bytes / 1024.0);
        return String.format(Locale.ENGLISH, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private void sendRaw(String rawResponse) {
        try {
            writer.write(rawResponse);
            writer.flush();
        } catch (IOException ignored) {}
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
