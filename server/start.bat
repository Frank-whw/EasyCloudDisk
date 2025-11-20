@echo off
echo ========================================
echo EasyCloudDisk Server Startup Script
echo ========================================
echo.

REM 检查Java
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Java is not installed or not in PATH
    echo Please install Java 21 or higher
    pause
    exit /b 1
)

echo [INFO] Java version:
java -version
echo.

REM 检查Maven
mvn -version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Maven is not installed or not in PATH
    echo Please install Maven 3.6 or higher
    pause
    exit /b 1
)

echo [INFO] Maven version:
mvn -version
echo.

REM 设置环境变量（请根据实际情况修改）
if "%AWS_ACCESS_KEY_ID%"=="" (
    echo [WARNING] AWS_ACCESS_KEY_ID not set
    echo Please set environment variables:
    echo   set AWS_ACCESS_KEY_ID=your-access-key-id
    echo   set AWS_SECRET_ACCESS_KEY=your-secret-access-key
    echo   set AWS_REGION=ap-northeast-1
    echo   set AWS_S3_BUCKET=clouddisk-test-1762861672
    echo.
)

REM 启动应用
echo [INFO] Starting EasyCloudDisk Server...
echo [INFO] Server will be available at: http://localhost:8080
echo [INFO] Press Ctrl+C to stop the server
echo.

cd /d %~dp0
mvn spring-boot:run

pause

