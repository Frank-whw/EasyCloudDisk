@echo off
echo 启动 EasyCloudDisk 前端测试服务器...
echo.
echo 请确保后端服务器已启动在 http://localhost:8080
echo.
echo 前端将在 http://localhost:3000 启动
echo 分享测试页面: http://localhost:3000/test-share.html
echo.

cd /d "%~dp0"

REM 检查是否安装了 Python
python --version >nul 2>&1
if %errorlevel% == 0 (
    echo 使用 Python 启动服务器...
    python -m http.server 3000
) else (
    REM 检查是否安装了 Node.js
    node --version >nul 2>&1
    if %errorlevel% == 0 (
        echo 使用 Node.js 启动服务器...
        npx http-server -p 3000 -c-1
    ) else (
        echo 错误: 未找到 Python 或 Node.js
        echo 请安装 Python 或 Node.js 来运行本地服务器
        pause
    )
)
