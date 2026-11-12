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
        public boolean isDir;
        public Timestamp uploadedAt;

        public FileItem(String name, long size, boolean isDir, Timestamp uploadedAt) {
            this.name = name;
            this.size = size;
            this.isDir = isDir;
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
                        + "filename VARCHAR(255) NOT NULL, "
                        + "parent_dir VARCHAR(255) NOT NULL DEFAULT '/', "
                        + "is_dir TINYINT(1) NOT NULL DEFAULT 0, "
                        + "filesize BIGINT NOT NULL, "
                        + "filedata LONGBLOB NULL, "
                        + "uploader VARCHAR(50) NOT NULL, "
                        + "uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
                        + ")");

                try {
                    stmt.executeUpdate("ALTER TABLE files ADD COLUMN parent_dir VARCHAR(255) NOT NULL DEFAULT '/'");
                } catch (Exception ignored) {}
                try {
                    stmt.executeUpdate("ALTER TABLE files ADD COLUMN is_dir TINYINT(1) NOT NULL DEFAULT 0");
                } catch (Exception ignored) {}
                try {
                    stmt.executeUpdate("ALTER TABLE files MODIFY COLUMN filedata LONGBLOB NULL");
                } catch (Exception ignored) {}
                try {
                    stmt.executeUpdate("ALTER TABLE files DROP INDEX filename");
                } catch (Exception ignored) {}
                try {
                    stmt.executeUpdate("ALTER TABLE files ADD UNIQUE KEY uk_parent_file (parent_dir, filename)");
                } catch (Exception ignored) {}

                isConnected = true;

                if (getFileSize("/", "welcome.txt") < 0) {
                    byte[] sampleBytes = "Chào mừng bạn đến với FTP Server!\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    saveFile("/", "welcome.txt", sampleBytes, "admin");
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

    // Quản lý Thư mục
    public static boolean createDirectory(String parentDir, String dirName, String uploader) {
        if (!isConnected || dirName == null || dirName.trim().isEmpty()) return false;
        dirName = dirName.trim();
        String sql = "INSERT INTO files (filename, parent_dir, is_dir, filesize, uploader) VALUES (?, ?, 1, 4096, ?) "
                + "ON DUPLICATE KEY UPDATE is_dir = 1";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, dirName);
            ps.setString(2, parentDir);
            ps.setString(3, uploader != null ? uploader : "anonymous");
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    public static boolean deleteDirectory(String parentDir, String dirName) {
        if (!isConnected) return false;
        String folderPath = parentDir.equals("/") ? "/" + dirName : parentDir + "/" + dirName;
        String sql = "DELETE FROM files WHERE (parent_dir = ? AND filename = ?) OR parent_dir = ? OR parent_dir LIKE ?";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, parentDir);
            ps.setString(2, dirName);
            ps.setString(3, folderPath);
            ps.setString(4, folderPath + "/%");
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public static boolean directoryExists(String fullPath) {
        if (!isConnected) return false;
        if (fullPath == null || fullPath.equals("/")) return true;
        int lastSlash = fullPath.lastIndexOf('/');
        String parent = lastSlash <= 0 ? "/" : fullPath.substring(0, lastSlash);
        String name = fullPath.substring(lastSlash + 1);
        String sql = "SELECT id FROM files WHERE parent_dir = ? AND filename = ? AND is_dir = 1";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, parent);
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    // Quản lý Tệp tin
    public static boolean saveFile(String parentDir, String filename, byte[] data, String uploader) {
        if (!isConnected) return false;
        String sql = "INSERT INTO files (filename, parent_dir, is_dir, filesize, filedata, uploader) VALUES (?, ?, 0, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE filesize = VALUES(filesize), filedata = VALUES(filedata), uploader = VALUES(uploader), is_dir = 0";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, filename);
            ps.setString(2, parentDir);
            ps.setLong(3, data.length);
            ps.setBytes(4, data);
            ps.setString(5, uploader != null ? uploader : "anonymous");
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    public static byte[] getFileData(String parentDir, String filename) {
        if (!isConnected) return null;
        String sql = "SELECT filedata FROM files WHERE parent_dir = ? AND filename = ? AND is_dir = 0";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, parentDir);
            ps.setString(2, filename);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBytes("filedata");
                }
            }
        } catch (SQLException ignored) {}
        return null;
    }

    public static long getFileSize(String parentDir, String filename) {
        if (!isConnected) return -1;
        String sql = "SELECT filesize FROM files WHERE parent_dir = ? AND filename = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, parentDir);
            ps.setString(2, filename);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong("filesize");
            }
        } catch (SQLException ignored) {}
        return -1;
    }

    public static boolean deleteFile(String parentDir, String filename) {
        if (!isConnected) return false;
        String sql = "DELETE FROM files WHERE parent_dir = ? AND filename = ? AND is_dir = 0";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, parentDir);
            ps.setString(2, filename);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public static boolean renameFile(String parentDir, String oldName, String newName) {
        if (!isConnected) return false;
        String sql = "UPDATE files SET filename = ? WHERE parent_dir = ? AND filename = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newName);
            ps.setString(2, parentDir);
            ps.setString(3, oldName);
            int rows = ps.executeUpdate();
            if (rows > 0) {
                // Nếu là thư mục, cập nhật parent_dir của các file bên trong
                String oldPath = parentDir.equals("/") ? "/" + oldName : parentDir + "/" + oldName;
                String newPath = parentDir.equals("/") ? "/" + newName : parentDir + "/" + newName;
                String updateChildSql = "UPDATE files SET parent_dir = CONCAT(?, SUBSTRING(parent_dir, ?)) WHERE parent_dir = ? OR parent_dir LIKE ?";
                try (PreparedStatement childPs = conn.prepareStatement(updateChildSql)) {
                    childPs.setString(1, newPath);
                    childPs.setInt(2, oldPath.length() + 1);
                    childPs.setString(3, oldPath);
                    childPs.setString(4, oldPath + "/%");
                    childPs.executeUpdate();
                } catch (SQLException ignored) {}
                return true;
            }
        } catch (SQLException ignored) {}
        return false;
    }

    public static List<FileItem> listFiles(String parentDir) {
        List<FileItem> list = new ArrayList<>();
        if (!isConnected) return list;
        String sql = "SELECT filename, filesize, is_dir, uploaded_at FROM files WHERE parent_dir = ? ORDER BY is_dir DESC, filename ASC";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, parentDir);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new FileItem(
                            rs.getString("filename"),
                            rs.getLong("filesize"),
                            rs.getInt("is_dir") == 1,
                            rs.getTimestamp("uploaded_at")
                    ));
                }
            }
        } catch (SQLException ignored) {}
        return list;
    }

    // Các hàm tương thích ngược (mặc định thư mục gốc '/')
    public static boolean saveFile(String filename, byte[] data, String uploader) {
        return saveFile("/", filename, data, uploader);
    }

    public static byte[] getFileData(String filename) {
        return getFileData("/", filename);
    }

    public static long getFileSize(String filename) {
        return getFileSize("/", filename);
    }

    public static boolean deleteFile(String filename) {
        return deleteFile("/", filename);
    }

    public static boolean renameFile(String oldName, String newName) {
        return renameFile("/", oldName, newName);
    }

    public static List<FileItem> listFiles() {
        return listFiles("/");
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
