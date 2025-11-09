#!/bin/bash

# SSH 免密登录设置脚本
# 帮助快速配置到服务器的免密登录

set -e

# 加载配置
PROJECT_ROOT="/home/frank/learning/EasyCloudDisk"
CONFIG_FILE="$PROJECT_ROOT/scripts/config.sh"

if [ -f "$CONFIG_FILE" ]; then
    source "$CONFIG_FILE"
else
    echo "错误: 配置文件不存在: $CONFIG_FILE"
    echo "请先配置 scripts/config.sh"
    exit 1
fi

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1"
}

check_ssh_key() {
    # 如果配置了指定的私钥文件，检查它是否存在
    if [ -n "$SSH_KEY_FILE" ]; then
        if [ -f "$SSH_KEY_FILE" ]; then
            log "使用指定的 SSH 私钥: $SSH_KEY_FILE"
            
            # 检查私钥权限
            CURRENT_PERM=$(stat -c "%a" "$SSH_KEY_FILE")
            if [ "$CURRENT_PERM" != "600" ]; then
                log "修正 SSH 私钥权限..."
                chmod 600 "$SSH_KEY_FILE"
                log "SSH 私钥权限已设置为 600"
            fi
            
            # 检查对应的公钥是否存在
            PUB_KEY_FILE="${SSH_KEY_FILE}.pub"
            if [ ! -f "$PUB_KEY_FILE" ]; then
                log "警告: 公钥文件不存在: $PUB_KEY_FILE"
                log "如需生成公钥，请运行: ssh-keygen -y -f $SSH_KEY_FILE > $PUB_KEY_FILE"
            fi
            
            return 0
        else
            log "错误: 指定的 SSH 私钥文件不存在: $SSH_KEY_FILE"
            exit 1
        fi
    fi
    
    # 默认行为：检查或生成标准 SSH 密钥
    DEFAULT_SSH_KEY="$HOME/.ssh/id_rsa"
    
    if [ ! -f "$DEFAULT_SSH_KEY" ]; then
        log "SSH 密钥不存在，正在生成..."
        
        read -p "请输入你的邮箱地址: " EMAIL
        if [ -z "$EMAIL" ]; then
            EMAIL="deploy@easyclouddisk.local"
        fi
        
        ssh-keygen -t rsa -b 4096 -C "$EMAIL" -f "$DEFAULT_SSH_KEY" -N ""
        log "SSH 密钥已生成: $DEFAULT_SSH_KEY"
    else
        log "SSH 密钥已存在: $DEFAULT_SSH_KEY"
    fi
}

copy_ssh_key() {
    log "复制 SSH 公钥到服务器..."
    log "服务器: $REMOTE_USER@$REMOTE_HOST:$REMOTE_PORT"
    
    # 如果使用指定的私钥文件，需要特殊处理
    if [ -n "$SSH_KEY_FILE" ] && [ -f "$SSH_KEY_FILE" ]; then
        PUB_KEY_FILE="${SSH_KEY_FILE}.pub"
        if [ -f "$PUB_KEY_FILE" ]; then
            log "使用指定的公钥文件: $PUB_KEY_FILE"
            if ssh-copy-id -i "$PUB_KEY_FILE" -p "$REMOTE_PORT" "$REMOTE_USER@$REMOTE_HOST"; then
                log "SSH 公钥复制成功"
            else
                log "SSH 公钥复制失败，请检查："
                log "  1. 服务器地址是否正确"
                log "  2. 用户名和密码是否正确"
                log "  3. 网络连接是否正常"
                log "  4. 公钥文件是否存在: $PUB_KEY_FILE"
                return 1
            fi
        else
            log "错误: 公钥文件不存在: $PUB_KEY_FILE"
            log "请先生成公钥: ssh-keygen -y -f $SSH_KEY_FILE > $PUB_KEY_FILE"
            return 1
        fi
    else
        # 使用默认的 ssh-copy-id 行为
        if ssh-copy-id -p "$REMOTE_PORT" "$REMOTE_USER@$REMOTE_HOST"; then
            log "SSH 公钥复制成功"
        else
            log "SSH 公钥复制失败，请检查："
            log "  1. 服务器地址是否正确"
            log "  2. 用户名和密码是否正确"
            log "  3. 网络连接是否正常"
            return 1
        fi
    fi
}

