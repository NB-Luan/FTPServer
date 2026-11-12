@echo off
setlocal
chcp 65001 >nul
title FTP Client

set "JAVA_CMD=java"
set "JAVAC_CMD=javac"

where javac >nul 2>nul
if %errorlevel% neq 0 (
    if exist "D:\Software\App\IntelliJ IDEA 2026.2.3\jbr\bin\javac.exe" (
        set "JAVAC_CMD=D:\Software\App\IntelliJ IDEA 2026.2.3\jbr\bin\javac.exe"
        set "JAVA_CMD=D:\Software\App\IntelliJ IDEA 2026.2.3\jbr\bin\java.exe"
    ) else (
        echo [ERROR] Khong tim thay JDK.
        pause
        exit /b 1
    )
)

if not exist "bin" mkdir "bin"
"%JAVAC_CMD%" -encoding UTF-8 -cp "lib/*" -d bin src\*.java
if %errorlevel% neq 0 (
    echo [ERROR] Bien dich that bai!
    pause
    exit /b 1
)

echo [*] Khoi chay FTP Client...
start "" "%JAVA_CMD%" -cp "bin;lib/*" FtpClient
exit /b 0