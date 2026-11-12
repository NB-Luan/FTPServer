import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

public class FtpServer extends JFrame {

    private JComboBox<String> cbIp;
    private JTextField txtPort;
    private JButton btnStartStop;
    private JButton btnClearLog;
    private JLabel lblStatus;
    private JLabel lblClientCount;
    private JTextArea logArea;

    private ServerSocket serverSocket;
    private Thread serverThread;
    private volatile boolean isRunning = false;
    private final List<ClientHandler> activeClients = Collections.synchronizedList(new ArrayList<>());

    public static boolean checkLogin(String username, String password) {
        return Database.checkLogin(username, password);
    }

    public static boolean registerUser(String username, String password) {
        return Database.registerUser(username, password);
    }

    public FtpServer() {
        super("FTP Server - LTM (Lưu trữ MySQL Database)");
        initUI();
        Database.init();
        if (Database.isConnected()) {
            log("[CSDL MYSQL]  Kết nối MySQL XAMPP (ftp_db -> files, users, ftp_logs) thành công!");
            log("[LƯU TRỮ]     100% tệp tin được lưu trực tiếp dạng LONGBLOB trong CSDL MySQL (Không dùng thư mục cục bộ).");
        } else {
            log("[CSDL MYSQL]  Chưa bật MySQL XAMPP! Vui lòng bật MySQL trong XAMPP Control Panel.");
        }
    }

    private void initUI() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        setSize(860, 580);
        setMinimumSize(new Dimension(740, 500));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        getContentPane().setBackground(Color.WHITE);

        JPanel mainPanel = new JPanel(new BorderLayout(8, 8));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        mainPanel.setBackground(Color.WHITE);

        // Panel cấu hình
        JPanel configPanel = new JPanel(new GridBagLayout());
        configPanel.setBorder(BorderFactory.createTitledBorder("Cấu hình Server"));
        configPanel.setBackground(Color.WHITE);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 6, 5, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Dòng 1: IP, Port, Start/Stop, Xóa log
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        configPanel.add(new JLabel("IP Server:"), gbc);

        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 0.5;
        cbIp = new JComboBox<>(getAvailableIpAddresses().toArray(new String[0]));
        cbIp.setEditable(true);
        cbIp.setBackground(Color.WHITE);
        configPanel.add(cbIp, gbc);

        gbc.gridx = 2; gbc.gridy = 0; gbc.weightx = 0;
        configPanel.add(new JLabel("Port:"), gbc);

        gbc.gridx = 3; gbc.gridy = 0; gbc.weightx = 0.2;
        txtPort = new JTextField("2121");
        configPanel.add(txtPort, gbc);

        gbc.gridx = 4; gbc.gridy = 0; gbc.weightx = 0;
        btnStartStop = new JButton("Start");
        configPanel.add(btnStartStop, gbc);

        gbc.gridx = 5; gbc.gridy = 0; gbc.weightx = 0;
        btnClearLog = new JButton("Xóa Log");
        configPanel.add(btnClearLog, gbc);

        // Dòng 2: Thông tin Lưu trữ MySQL Database & Trạng thái chạy
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        configPanel.add(new JLabel("Lưu trữ CSDL:"), gbc);

        gbc.gridx = 1; gbc.gridy = 1; gbc.gridwidth = 4; gbc.weightx = 0.8;
        JLabel lblStorage = new JLabel("MySQL: 'ftp_db' -> Bảng files (LONGBLOB) | users | ftp_logs");
        lblStorage.setFont(lblStorage.getFont().deriveFont(Font.BOLD, 12f));
        lblStorage.setForeground(new Color(0, 102, 204));
        configPanel.add(lblStorage, gbc);

        gbc.gridx = 5; gbc.gridy = 1; gbc.gridwidth = 1; gbc.weightx = 0;
        lblStatus = new JLabel("● ĐÃ DỪNG", SwingConstants.CENTER);
        lblStatus.setForeground(new Color(220, 53, 69));
        lblStatus.setFont(lblStatus.getFont().deriveFont(Font.BOLD, 12f));
        configPanel.add(lblStatus, gbc);

        mainPanel.add(configPanel, BorderLayout.NORTH);

