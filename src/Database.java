import java.sql.*;

public class Database {

    private static final String DB_URL_NO_DB = "jdbc:mysql://localhost:3306/?allowPublicKeyRetrieval=true&useSSL=false";
    private static final String DB_URL = "jdbc:mysql://localhost:3306/ftp_db?allowPublicKeyRetrieval=true&useSSL=false";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "";

    private static boolean isConnected = false;

    // Khởi tạo Database và các bảng trên MySQL (XAMPP)
    public static synchronized void init() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");

            // 1. Tạo database ftp_db nếu chưa tồn tại
            try (Connection conn = DriverManager.getConnection(DB_URL_NO_DB, DB_USER, DB_PASS);
                 Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS ftp_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            }

            // 2. Tạo các bảng: users, files, ftp_logs
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
                 Statement stmt = conn.createStatement()) {

                // Bảng 1: users (Quản lý tài khoản)
                String sqlUsers = "CREATE TABLE IF NOT EXISTS users ("
                        + "id INT AUTO_INCREMENT PRIMARY KEY, "
                        + "username VARCHAR(50) UNIQUE NOT NULL, "
                        + "password VARCHAR(255) NOT NULL, "
                        + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                        + ")";
                stmt.executeUpdate(sqlUsers);

                // Thêm 2 tài khoản mặc định vào MySQL nếu chưa có
                stmt.executeUpdate("INSERT IGNORE INTO users (username, password) VALUES ('admin', '123456')");
                stmt.executeUpdate("INSERT IGNORE INTO users (username, password) VALUES ('user', '123456')");

                // Bảng 2: files (Quản lý tệp tin tải lên Server)
                String sqlFiles = "CREATE TABLE IF NOT EXISTS files ("
                        + "id INT AUTO_INCREMENT PRIMARY KEY, "
                        + "filename VARCHAR(255) NOT NULL, "
                        + "filesize BIGINT NOT NULL, "
                        + "uploader VARCHAR(50) NOT NULL, "
                        + "uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                        + ")";
                stmt.executeUpdate(sqlFiles);

                // Bảng 3: ftp_logs (Lưu toàn bộ nhật ký hoạt động vào CSDL)
                String sqlLogs = "CREATE TABLE IF NOT EXISTS ftp_logs ("
                        + "id INT AUTO_INCREMENT PRIMARY KEY, "
                        + "client_ip VARCHAR(50), "
                        + "username VARCHAR(50), "
                        + "action VARCHAR(50), "
                        + "details TEXT, "
                        + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                        + ")";
                stmt.executeUpdate(sqlLogs);
            }

            isConnected = true;
            System.out.println("[DATABASE] Đã kết nối MySQL XAMPP và đồng bộ 3 bảng (users, files, ftp_logs).");
        } catch (Exception e) {
            isConnected = false;
            System.err.println("[DATABASE ERROR] Không thể kết nối MySQL XAMPP: " + e.getMessage());
        }
    }

    public static boolean isConnected() {
        return isConnected;
    }

    // Xác thực tài khoản 100% bằng câu lệnh SQL SELECT từ bảng users
    public static boolean checkLogin(String username, String password) {
        if (!isConnected) return false;

        String sql = "SELECT id FROM users WHERE username = ? AND password = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, password);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("[DATABASE ERROR] Lỗi SELECT users: " + e.getMessage());
            return false;
        }
    }

    // Đăng ký tài khoản 100% bằng câu lệnh SQL INSERT vào bảng users
    public static boolean registerUser(String username, String password) {
        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            return false;
        }
        if (!isConnected) return false;

        String sql = "INSERT INTO users (username, password) VALUES (?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username.trim());
            ps.setString(2, password.trim());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("[DATABASE ERROR] Lỗi INSERT users: " + e.getMessage());
            return false;
        }
    }

    // Lưu thông tin tệp tin tải lên vào bảng files
    public static void logFileUploaded(String filename, long filesize, String uploader) {
        if (!isConnected) return;
        String sql = "INSERT INTO files (filename, filesize, uploader) VALUES (?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, filename);
            ps.setLong(2, filesize);
            ps.setString(3, uploader != null ? uploader : "anonymous");
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DATABASE ERROR] Lỗi INSERT files: " + e.getMessage());
        }
    }

    // Cập nhật khi tệp bị xóa khỏi bảng files
    public static void logFileDeleted(String filename) {
        if (!isConnected) return;
        String sql = "DELETE FROM files WHERE filename = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, filename);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DATABASE ERROR] Lỗi DELETE files: " + e.getMessage());
        }
    }

    // Ghi toàn bộ nhật ký thao tác vào bảng ftp_logs trong MySQL
    public static void logActivity(String clientIp, String username, String action, String details) {
        if (!isConnected) return;
        String sql = "INSERT INTO ftp_logs (client_ip, username, action, details) VALUES (?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, clientIp);
            ps.setString(2, username != null ? username : "chưa đăng nhập");
            ps.setString(3, action);
            ps.setString(4, details);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DATABASE ERROR] Lỗi INSERT ftp_logs: " + e.getMessage());
        }
    }
}
