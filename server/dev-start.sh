#!/bin/bash

# 本地开发环境启动脚本
# 用于在WSL中启动后端服务进行调试

echo "========================================"
echo "EasyCloudDisk 本地开发环境启动"
echo "========================================"
echo ""

# 进入server目录
cd "$(dirname "$0")"

# 设置环境变量（请根据实际情况修改）
export AWS_ACCESS_KEY_ID="${AWS_ACCESS_KEY_ID:-AKIARCSPQ2MSDC2UES4A}"
export AWS_SECRET_ACCESS_KEY="${AWS_SECRET_ACCESS_KEY:-your-secret-key}"
export AWS_REGION="${AWS_REGION:-ap-northeast-1}"
export AWS_S3_BUCKET="${AWS_S3_BUCKET:-clouddisk-test-1762861672}"

# 检查环境变量
if [ "$AWS_SECRET_ACCESS_KEY" = "your-secret-key" ]; then
    echo "[WARNING] 请设置 AWS_SECRET_ACCESS_KEY 环境变量"
    echo "  方法1: export AWS_SECRET_ACCESS_KEY='your-secret-key'"
    echo "  方法2: 从EC2获取: ssh myec2 'cat ~/.aws/credentials'"
    echo ""
fi

echo "[INFO] 环境变量:"
echo "  AWS_ACCESS_KEY_ID: ${AWS_ACCESS_KEY_ID:0:10}..."
echo "  AWS_REGION: $AWS_REGION"
echo "  AWS_S3_BUCKET: $AWS_S3_BUCKET"
echo ""

# 检查Java
if ! command -v java &> /dev/null; then
    echo "[ERROR] Java未安装，请先安装Java 21+"
    exit 1
fi

# 检查Maven
if ! command -v mvn &> /dev/null; then
    echo "[ERROR] Maven未安装，请先安装Maven"
    exit 1
fi

echo "[INFO] 启动本地开发服务器..."
echo "[INFO] 服务地址: http://localhost:8080"
echo "[INFO] 健康检查: http://localhost:8080/health"
echo "[INFO] 按 Ctrl+C 停止服务"
echo ""

# 启动服务
mvn spring-boot:run