        // Khung in Log
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 13));
        logArea.setBackground(Color.WHITE);
        logArea.setForeground(new Color(30, 30, 30));

        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Nhật ký Server (Tiếng Việt)"));
        scrollPane.getViewport().setBackground(Color.WHITE);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // Thanh trạng thái dưới cùng
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBackground(Color.WHITE);
        JLabel lblAccounts = new JLabel("  Tài khoản CSDL: admin/123456 | user/123456 | Chế độ truyền: PASV");
        lblAccounts.setForeground(new Color(100, 100, 100));
        lblClientCount = new JLabel("Đang kết nối: 0   ");
        lblClientCount.setForeground(new Color(100, 100, 100));

        bottomPanel.add(lblAccounts, BorderLayout.WEST);
        bottomPanel.add(lblClientCount, BorderLayout.EAST);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        add(mainPanel);

        // Bắt sự kiện
        btnStartStop.addActionListener(e -> toggleServer());
        btnClearLog.addActionListener(e -> logArea.setText(""));

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                stopServer();
            }
        });
    }

    private synchronized void toggleServer() {
        if (!isRunning) {
            String ip = ((String) cbIp.getSelectedItem()).trim();
            String portStr = txtPort.getText().trim();

            if (ip.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Vui lòng nhập IP!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }

            int port;
            try {
                port = Integer.parseInt(portStr);
                if (port < 1 || port > 65535) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "Port không hợp lệ!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }

            try {
                startServer(ip, port);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Lỗi mở cổng: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        } else {
            stopServer();
        }
    }

    public synchronized void startServer(String ip, int port) throws IOException {
        if (isRunning) return;

        serverSocket = new ServerSocket(port);
        isRunning = true;

        btnStartStop.setText("Stop");
        lblStatus.setText("● ĐANG CHẠY (" + port + ")");
        lblStatus.setForeground(new Color(40, 167, 69));
        cbIp.setEnabled(false);
        txtPort.setEnabled(false);

        log("[KHỞI ĐỘNG]   ServerSocket đang lắng nghe tại cổng " + port + " | Lưu trữ: 100% CSDL MySQL (ftp_db)");

        // Đa luồng lắng nghe client
        serverThread = new Thread(() -> {
            while (isRunning) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    ClientHandler handler = new ClientHandler(clientSocket, ip, this);
                    activeClients.add(handler);
                    updateClientCount();
                    new Thread(handler).start();
                } catch (IOException e) {
                    if (!isRunning) break;
                    log("[LỖI ACCEPT]  " + e.getMessage());
                }
            }
        }, "FTP-Server-Thread");
        serverThread.start();
    }

    public synchronized void stopServer() {
        if (!isRunning) return;
        isRunning = false;

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {}

        synchronized (activeClients) {
            for (ClientHandler client : activeClients) {
                client.closeConnection();
            }
            activeClients.clear();
        }

        btnStartStop.setText("Start");
        lblStatus.setText("● ĐÃ DỪNG");
        lblStatus.setForeground(new Color(220, 53, 69));
        cbIp.setEnabled(true);
        txtPort.setEnabled(true);
        updateClientCount();

        log("[DỪNG HẲN]    Server đã dừng hoạt động và đóng toàn bộ kết nối.");
    }

    public void removeClient(ClientHandler handler) {
        activeClients.remove(handler);
        updateClientCount();
    }

    private void updateClientCount() {
        SwingUtilities.invokeLater(() -> {
            lblClientCount.setText("Đang kết nối: " + activeClients.size() + "   ");
        });
    }

    public void log(String message) {
        String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
        SwingUtilities.invokeLater(() -> {
            logArea.append("[" + time + "] " + message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private static List<String> getAvailableIpAddresses() {
        Set<String> ips = new LinkedHashSet<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces != null) {
                while (interfaces.hasMoreElements()) {
                    NetworkInterface iface = interfaces.nextElement();
                    if (!iface.isUp() || iface.isLoopback()) continue;
                    Enumeration<InetAddress> addresses = iface.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        InetAddress addr = addresses.nextElement();
                        if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                            ips.add(addr.getHostAddress());
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        ips.add("127.0.0.1");
        return new ArrayList<>(ips);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new FtpServer().setVisible(true));
    }
}
