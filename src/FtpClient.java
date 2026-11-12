import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

public class FtpClient extends JFrame {

    private CardLayout cardLayout;
    private JPanel cardsPanel;

    // --- Trang Đăng nhập & Đăng ký ---
    private JTextField txtLoginHost;
    private JTextField txtLoginPort;
    private JTextField txtLoginUser;
    private JPasswordField txtLoginPass;
    private JButton btnDoLogin;
    private JButton btnDoRegister;

    // --- Trang Quản lý File (Sau khi đăng nhập) ---
    private JLabel lblUserInfo;
    private JLabel lblCurrentPath;
    private JButton btnLogout;
    private JButton btnUp;
    private JButton btnRefresh;
    private JTable fileTable;
    private DefaultTableModel tableModel;

    private JButton btnUpload;
    private JButton btnDownload;
    private JButton btnView;
    private JButton btnNewFolder;
    private JButton btnRename;
    private JButton btnDelete;
    private JButton btnClearLog;

    private JTextArea logArea;

    // --- Biến Socket ---
    private Socket controlSocket;
    private BufferedReader reader;
    private BufferedWriter writer;
    private boolean isConnected = false;
    private String currentPath = "/";
    private String loggedInUser = "";

    public FtpClient() {
        super("FTP Client - Hệ thống truyền tập tin");
        initUI();
    }

    private void initUI() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        setSize(920, 660);
        setMinimumSize(new Dimension(800, 550));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        getContentPane().setBackground(Color.WHITE);

        cardLayout = new CardLayout();
        cardsPanel = new JPanel(cardLayout);
        cardsPanel.setBackground(Color.WHITE);

        cardsPanel.add(createAuthPanel(), "AUTH");
        cardsPanel.add(createMainPanel(), "MAIN");