test_ssh_connection() {
    log "测试 SSH 免密登录..."
    
    # 构建 SSH 命令参数
    SSH_OPTS="-p $REMOTE_PORT -o ConnectTimeout=10 -o BatchMode=yes"
    if [ -n "$SSH_KEY_FILE" ] && [ -f "$SSH_KEY_FILE" ]; then
        SSH_OPTS="$SSH_OPTS -i $SSH_KEY_FILE"
    fi
    
    if ssh $SSH_OPTS "$REMOTE_USER@$REMOTE_HOST" "echo 'SSH 免密登录测试成功'"; then
        log "✅ SSH 免密登录配置成功！"
        return 0
    else
        log "❌ SSH 免密登录测试失败"
        return 1
    fi
}

setup_server_environment() {
    log "设置服务器环境..."
    
    # 构建 SSH 命令参数
    SSH_OPTS="-p $REMOTE_PORT"
    if [ -n "$SSH_KEY_FILE" ] && [ -f "$SSH_KEY_FILE" ]; then
        SSH_OPTS="$SSH_OPTS -i $SSH_KEY_FILE"
    fi
    
    # 创建应用目录
    ssh $SSH_OPTS "$REMOTE_USER@$REMOTE_HOST" "
        mkdir -p $REMOTE_APP_DIR $REMOTE_BACKUP_DIR
        echo '应用目录已创建:'
        echo '  - $REMOTE_APP_DIR'
        echo '  - $REMOTE_BACKUP_DIR'
    "
    
    # 检查 Java 环境
    log "检查服务器 Java 环境..."
    ssh $SSH_OPTS "$REMOTE_USER@$REMOTE_HOST" "
        if command -v java &> /dev/null; then
            echo 'Java 已安装:'
            java -version
        else
            echo '警告: Java 未安装，请手动安装 Java 17 或更高版本'
            echo '  Ubuntu/Debian: sudo apt update && sudo apt install openjdk-17-jre-headless'
            echo '  CentOS/RHEL: sudo yum install java-17-openjdk-headless'
        fi
    "
    
    log "服务器环境设置完成"
}

show_summary() {
    log "==================== 配置摘要 ===================="
    log "服务器: $REMOTE_USER@$REMOTE_HOST:$REMOTE_PORT"
    log "部署目录: $REMOTE_APP_DIR"
    log "备份目录: $REMOTE_BACKUP_DIR"
    log "SSH 密钥: $HOME/.ssh/id_rsa"
    log "=================================================="
}

main() {
    log "==================== SSH 免密登录设置 ===================="
    
    # 检查并生成 SSH 密钥
    check_ssh_key
    
    # 复制公钥到服务器
    copy_ssh_key
    
    # 测试连接
    if test_ssh_connection; then
        # 设置服务器环境
        setup_server_environment
        
        # 显示摘要
        show_summary
        
        log "✅ SSH 免密登录设置完成！"
        log "现在可以运行自动部署脚本了："
        log "  ./scripts/auto-deploy.sh"
    else
        log "❌ SSH 免密登录设置失败，请检查配置"
        exit 1
    fi
}

# 显示帮助信息
if [ "$1" = "-h" ] || [ "$1" = "--help" ]; then
    echo "SSH 免密登录设置脚本"
    echo ""
    echo "用法: $0"
    echo ""
    echo "功能:"
    echo "  1. 检查并生成 SSH 密钥对"
    echo "  2. 将公钥复制到服务器"
    echo "  3. 测试免密登录"
    echo "  4. 设置服务器环境"
    echo ""
    echo "前置条件:"
    echo "  1. 已配置 scripts/config.sh"
    echo "  2. 服务器允许 SSH 连接"
    echo "  3. 知道服务器用户密码（首次设置时需要）"
    exit 0
fi

main "$@"