@echo off
echo Starting EasyCloudDisk Frontend Server...
echo.
echo Server will be available at: http://localhost:3000
echo Press Ctrl+C to stop the server
echo.

cd /d %~dp0
python -m http.server 3000