        add(cardsPanel);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                disconnect();
            }
        });

        cardLayout.show(cardsPanel, "AUTH");
    }

    /**
     * Màn hình 1: Giao diện Đăng nhập & Đăng ký đơn giản, màu trắng
     */
    private JPanel createAuthPanel() {
        JPanel wrapper = new JPanel(new GridBagLayout());
        wrapper.setBackground(Color.WHITE);

        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBackground(Color.WHITE);
        box.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(220, 224, 230), 1, true),
                new EmptyBorder(25, 30, 25, 30)
        ));
        box.setPreferredSize(new Dimension(380, 370));

        JLabel lblTitle = new JLabel("FTP CLIENT", SwingConstants.CENTER);
        lblTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 22));
        lblTitle.setForeground(new Color(24, 43, 73));
        box.add(lblTitle);

        JLabel lblSub = new JLabel("Điền tài khoản để Đăng nhập hoặc Đăng ký", SwingConstants.CENTER);
        lblSub.setAlignmentX(Component.CENTER_ALIGNMENT);
        lblSub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblSub.setForeground(new Color(110, 120, 135));
        box.add(lblSub);
        box.add(Box.createVerticalStrut(20));

        // Host & Port
        JPanel hostPortPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        hostPortPanel.setBackground(Color.WHITE);
        hostPortPanel.setMaximumSize(new Dimension(320, 45));

        JPanel hostBox = new JPanel(new BorderLayout(2, 2));
        hostBox.setBackground(Color.WHITE);
        hostBox.add(new JLabel("Host:"), BorderLayout.NORTH);
        txtLoginHost = new JTextField("127.0.0.1");
        hostBox.add(txtLoginHost, BorderLayout.CENTER);

        JPanel portBox = new JPanel(new BorderLayout(2, 2));
        portBox.setBackground(Color.WHITE);
        portBox.add(new JLabel("Port:"), BorderLayout.NORTH);
        txtLoginPort = new JTextField("2121");
        portBox.add(txtLoginPort, BorderLayout.CENTER);

        hostPortPanel.add(hostBox);
        hostPortPanel.add(portBox);
        box.add(hostPortPanel);
        box.add(Box.createVerticalStrut(12));

        // Username
        JPanel userBox = new JPanel(new BorderLayout(2, 2));
        userBox.setBackground(Color.WHITE);
        userBox.setMaximumSize(new Dimension(320, 48));
        userBox.add(new JLabel("Tên tài khoản:"), BorderLayout.NORTH);
        txtLoginUser = new JTextField("admin");
        txtLoginUser.setPreferredSize(new Dimension(320, 28));
        userBox.add(txtLoginUser, BorderLayout.CENTER);
        box.add(userBox);
        box.add(Box.createVerticalStrut(10));

        // Password
        JPanel passBox = new JPanel(new BorderLayout(2, 2));
        passBox.setBackground(Color.WHITE);
        passBox.setMaximumSize(new Dimension(320, 48));
        passBox.add(new JLabel("Mật khẩu:"), BorderLayout.NORTH);
        txtLoginPass = new JPasswordField("123456");
        txtLoginPass.setPreferredSize(new Dimension(320, 28));
        passBox.add(txtLoginPass, BorderLayout.CENTER);
        box.add(passBox);
        box.add(Box.createVerticalStrut(22));

        // Nút cơ bản: Đăng nhập & Đăng ký
        JPanel btnPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        btnPanel.setBackground(Color.WHITE);
        btnPanel.setMaximumSize(new Dimension(320, 32));

        btnDoLogin = new JButton("Đăng nhập");
        btnDoRegister = new JButton("Đăng ký");

        btnPanel.add(btnDoLogin);
        btnPanel.add(btnDoRegister);
        box.add(btnPanel);

        wrapper.add(box);

        btnDoLogin.addActionListener(e -> performLogin());
        btnDoRegister.addActionListener(e -> performRegister());

        return wrapper;
    }

    /**
     * Màn hình 2: Giao diện Quản lý File chính (Màu trắng, sạch sẽ)
     */
    private JPanel createMainPanel() {
        JPanel main = new JPanel(new BorderLayout(8, 8));
        main.setBackground(Color.WHITE);
        main.setBorder(new EmptyBorder(10, 12, 10, 12));

        // Header trên cùng
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(Color.WHITE);
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(230, 230, 230), 1),
                new EmptyBorder(8, 12, 8, 12)
        ));

        lblUserInfo = new JLabel("Đã kết nối với tư cách: admin");
        lblUserInfo.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblUserInfo.setForeground(new Color(40, 167, 69));

        btnLogout = new JButton("Đăng xuất");

        headerPanel.add(lblUserInfo, BorderLayout.WEST);
        headerPanel.add(btnLogout, BorderLayout.EAST);
        main.add(headerPanel, BorderLayout.NORTH);

        // Center: Bảng file + Thanh công cụ
        JPanel centerPanel = new JPanel(new BorderLayout(6, 6));
        centerPanel.setBackground(Color.WHITE);

        // Thanh đường dẫn
        JPanel pathPanel = new JPanel(new BorderLayout(6, 6));
        pathPanel.setBackground(Color.WHITE);

        lblCurrentPath = new JLabel(" Thư mục: /");
        lblCurrentPath.setFont(new Font("Segoe UI", Font.BOLD, 13));
        pathPanel.add(lblCurrentPath, BorderLayout.WEST);

        JPanel pathBtn = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        pathBtn.setBackground(Color.WHITE);
        btnUp = new JButton("Lên thư mục cha (CDUP)");
        btnRefresh = new JButton("Làm mới (LIST)");
        pathBtn.add(btnUp);
        pathBtn.add(btnRefresh);
        pathPanel.add(pathBtn, BorderLayout.EAST);

        centerPanel.add(pathPanel, BorderLayout.NORTH);

        // Bảng danh sách file
        String[] columns = {"Loại", "Tên tệp tin / Thư mục", "Kích thước (Bytes)", "Ngày sửa đổi"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        fileTable = new JTable(tableModel);
        fileTable.setBackground(Color.WHITE);
        fileTable.setForeground(new Color(33, 37, 41));
        fileTable.setGridColor(new Color(240, 240, 240));
        fileTable.setRowHeight(24);
        fileTable.setSelectionBackground(new Color(210, 235, 255));
        fileTable.setSelectionForeground(Color.BLACK);
        fileTable.getTableHeader().setBackground(new Color(245, 247, 250));
        fileTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));

        fileTable.getColumnModel().getColumn(0).setPreferredWidth(80);
        fileTable.getColumnModel().getColumn(1).setPreferredWidth(350);
        fileTable.getColumnModel().getColumn(2).setPreferredWidth(130);
        fileTable.getColumnModel().getColumn(3).setPreferredWidth(160);

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        fileTable.getColumnModel().getColumn(0).setCellRenderer(centerRenderer);

        JScrollPane tableScroll = new JScrollPane(fileTable);
        tableScroll.getViewport().setBackground(Color.WHITE);
        tableScroll.setBorder(new LineBorder(new Color(225, 225, 225), 1));
        centerPanel.add(tableScroll, BorderLayout.CENTER);

        // Thanh công cụ thao tác file
        JPanel fileActionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        fileActionPanel.setBackground(Color.WHITE);
        btnUpload = new JButton("Upload File");
        btnDownload = new JButton("Download File");
        btnView = new JButton("Mở xem file");
        btnNewFolder = new JButton("Tạo thư mục");
        btnRename = new JButton("Đổi tên");
        btnDelete = new JButton("Xóa");
        btnClearLog = new JButton("Xóa nhật ký");

        fileActionPanel.add(btnUpload);
        fileActionPanel.add(btnDownload);
        fileActionPanel.add(btnView);
        fileActionPanel.add(btnNewFolder);
        fileActionPanel.add(btnRename);
        fileActionPanel.add(btnDelete);
        fileActionPanel.add(btnClearLog);
        centerPanel.add(fileActionPanel, BorderLayout.SOUTH);

        // Khung Nhật ký Log (Màu trắng, format dễ đọc)
        logArea = new JTextArea(8, 50);
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        logArea.setBackground(Color.WHITE);
        logArea.setForeground(new Color(35, 35, 35));

        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.getViewport().setBackground(Color.WHITE);
        logScroll.setBorder(BorderFactory.createTitledBorder(new LineBorder(new Color(225, 225, 225), 1), "Nhật ký truyền tin FTP"));

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, centerPanel, logScroll);
        splitPane.setResizeWeight(0.65);
        splitPane.setBackground(Color.WHITE);
        main.add(splitPane, BorderLayout.CENTER);

        // Sự kiện các nút
        btnLogout.addActionListener(e -> disconnect());
        btnUp.addActionListener(e -> cdup());
        btnRefresh.addActionListener(e -> refreshList());
        btnUpload.addActionListener(e -> upload());
        btnDownload.addActionListener(e -> download());
        btnView.addActionListener(e -> openSelectedFile());
        btnNewFolder.addActionListener(e -> makeDir());
        btnRename.addActionListener(e -> rename());
        btnDelete.addActionListener(e -> delete());
        btnClearLog.addActionListener(e -> logArea.setText(""));

        // Double click: nếu là Folder thì vào trong, nếu là File thì MỞ XEM NGAY
        fileTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && fileTable.getSelectedRow() != -1) {
                    int row = fileTable.getSelectedRow();
                    String type = (String) tableModel.getValueAt(row, 0);
                    String name = (String) tableModel.getValueAt(row, 1);
                    if ("[Folder]".equals(type)) {
                        cwd(name);
                    } else {
                        viewFile(name);
                    }
                }
            }
        });

        return main;
    }

    /**
     * Xử lý ĐĂNG NHẬP
     */
    private void performLogin() {
        String host = txtLoginHost.getText().trim();
        int port;
        try {
            port = Integer.parseInt(txtLoginPort.getText().trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Port không hợp lệ!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }
        String user = txtLoginUser.getText().trim();
        String pass = new String(txtLoginPass.getPassword());

        if (user.isEmpty() || pass.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập Tên tài khoản và Mật khẩu!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        btnDoLogin.setEnabled(false);
        new Thread(() -> {
            try {
                controlSocket = new Socket(host, port);
                reader = new BufferedReader(new InputStreamReader(controlSocket.getInputStream(), StandardCharsets.UTF_8));
                writer = new BufferedWriter(new OutputStreamWriter(controlSocket.getOutputStream(), StandardCharsets.UTF_8));

                String res = readResponse();
                if (!res.startsWith("220")) throw new IOException("Server từ chối: " + res);

                sendCommand("USER " + user);
                res = readResponse();
                if (!res.startsWith("331")) throw new IOException("Lỗi USER: " + res);

                sendCommand("PASS " + pass);
                res = readResponse();
                if (!res.startsWith("230")) {
                    throw new IOException("Đăng nhập thất bại: Sai tài khoản hoặc mật khẩu!");
                }

                sendCommand("TYPE I");
                readResponse();

                isConnected = true;
                loggedInUser = user;

                SwingUtilities.invokeLater(() -> {
                    lblUserInfo.setText("Đang đăng nhập với tài khoản: " + loggedInUser + " (" + host + ":" + port + ")");
                    cardLayout.show(cardsPanel, "MAIN");
                    btnDoLogin.setEnabled(true);
                });

                log("[HOẠT ĐỘNG]  Đăng nhập thành công với tài khoản '" + loggedInUser + "'");
                updatePwd();
                refreshList();
            } catch (Exception ex) {
                disconnect();
                SwingUtilities.invokeLater(() -> {
                    btnDoLogin.setEnabled(true);
                    JOptionPane.showMessageDialog(this, ex.getMessage(), "Lỗi đăng nhập", JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
    }

    /**
     * Xử lý ĐĂNG KÝ
     */
    private void performRegister() {
        String host = txtLoginHost.getText().trim();
        int port;
        try {
            port = Integer.parseInt(txtLoginPort.getText().trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Port không hợp lệ!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String user = txtLoginUser.getText().trim();
        String pass = new String(txtLoginPass.getPassword());

        if (user.isEmpty() || pass.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập Tên tài khoản và Mật khẩu để đăng ký!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        btnDoRegister.setEnabled(false);
        new Thread(() -> {
            Socket tempSocket = null;
            try {
                tempSocket = new Socket(host, port);
                BufferedReader r = new BufferedReader(new InputStreamReader(tempSocket.getInputStream(), StandardCharsets.UTF_8));
                BufferedWriter w = new BufferedWriter(new OutputStreamWriter(tempSocket.getOutputStream(), StandardCharsets.UTF_8));

                r.readLine();

                w.write("REGISTER " + user + " " + pass + "\r\n");
                w.flush();
                String res = r.readLine();
                tempSocket.close();

                if (res != null && res.startsWith("200")) {
                    SwingUtilities.invokeLater(() -> {
                        btnDoRegister.setEnabled(true);
                        int opt = JOptionPane.showConfirmDialog(this,
                                "Đăng ký thành công tài khoản '" + user + "'!\nBạn có muốn đăng nhập ngay bây giờ không?",
                                "Đăng ký thành công", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
                        if (opt == JOptionPane.YES_OPTION) {
                            performLogin();
                        }
                    });
                } else {
                    String msg = (res != null && res.contains("already exists")) ? "Tên tài khoản đã tồn tại!" : "Đăng ký thất bại: " + res;
                    SwingUtilities.invokeLater(() -> {
                        btnDoRegister.setEnabled(true);
                        JOptionPane.showMessageDialog(this, msg, "Lỗi đăng ký", JOptionPane.ERROR_MESSAGE);
                    });
                }
            } catch (Exception ex) {
                if (tempSocket != null) try { tempSocket.close(); } catch (IOException ignored) {}
                SwingUtilities.invokeLater(() -> {
                    btnDoRegister.setEnabled(true);
                    JOptionPane.showMessageDialog(this, "Không thể kết nối Server để đăng ký: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
    }

    private void disconnect() {
        if (isConnected) {
            try {
                sendCommand("QUIT");
                readResponse();
            } catch (Exception ignored) {}
        }
        isConnected = false;
        try {
            if (controlSocket != null && !controlSocket.isClosed()) controlSocket.close();
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> {
            cardLayout.show(cardsPanel, "AUTH");
            tableModel.setRowCount(0);
            lblCurrentPath.setText(" Thư mục: /");
            logArea.setText("");
        });
    }

    private Socket enterPassiveMode() throws IOException {
        sendCommand("PASV");
        String res = readResponse();
        if (!res.startsWith("227")) throw new IOException("Lỗi PASV: " + res);

        int start = res.indexOf('(');
        int end = res.indexOf(')');
        String[] parts = res.substring(start + 1, end).split(",");
        String dataIp = parts[0] + "." + parts[1] + "." + parts[2] + "." + parts[3];
        int dataPort = Integer.parseInt(parts[4].trim()) * 256 + Integer.parseInt(parts[5].trim());

        return new Socket(dataIp, dataPort);
    }

    private void refreshList() {
        if (!isConnected) return;
        new Thread(() -> {
            try {
                Socket dataSocket = enterPassiveMode();
                sendCommand("LIST");
                readResponse();

                BufferedReader dr = new BufferedReader(new InputStreamReader(dataSocket.getInputStream(), StandardCharsets.UTF_8));
                java.util.List<Object[]> rows = new java.util.ArrayList<>();

                String line;
                while ((line = dr.readLine()) != null) {
                    String[] tokens = line.split("\\s+", 9);
                    if (tokens.length >= 9) {
                        String perms = tokens[0];
                        String size = tokens[4];
                        String date = tokens[5] + " " + tokens[6] + " " + tokens[7];
                        String name = tokens[8];
                        String type = perms.startsWith("d") ? "[Folder]" : "[File]";
                        rows.add(new Object[]{type, name, size, date});
                    }
                }
                dataSocket.close();
                readResponse();

                SwingUtilities.invokeLater(() -> {
                    tableModel.setRowCount(0);
                    for (Object[] r : rows) tableModel.addRow(r);
                });
                log("[HOẠT ĐỘNG]  Đã làm mới danh sách thư mục (" + rows.size() + " mục)");
            } catch (Exception e) {
                log("[LỖI LIST]   " + e.getMessage());
            }
        }).start();
    }

    private void updatePwd() {
        try {
            sendCommand("PWD");
            String res = readResponse();
            if (res.startsWith("257")) {
                int s1 = res.indexOf('"');
                int s2 = res.lastIndexOf('"');
                if (s1 != -1 && s2 > s1) {
                    currentPath = res.substring(s1 + 1, s2);
                    SwingUtilities.invokeLater(() -> lblCurrentPath.setText(" Thư mục: " + currentPath));
                }
            }
        } catch (Exception ignored) {}
    }

    private void cwd(String folderName) {
        new Thread(() -> {
            try {
                sendCommand("CWD " + folderName);
                String res = readResponse();
                if (res.startsWith("250")) {
                    updatePwd();
                    refreshList();
                } else {
                    JOptionPane.showMessageDialog(this, "Không thể chuyển thư mục: " + res);
                }
            } catch (Exception e) {
                log("[LỖI CWD]    " + e.getMessage());
            }
        }).start();
    }

    private void cdup() {
        new Thread(() -> {
            try {
                sendCommand("CDUP");
                readResponse();
                updatePwd();
                refreshList();
            } catch (Exception e) {
                log("[LỖI CDUP]   " + e.getMessage());
            }
        }).start();
    }

    private void upload() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            new Thread(() -> {
                try {
                    log("[HOẠT ĐỘNG]  Bắt đầu upload: " + file.getName() + " (" + file.length() + " bytes)");
                    Socket dataSocket = enterPassiveMode();
                    sendCommand("STOR " + file.getName());
                    readResponse();

                    try (FileInputStream fis = new FileInputStream(file);
                         OutputStream dos = dataSocket.getOutputStream()) {
                        byte[] buf = new byte[4096];
                        int n;
                        while ((n = fis.read(buf)) != -1) dos.write(buf, 0, n);
                        dos.flush();
                    }
                    dataSocket.close();
                    readResponse();
                    log("[HOẠT ĐỘNG]  Upload thành công: " + file.getName());
                    refreshList();
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Upload thành công!"));
                } catch (Exception e) {
                    log("[LỖI STOR]   " + e.getMessage());
                }
            }).start();
        }
    }

    private void download() {
        int row = fileTable.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn 1 file để download!");
            return;
        }
        String type = (String) tableModel.getValueAt(row, 0);
        String name = (String) tableModel.getValueAt(row, 1);
        if ("[Folder]".equals(type)) {
            JOptionPane.showMessageDialog(this, "Chỉ hỗ trợ download File, không download Folder!");
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(name));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File saveFile = chooser.getSelectedFile();
            new Thread(() -> {
                try {
                    log("[HOẠT ĐỘNG]  Bắt đầu download: " + name);
                    Socket dataSocket = enterPassiveMode();
                    sendCommand("RETR " + name);
                    readResponse();

                    try (InputStream dis = dataSocket.getInputStream();
                         FileOutputStream fos = new FileOutputStream(saveFile)) {
                        byte[] buf = new byte[4096];
                        int n;
                        while ((n = dis.read(buf)) != -1) fos.write(buf, 0, n);
                        fos.flush();
                    }
                    dataSocket.close();
                    readResponse();
                    log("[HOẠT ĐỘNG]  Download thành công -> " + saveFile.getAbsolutePath());
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Download thành công!"));
                } catch (Exception e) {
                    log("[LỖI RETR]   " + e.getMessage());
                }
            }).start();
        }
    }

    /**
     * MỞ XEM FILE: Tải tạm file về thư mục Temp và mở ngay bằng ứng dụng của Windows
     */
    private void openSelectedFile() {
        int row = fileTable.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn 1 file để mở xem!");
            return;
        }
        String type = (String) tableModel.getValueAt(row, 0);
        String name = (String) tableModel.getValueAt(row, 1);
        if ("[Folder]".equals(type)) {
            cwd(name);
        } else {
            viewFile(name);
        }
    }

    private void viewFile(String name) {
        new Thread(() -> {
            try {
                log("[HOẠT ĐỘNG]  Đang tải file '" + name + "' để mở xem...");
                Socket dataSocket = enterPassiveMode();
                sendCommand("RETR " + name);
                String res = readResponse();
                if (!res.startsWith("150")) {
                    dataSocket.close();
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Không thể tải file: " + res));
                    return;
                }

                String prefix = "ftp_view_";
                String suffix = name.contains(".") ? name.substring(name.lastIndexOf('.')) : ".txt";
                File tempFile = File.createTempFile(prefix, suffix);
                tempFile.deleteOnExit();

                try (InputStream dis = dataSocket.getInputStream();
                     FileOutputStream fos = new FileOutputStream(tempFile)) {
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = dis.read(buf)) != -1) fos.write(buf, 0, n);
                    fos.flush();
                }
                dataSocket.close();
                readResponse();

                log("[HOẠT ĐỘNG]  Đã tải xong. Đang mở file '" + name + "'...");

                // Mở file bằng ứng dụng mặc định của Windows
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                    Desktop.getDesktop().open(tempFile);
                } else {
                    new ProcessBuilder("notepad.exe", tempFile.getAbsolutePath()).start();
                }
            } catch (Exception e) {
                log("[LỖI XEM FILE] " + e.getMessage());
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Lỗi mở file: " + e.getMessage()));
            }
        }).start();
    }

    private void makeDir() {
        String name = JOptionPane.showInputDialog(this, "Nhập tên thư mục mới:");
        if (name != null && !name.trim().isEmpty()) {
            new Thread(() -> {
                try {
                    sendCommand("MKD " + name.trim());
                    String res = readResponse();
                    if (res.startsWith("257")) refreshList();
                    else JOptionPane.showMessageDialog(this, "Tạo thư mục thất bại: " + res);
                } catch (Exception e) {
                    log("[LỖI MKD]    " + e.getMessage());
                }
            }).start();
        }
    }

    private void rename() {
        int row = fileTable.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một mục để đổi tên!");
            return;
        }
        String oldName = (String) tableModel.getValueAt(row, 1);
        String newName = JOptionPane.showInputDialog(this, "Nhập tên mới:", oldName);
        if (newName != null && !newName.trim().isEmpty() && !newName.equals(oldName)) {
            new Thread(() -> {
                try {
                    sendCommand("RNFR " + oldName);
                    String res = readResponse();
                    if (res.startsWith("350")) {
                        sendCommand("RNTO " + newName.trim());
                        res = readResponse();
                        if (res.startsWith("250")) refreshList();
                        else JOptionPane.showMessageDialog(this, "Đổi tên thất bại: " + res);
                    } else {
                        JOptionPane.showMessageDialog(this, "Không tìm thấy file: " + res);
                    }
                } catch (Exception e) {
                    log("[LỖI RENAME] " + e.getMessage());
                }
            }).start();
        }
    }

    private void delete() {
        int row = fileTable.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một mục để xóa!");
            return;
        }
        String type = (String) tableModel.getValueAt(row, 0);
        String name = (String) tableModel.getValueAt(row, 1);
        int confirm = JOptionPane.showConfirmDialog(this, "Bạn có chắc muốn xóa " + name + "?", "Xác nhận xóa", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            new Thread(() -> {
                try {
                    String cmd = "[Folder]".equals(type) ? "RMD " : "DELE ";
                    sendCommand(cmd + name);
                    String res = readResponse();
                    if (res.startsWith("250")) refreshList();
                    else JOptionPane.showMessageDialog(this, "Xóa thất bại: " + res);
                } catch (Exception e) {
                    log("[LỖI XÓA]   " + e.getMessage());
                }
            }).start();
        }
    }

    private void sendCommand(String cmd) throws IOException {
        writer.write(cmd + "\r\n");
        writer.flush();
        String display = cmd.toUpperCase().startsWith("PASS ") ? "PASS ******" : cmd;
        log("[CLIENT GỬI]  --> " + display);
    }

    private String readResponse() throws IOException {
        String line = reader.readLine();
        if (line == null) throw new IOException("Mất kết nối với Server!");
        log("[SERVER BÁO]  <-- " + line);
        return line;
    }

    private void log(String msg) {
        String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
        SwingUtilities.invokeLater(() -> {
            logArea.append("[" + time + "] " + msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new FtpClient().setVisible(true));
    }
}
