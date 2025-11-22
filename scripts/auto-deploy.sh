#!/bin/bash

# EasyCloudDisk 自动部署脚本
# 功能：Maven 打包 -> 传输到服务器 -> 远程部署

set -e  # 遇到错误立即退出

# ==================== 配置区域 ====================
# 项目配置（自动检测）
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
SERVER_MODULE="$PROJECT_ROOT/server"

# 加载配置文件
CONFIG_FILE="$SCRIPT_DIR/config.sh"

# ==================== 函数定义 ====================
log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" | tee -a "$LOG_FILE"
}

error_exit() {
    log "错误: $1"
    exit 1
}

# 加载配置
if [ -f "$CONFIG_FILE" ]; then
    source "$CONFIG_FILE"
    echo "配置文件已加载"
    echo "服务器: $REMOTE_USER@$REMOTE_HOST:$REMOTE_PORT"
    echo "部署目录: $REMOTE_APP_DIR"
else
    # 默认配置 (请根据实际情况修改)
    JAR_NAME="easyclouddisk-server"
    BUILD_PROFILE="prod"
    REMOTE_HOST="your-server.com"
    REMOTE_USER="deploy"
    REMOTE_PORT="22"
    REMOTE_APP_DIR="/home/ubuntu/clouddisk"
    REMOTE_BACKUP_DIR="/home/ubuntu/clouddisk/backup"
    echo "警告: 配置文件不存在，使用默认配置"
fi

# 本地配置
LOCAL_JAR_PATH="$SERVER_MODULE/target/${JAR_NAME}.jar"
LOG_FILE="$PROJECT_ROOT/logs/deploy.log"
TIMESTAMP=$(date '+%Y%m%d_%H%M%S')

# 确保日志目录存在
mkdir -p "$(dirname "$LOG_FILE")"

# SSH 配置
SSH_OPTS="-p $REMOTE_PORT -o ConnectTimeout=10 -o BatchMode=yes"
if [ -n "$SSH_KEY_FILE" ] && [ -f "$SSH_KEY_FILE" ]; then
    SSH_OPTS="$SSH_OPTS -i $SSH_KEY_FILE"
fi

# ==================== 前置检查 ====================
check_prerequisites() {
    log "========== 前置条件检查 =========="
    
    # 检查 Maven
    if ! command -v mvn &> /dev/null; then
        error_exit "Maven 未安装，请先安装 Maven"
    fi
    log "✓ Maven 已安装: $(mvn -version | head -1)"
    
    # 检查 SSH 连接
    log "检查服务器连接..."
    if ! ssh $SSH_OPTS "$REMOTE_USER@$REMOTE_HOST" "echo '连接测试成功'" &> /dev/null; then
        error_exit "无法连接到服务器 $REMOTE_USER@$REMOTE_HOST:$REMOTE_PORT
请检查:
  1. 服务器地址是否正确
  2. SSH 密钥是否配置正确
  3. 网络连接是否正常
  
如果尚未配置 SSH 免密登录，请运行: ./scripts/setup-ssh.sh"
    fi
    log "✓ 服务器连接正常"
    
    # 检查服务器目录
    log "检查服务器目录..."
    ssh $SSH_OPTS "$REMOTE_USER@$REMOTE_HOST" "mkdir -p $REMOTE_APP_DIR $REMOTE_BACKUP_DIR"
    log "✓ 服务器目录已准备"
    
    log "前置条件检查通过"
}

