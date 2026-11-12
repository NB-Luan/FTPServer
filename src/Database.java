import java.sql.*;

public class Database {

    private static final String DB_URL_NO_DB = "jdbc:mysql://localhost:3306/?allowPublicKeyRetrieval=true&useSSL=false";
    private static final String DB_URL = "jdbc:mysql://localhost:3306/ftp_db?allowPublicKeyRetrieval=true&useSSL=false";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "";

    private static boolean isConnected = false;

    // Tự động khởi tạo Database và Table trên XAMPP MySQL
    public static synchronized void init() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");

            // 1. Tạo database ftp_db nếu chưa có
            try (Connection conn = DriverManager.getConnection(DB_URL_NO_DB, DB_USER, DB_PASS);
                 Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS ftp_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            }

            // 2. Tạo bảng users nếu chưa có
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
                 Statement stmt = conn.createStatement()) {
                String sql = "CREATE TABLE IF NOT EXISTS users ("
                        + "id INT AUTO_INCREMENT PRIMARY KEY, "
                        + "username VARCHAR(50) UNIQUE NOT NULL, "
                        + "password VARCHAR(255) NOT NULL, "
                        + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                        + ")";
                stmt.executeUpdate(sql);

                // Thêm sẵn 2 tài khoản mặc định
                stmt.executeUpdate("INSERT IGNORE INTO users (username, password) VALUES ('admin', '123456')");
                stmt.executeUpdate("INSERT IGNORE INTO users (username, password) VALUES ('user', '123456')");
            }

            isConnected = true;
            System.out.println("[DATABASE] Kết nối MySQL (XAMPP) thành công! Đã sẵn sàng.");
        } catch (Exception e) {
            isConnected = false;
            System.err.println("[DATABASE WARNING] Không thể kết nối MySQL (XAMPP): " + e.getMessage());
        }
    }

    public static boolean isConnected() {
        return isConnected;
    }

    // Kiểm tra đăng nhập (SELECT)
    public static boolean checkLogin(String username, String password) {
        if (!isConnected) {
            // Dự phòng nếu chưa bật XAMPP: dùng tài khoản mặc định
            return ("admin".equals(username) && "123456".equals(password))
                    || ("user".equals(username) && "123456".equals(password));
        }

        String sql = "SELECT id FROM users WHERE username = ? AND password = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, password);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("[DATABASE ERROR] Lỗi kiểm tra đăng nhập: " + e.getMessage());
            return false;
        }
    }

    // Đăng ký tài khoản mới (INSERT)
    public static boolean registerUser(String username, String password) {
        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            return false;
        }

        if (!isConnected) {
            System.err.println("[DATABASE ERROR] Chưa kết nối được MySQL XAMPP để lưu tài khoản!");
            return false;
        }

        String sql = "INSERT INTO users (username, password) VALUES (?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username.trim());
            ps.setString(2, password.trim());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("[DATABASE ERROR] Lỗi đăng ký (có thể trùng tên): " + e.getMessage());
            return false;
        }
    }
}

