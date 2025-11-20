#!/bin/bash

# EasyCloudDisk 自动部署配置文件
# 请根据你的实际服务器环境修改以下配置

# ==================== 服务器连接配置 ====================
# AWS EC2 服务器地址
export REMOTE_HOST="54.95.61.230"

# SSH 用户名（AWS EC2 Ubuntu 默认用户）
export REMOTE_USER="ubuntu"

# SSH 端口
export REMOTE_PORT="22"

# SSH 私钥文件路径
export SSH_KEY_FILE="$HOME/.ssh/cloud2.pem"

# ==================== 服务器路径配置 ====================
# 应用部署目录
export REMOTE_APP_DIR="/home/ubuntu/clouddisk"

# 备份目录
export REMOTE_BACKUP_DIR="/home/ubuntu/clouddisk/backup"

# ==================== 应用配置 ====================
# JAR 文件名（不包含 .jar 扩展名）
export JAR_NAME="clouddisk-server-1.0.0"

# Maven 构建配置文件（如果没有特定的 profile，使用 default）
export BUILD_PROFILE="default"

# Spring Boot 配置文件
export SPRING_PROFILES="prod"

# 服务器端口
export SERVER_PORT="8080"

# ==================== JVM 配置 ====================
# JVM 内存配置
export JVM_XMS="512m"
export JVM_XMX="1024m"

# ==================== 使用说明 ====================
# 1. 修改上述配置项以匹配你的服务器环境
# 2. 确保服务器已配置 SSH 免密登录
# 3. 确保服务器已安装 Java 运行环境
# 4. 确保服务器目录具有写权限

# ==================== SSH 免密登录设置 ====================
# 如果还未设置 SSH 免密登录，请执行以下步骤：
#
# 1. 生成 SSH 密钥对（如果还没有）：
#    ssh-keygen -t rsa -b 4096 -C "your-email@example.com"
#
# 2. 将公钥复制到服务器：
#    ssh-copy-id -p $REMOTE_PORT $REMOTE_USER@$REMOTE_HOST
#
# 3. 测试连接：
#    ssh -p $REMOTE_PORT $REMOTE_USER@$REMOTE_HOST "echo 'SSH 连接成功'"

# ==================== 服务器环境准备 ====================
# 在服务器上执行以下命令准备环境：
#
# 1. 创建部署用户（如果需要）：
#    sudo useradd -m -s /bin/bash deploy
#
# 2. 创建应用目录：
#    sudo mkdir -p $REMOTE_APP_DIR $REMOTE_BACKUP_DIR
#    sudo chown -R root:root /root/clouddisk
#
# 3. 安装 Java（如果未安装）：
#    # Ubuntu/Debian:
#    sudo apt update && sudo apt install openjdk-17-jre-headless
#    # CentOS/RHEL:
#    sudo yum install java-17-openjdk-headless

echo "配置文件已加载"
echo "服务器: $REMOTE_USER@$REMOTE_HOST:$REMOTE_PORT"
echo "部署目录: $REMOTE_APP_DIR"