# ==================== Maven 构建 ====================
build_jar() {
    log "========== Maven 构建 =========="
    
    cd "$SERVER_MODULE" || error_exit "无法进入 server 目录"
    
    log "开始构建 JAR 包..."
    log "构建配置: $BUILD_PROFILE"
    
    # 构建命令
    if [[ "$BUILD_PROFILE" == "default" ]]; then
        mvn_cmd="mvn clean package -Dmaven.test.skip=true"
    else
        mvn_cmd="mvn clean package -P$BUILD_PROFILE -Dmaven.test.skip=true"
    fi
    
    log "执行命令: $mvn_cmd"
    
    if $mvn_cmd; then
        log "✓ Maven 构建成功"
    else
        error_exit "Maven 构建失败"
    fi
    
    # 检查 JAR 文件
    if [ ! -f "$LOCAL_JAR_PATH" ]; then
        error_exit "JAR 文件不存在: $LOCAL_JAR_PATH"
    fi
    
    JAR_SIZE=$(du -h "$LOCAL_JAR_PATH" | cut -f1)
    log "✓ JAR 文件已生成: $LOCAL_JAR_PATH ($JAR_SIZE)"
    
    cd - > /dev/null
}

# ==================== 文件传输 ====================
transfer_jar() {
    log "========== 传输 JAR 文件 =========="
    
    log "源文件: $LOCAL_JAR_PATH"
    log "目标服务器: $REMOTE_USER@$REMOTE_HOST:$REMOTE_PORT"
    log "目标路径: $REMOTE_APP_DIR/${JAR_NAME}.jar"
    
    # 使用 scp 传输
    SCP_OPTS="-P $REMOTE_PORT"
    if [ -n "$SSH_KEY_FILE" ] && [ -f "$SSH_KEY_FILE" ]; then
        SCP_OPTS="$SCP_OPTS -i $SSH_KEY_FILE"
    fi
    
    if scp $SCP_OPTS "$LOCAL_JAR_PATH" "$REMOTE_USER@$REMOTE_HOST:$REMOTE_APP_DIR/${JAR_NAME}.jar"; then
        log "✓ JAR 文件传输成功"
    else
        error_exit "JAR 文件传输失败"
    fi
}

# ==================== 远程部署 ====================
deploy_remote() {
    log "========== 远程部署 =========="
    
    log "部署到: $REMOTE_USER@$REMOTE_HOST:$REMOTE_APP_DIR"
    
    # 远程部署脚本
    ssh $SSH_OPTS "$REMOTE_USER@$REMOTE_HOST" << EOF
        set -e
        
        cd $REMOTE_APP_DIR
        
        # 1. 备份旧版本
        if [ -f "${JAR_NAME}-running.jar" ]; then
            echo "备份旧版本..."
            mv "${JAR_NAME}-running.jar" "$REMOTE_BACKUP_DIR/${JAR_NAME}-${TIMESTAMP}.jar"
            echo "✓ 旧版本已备份"
        fi
        
        # 2. 停止旧应用
        echo "停止旧应用..."
        if [ -f "${JAR_NAME}.pid" ]; then
            OLD_PID=\$(cat "${JAR_NAME}.pid")
            if ps -p \$OLD_PID > /dev/null 2>&1; then
                kill \$OLD_PID
                sleep 3
                if ps -p \$OLD_PID > /dev/null 2>&1; then
                    echo "强制停止应用..."
                    kill -9 \$OLD_PID
                fi
                echo "✓ 旧应用已停止"
            fi
            rm -f "${JAR_NAME}.pid"
        else
            # 尝试通过名称查找并停止
            pkill -f "${JAR_NAME}.jar" || true
            echo "✓ 已尝试停止同名应用"
        fi
        
        # 3. 复制新版本
        echo "部署新版本..."
        cp "${JAR_NAME}.jar" "${JAR_NAME}-running.jar"
        echo "✓ 新版本已部署"
        
        # 4. 启动新应用
        echo "启动新应用..."
        nohup java -jar "${JAR_NAME}-running.jar" > app.log 2>&1 &
        NEW_PID=\$!
        echo \$NEW_PID > "${JAR_NAME}.pid"
        echo "✓ 新应用已启动 (PID: \$NEW_PID)"
        
        # 5. 验证启动
        sleep 5
        if ps -p \$NEW_PID > /dev/null 2>&1; then
            echo "✓ 应用启动成功"
        else
            echo "✗ 应用启动失败，请检查日志"
            tail -n 50 app.log
            exit 1
        fi
EOF
    
    if [ $? -eq 0 ]; then
        log "✓ 远程部署完成"
    else
        error_exit "远程部署失败"
    fi
}

