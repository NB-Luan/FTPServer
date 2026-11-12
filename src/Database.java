import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class Database {

    private static final String DB_URL_NO_DB = "jdbc:mysql://localhost:3306/?allowPublicKeyRetrieval=true&useSSL=false&characterEncoding=UTF-8";
    private static final String DB_URL = "jdbc:mysql://localhost:3306/ftp_db?allowPublicKeyRetrieval=true&useSSL=false&characterEncoding=UTF-8";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "";

    private static boolean isConnected = false;

    public static class FileItem {
        public String name;
        public long size;
        public Timestamp uploadedAt;

        public FileItem(String name, long size, Timestamp uploadedAt) {
            this.name = name;
            this.size = size;
            this.uploadedAt = uploadedAt;
        }
    }

    public static synchronized void init() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");

            // Tạo database ftp_db nếu chưa có
            try (Connection conn = DriverManager.getConnection(DB_URL_NO_DB, DB_USER, DB_PASS);
                 Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS ftp_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
                try {
                    stmt.execute("SET GLOBAL max_allowed_packet = 67108864");
                } catch (Exception ignored) {}
            }

            // Tạo các bảng: users, files, ftp_logs
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
                 Statement stmt = conn.createStatement()) {

                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS users ("
                        + "id INT AUTO_INCREMENT PRIMARY KEY, "
                        + "username VARCHAR(50) UNIQUE NOT NULL, "
                        + "password VARCHAR(255) NOT NULL, "
                        + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                        + ")");

                stmt.executeUpdate("INSERT IGNORE INTO users (username, password) VALUES ('admin', '123456')");
                stmt.executeUpdate("INSERT IGNORE INTO users (username, password) VALUES ('user', '123456')");

                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS files ("
                        + "id INT AUTO_INCREMENT PRIMARY KEY, "
                        + "filename VARCHAR(255) UNIQUE NOT NULL, "
                        + "filesize BIGINT NOT NULL, "
                        + "filedata LONGBLOB NOT NULL, "
                        + "uploader VARCHAR(50) NOT NULL, "
                        + "uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
                        + ")");

                try {
                    stmt.executeUpdate("ALTER TABLE files ADD COLUMN filedata LONGBLOB");
                } catch (Exception ignored) {}
                try {
                    stmt.executeUpdate("ALTER TABLE files ADD UNIQUE (filename)");
                } catch (Exception ignored) {}

                isConnected = true;

                if (getFileSize("welcome.txt") < 0) {
                    byte[] sampleBytes = "Chào mừng bạn đến với FTP Server!\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    saveFile("welcome.txt", sampleBytes, "admin");
                }

                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS ftp_logs ("
                        + "id INT AUTO_INCREMENT PRIMARY KEY, "
                        + "client_ip VARCHAR(50), "
                        + "username VARCHAR(50), "
                        + "action VARCHAR(50), "
                        + "details TEXT, "
                        + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                        + ")");
            }

            isConnected = true;
        } catch (Exception e) {
            isConnected = false;
        }
    }

    public static boolean isConnected() {
        return isConnected;
    }

    // Tài khoản
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
            return false;
        }
    }

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
            return false;
        }
    }

    // Quản lý file trong MySQL
    public static boolean saveFile(String filename, byte[] data, String uploader) {
        if (!isConnected) return false;
        String sql = "INSERT INTO files (filename, filesize, filedata, uploader) VALUES (?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE filesize = VALUES(filesize), filedata = VALUES(filedata), uploader = VALUES(uploader)";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, filename);
            ps.setLong(2, data.length);
            ps.setBytes(3, data);
            ps.setString(4, uploader != null ? uploader : "anonymous");
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    public static byte[] getFileData(String filename) {
        if (!isConnected) return null;
        String sql = "SELECT filedata FROM files WHERE filename = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, filename);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBytes("filedata");
                }
            }
        } catch (SQLException ignored) {}
        return null;
    }

    public static long getFileSize(String filename) {
        if (!isConnected) return -1;
        String sql = "SELECT filesize FROM files WHERE filename = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, filename);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong("filesize");
            }
        } catch (SQLException ignored) {}
        return -1;
    }

    public static boolean deleteFile(String filename) {
        if (!isConnected) return false;
        String sql = "DELETE FROM files WHERE filename = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, filename);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public static boolean renameFile(String oldName, String newName) {
        if (!isConnected) return false;
        String sql = "UPDATE files SET filename = ? WHERE filename = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newName);
            ps.setString(2, oldName);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public static List<FileItem> listFiles() {
        List<FileItem> list = new ArrayList<>();
        if (!isConnected) return list;
        String sql = "SELECT filename, filesize, uploaded_at FROM files ORDER BY filename ASC";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new FileItem(
                        rs.getString("filename"),
                        rs.getLong("filesize"),
                        rs.getTimestamp("uploaded_at")
                ));
            }
        } catch (SQLException ignored) {}
        return list;
    }

    // Nhật ký
    public static void logActivity(String clientIp, String username, String action, String details) {
        if (!isConnected) return;
        String sql = "INSERT INTO ftp_logs (client_ip, username, action, details) VALUES (?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, clientIp);
            ps.setString(2, username != null ? username : "");
            ps.setString(3, action);
            ps.setString(4, details != null ? details : "");
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }
}
