#!/bin/bash

echo "========================================"
echo "EasyCloudDisk Server Startup Script"
echo "========================================"
echo ""

# 检查Java
if ! command -v java &> /dev/null; then
    echo "[ERROR] Java is not installed or not in PATH"
    echo "Please install Java 21 or higher"
    exit 1
fi

echo "[INFO] Java version:"
java -version
echo ""

# 检查Maven
if ! command -v mvn &> /dev/null; then
    echo "[ERROR] Maven is not installed or not in PATH"
    echo "Please install Maven 3.6 or higher"
    exit 1
fi

echo "[INFO] Maven version:"
mvn -version
echo ""

# 检查环境变量
if [ -z "$AWS_ACCESS_KEY_ID" ]; then
    echo "[WARNING] AWS_ACCESS_KEY_ID not set"
    echo "Please set environment variables:"
    echo "  export AWS_ACCESS_KEY_ID=your-access-key-id"
    echo "  export AWS_SECRET_ACCESS_KEY=your-secret-access-key"
    echo "  export AWS_REGION=ap-northeast-1"
    echo "  export AWS_S3_BUCKET=clouddisk-test-1762861672"
    echo ""
fi

# 启动应用
echo "[INFO] Starting EasyCloudDisk Server..."
echo "[INFO] Server will be available at: http://localhost:8080"
echo "[INFO] Press Ctrl+C to stop the server"
echo ""

cd "$(dirname "$0")"
mvn spring-boot:run

