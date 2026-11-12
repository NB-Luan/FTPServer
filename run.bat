@echo off
chcp 65001 > nul
title FTP Server - Lập trình mạng
echo ====================================================
echo        KHỞI ĐỘNG FTP SERVER (JAVA SWING)
echo ====================================================

set "JAVA_CMD=java"
set "JAVAC_CMD=javac"

where javac >nul 2>nul
if %errorlevel% neq 0 (
    if exist "D:\Software\App\IntelliJ IDEA 2026.2.3\jbr\bin\javac.exe" (
        set "JAVAC_CMD=D:\Software\App\IntelliJ IDEA 2026.2.3\jbr\bin\javac.exe"
        set "JAVA_CMD=D:\Software\App\IntelliJ IDEA 2026.2.3\jbr\bin\java.exe"
    ) else (
        echo [LỖI] Không tìm thấy Java SDK trong PATH.
        pause
        exit /b 1
    )
)

echo [*] Đang biên dịch src\*.java ...
if not exist "bin" mkdir "bin"
"%JAVAC_CMD%" -encoding UTF-8 -cp "lib/*" -d bin src\*.java
if %errorlevel% neq 0 (
    echo [LỖI] Biên dịch thất bại!
    pause
    exit /b 1
)

echo [*] Biên dịch thành công! Đang khởi chạy giao diện FTP Server...
start "" "%JAVA_CMD%" -cp "bin;lib/*" FtpServer
exit
