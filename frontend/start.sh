#!/usr/bin/env bash

echo "Starting EasyCloudDisk Frontend Server..."
echo ""
echo "Server will be available at: http://localhost:3000"
echo "Press Ctrl+C to stop the server"
echo ""

cd "$(dirname "$0")"

if command -v python3 >/dev/null 2>&1; then
    python3 -m http.server 3000
elif command -v python >/dev/null 2>&1; then
    python -m http.server 3000
else
    echo "Error: Python is not installed"
    echo "Please install Python 3 or use Node.js http-server instead"
    exit 1
fi