# ==================== 健康检查 ====================
health_check() {
    log "========== 健康检查 =========="
    
    log "等待应用启动..."
    sleep 10
    
    # 检查进程
    log "检查应用进程..."
    if ssh $SSH_OPTS "$REMOTE_USER@$REMOTE_HOST" "[ -f $REMOTE_APP_DIR/${JAR_NAME}.pid ] && ps -p \$(cat $REMOTE_APP_DIR/${JAR_NAME}.pid) > /dev/null 2>&1"; then
        log "✓ 应用进程运行正常"
    else
        log "✗ 应用进程未运行"
        log "查看日志:"
        ssh $SSH_OPTS "$REMOTE_USER@$REMOTE_HOST" "tail -n 50 $REMOTE_APP_DIR/app.log"
        error_exit "应用未正常启动"
    fi
    
    # 检查端口
    log "检查应用端口..."
    if ssh $SSH_OPTS "$REMOTE_USER@$REMOTE_HOST" "netstat -tuln | grep :$SERVER_PORT > /dev/null 2>&1"; then
        log "✓ 应用端口 $SERVER_PORT 正在监听"
    else
        log "✗ 应用端口 $SERVER_PORT 未监听"
    fi
    
    # HTTP 健康检查
    log "执行 HTTP 健康检查..."
    MAX_RETRIES=10
    RETRY_COUNT=0
    
    while [ $RETRY_COUNT -lt $MAX_RETRIES ]; do
        if ssh $SSH_OPTS "$REMOTE_USER@$REMOTE_HOST" "curl -s http://localhost:$SERVER_PORT/actuator/health > /dev/null 2>&1"; then
            log "✓ HTTP 健康检查通过"
            return 0
        fi
        
        RETRY_COUNT=$((RETRY_COUNT + 1))
        log "健康检查失败，重试 $RETRY_COUNT/$MAX_RETRIES..."
        sleep 3
    done
    
    log "✗ HTTP 健康检查失败"
    log "查看日志:"
    ssh $SSH_OPTS "$REMOTE_USER@$REMOTE_HOST" "tail -n 100 $REMOTE_APP_DIR/app.log"
}

# ==================== 主流程 ====================
main() {
    log "==================== 开始部署 ===================="
    log "时间: $(date '+%Y-%m-%d %H:%M:%S')"
    log "服务器: $REMOTE_USER@$REMOTE_HOST:$REMOTE_PORT"
    log "=================================================="
    
    # 执行部署流程
    check_prerequisites
    build_jar
    transfer_jar
    deploy_remote
    health_check
    
    log "==================== 部署完成 ===================="
    log "部署成功！"
    log "服务器: $REMOTE_USER@$REMOTE_HOST:$REMOTE_PORT"
    log "应用端口: $SERVER_PORT"
    log "查看日志: ssh $SSH_OPTS $REMOTE_USER@$REMOTE_HOST 'tail -f $REMOTE_APP_DIR/app.log'"
    log "=================================================="
}

# 显示帮助信息
if [ "$1" = "-h" ] || [ "$1" = "--help" ]; then
    echo "EasyCloudDisk 自动部署脚本"
    echo ""
    echo "用法: $0"
    echo ""
    echo "功能:"
    echo "  1. Maven 构建 JAR 包"
    echo "  2. 传输到服务器"
    echo "  3. 停止旧应用"
    echo "  4. 启动新应用"
    echo "  5. 健康检查"
    echo ""
    echo "前置条件:"
    echo "  1. 已配置 scripts/config.sh"
    echo "  2. 已配置 SSH 免密登录"
    echo "  3. 服务器已安装 Java 运行环境"
    exit 0
fi

# 执行主流程
main "$@"
