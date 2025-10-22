#!/bin/bash

# EasyCloudDisk 自动部署脚本
# 功能：Maven 打包 -> 传输到服务器 -> 远程部署

set -e  # 遇到错误立即退出

# ==================== 配置区域 ====================
# 项目配置
PROJECT_ROOT="/home/frank/learning/EasyCloudDisk"
SERVER_MODULE="$PROJECT_ROOT/server"

# 加载配置文件
CONFIG_FILE="$PROJECT_ROOT/scripts/config.sh"

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

check_prerequisites() {
    log "检查部署前置条件..."
    
    # 检查 Maven
    if ! command -v mvn &> /dev/null; then
        error_exit "Maven 未安装或不在 PATH 中"
    fi
    
    # 检查 SSH
    if ! command -v ssh &> /dev/null; then
        error_exit "SSH 客户端未安装"
    fi
    
    # 检查 SCP
    if ! command -v scp &> /dev/null; then
        error_exit "SCP 未安装"
    fi
    
    # 检查 server 模块目录
    if [ ! -d "$SERVER_MODULE" ]; then
        error_exit "server 模块目录不存在: $SERVER_MODULE"
    fi
    
    # 检查 pom.xml
    if [ ! -f "$SERVER_MODULE/pom.xml" ]; then
        error_exit "server 模块 pom.xml 不存在"
    fi
    
    log "前置条件检查通过"
}

build_jar() {
    log "开始 Maven 构建..."
    
    cd "$SERVER_MODULE"
    
    # 构建 Maven 命令
    if [[ "$BUILD_PROFILE" == "default" ]]; then
        mvn_cmd="mvn clean package -DskipTests"
    else
        mvn_cmd="mvn clean package -P$BUILD_PROFILE -DskipTests"
    fi
    
    # 清理并构建
    log "执行: $mvn_cmd"
    if $mvn_cmd >> "$LOG_FILE" 2>&1; then
        log "Maven 构建成功"
    else
        error_exit "Maven 构建失败，请检查日志: $LOG_FILE"
    fi
    
    # 检查 JAR 文件是否生成
    if [ ! -f "$LOCAL_JAR_PATH" ]; then
        # 尝试查找生成的 JAR 文件
        FOUND_JAR=$(find target -name "*.jar" -not -name "*-sources.jar" -not -name "*-javadoc.jar" | head -1)
        if [ -n "$FOUND_JAR" ]; then
            LOCAL_JAR_PATH="$SERVER_MODULE/$FOUND_JAR"
            log "找到构建的 JAR 文件: $LOCAL_JAR_PATH"
        else
            error_exit "构建完成但未找到 JAR 文件"
        fi
    fi
    
    JAR_SIZE=$(du -h "$LOCAL_JAR_PATH" | cut -f1)
    log "JAR 文件构建完成: $LOCAL_JAR_PATH (大小: $JAR_SIZE)"
    
    cd "$PROJECT_ROOT"
}

test_ssh_connection() {
    log "测试 SSH 连接..."
    
    # 构建 SSH 命令参数
    SSH_OPTS="-p $REMOTE_PORT -o ConnectTimeout=10 -o BatchMode=yes"
    if [ -n "$SSH_KEY_FILE" ] && [ -f "$SSH_KEY_FILE" ]; then
        SSH_OPTS="$SSH_OPTS -i $SSH_KEY_FILE"
        log "使用 SSH 私钥: $SSH_KEY_FILE"
    fi
    
    if ssh $SSH_OPTS "$REMOTE_USER@$REMOTE_HOST" "echo 'SSH 连接测试成功'" >> "$LOG_FILE" 2>&1; then
        log "SSH 连接测试成功"
        return 0
    else
        log "警告: SSH 连接失败，请检查："
        log "  1. 服务器地址: $REMOTE_HOST"
        log "  2. 用户名: $REMOTE_USER"
        log "  3. 端口: $REMOTE_PORT"
        log "  4. SSH 密钥文件: $SSH_KEY_FILE"
        log "  5. SSH 密钥权限（应为 600）"
        return 1
    fi
}

deploy_to_server() {
    log "开始部署到服务器..."
    
    # 测试连接
    if ! test_ssh_connection; then
        log "跳过服务器部署（SSH 连接失败）"
        return 1
    fi
    
    # 构建 SSH/SCP 命令参数
    SSH_OPTS="-p $REMOTE_PORT"
    SCP_OPTS="-P $REMOTE_PORT"
    if [ -n "$SSH_KEY_FILE" ] && [ -f "$SSH_KEY_FILE" ]; then
        SSH_OPTS="$SSH_OPTS -i $SSH_KEY_FILE"
        SCP_OPTS="$SCP_OPTS -i $SSH_KEY_FILE"
    fi
    
    # 创建远程目录
    log "创建远程目录结构..."
    ssh $SSH_OPTS "$REMOTE_USER@$REMOTE_HOST" "
        mkdir -p $REMOTE_APP_DIR $REMOTE_BACKUP_DIR
    " >> "$LOG_FILE" 2>&1
    
    # 备份现有 JAR（如果存在）
    log "备份现有应用..."
    ssh $SSH_OPTS "$REMOTE_USER@$REMOTE_HOST" "
        if [ -f $REMOTE_APP_DIR/${JAR_NAME}.jar ]; then
            cp $REMOTE_APP_DIR/${JAR_NAME}.jar $REMOTE_BACKUP_DIR/${JAR_NAME}_backup_${TIMESTAMP}.jar
            echo '备份完成: ${JAR_NAME}_backup_${TIMESTAMP}.jar'
        fi
    " >> "$LOG_FILE" 2>&1
    
    # 传输新 JAR 文件
    log "传输 JAR 文件到服务器..."
    if scp $SCP_OPTS "$LOCAL_JAR_PATH" "$REMOTE_USER@$REMOTE_HOST:$REMOTE_APP_DIR/${JAR_NAME}.jar" >> "$LOG_FILE" 2>&1; then
        log "文件传输成功"
    else
        error_exit "文件传输失败"
    fi
    
    # 传输启动脚本
    STARTUP_SCRIPT="$PROJECT_ROOT/scripts/server-startup.sh"
    if [ -f "$STARTUP_SCRIPT" ]; then
        log "传输启动脚本..."
        scp $SCP_OPTS "$STARTUP_SCRIPT" "$REMOTE_USER@$REMOTE_HOST:$REMOTE_APP_DIR/startup.sh" >> "$LOG_FILE" 2>&1
        ssh $SSH_OPTS "$REMOTE_USER@$REMOTE_HOST" "chmod +x $REMOTE_APP_DIR/startup.sh" >> "$LOG_FILE" 2>&1
    fi
    
    # 重启应用
    log "重启应用服务..."
    ssh $SSH_OPTS "$REMOTE_USER@$REMOTE_HOST" "
        # 停止现有进程
        pkill -f '${JAR_NAME}.jar' || true
        sleep 3
        
        # 启动新应用
        cd $REMOTE_APP_DIR
        if [ -f startup.sh ]; then
            nohup ./startup.sh > app.log 2>&1 &
        else
            nohup java -jar ${JAR_NAME}.jar > app.log 2>&1 &
        fi
        
        echo '应用启动命令已执行'
    " >> "$LOG_FILE" 2>&1
    
    log "服务器部署完成"
}

cleanup() {
    log "清理临时文件..."
    # 这里可以添加清理逻辑
}

show_summary() {
    log "==================== 部署摘要 ===================="
    log "项目: EasyCloudDisk Server"
    log "构建时间: $(date)"
    log "JAR 文件: $LOCAL_JAR_PATH"
    if [ -f "$LOCAL_JAR_PATH" ]; then
        log "JAR 大小: $(du -h "$LOCAL_JAR_PATH" | cut -f1)"
    fi
    log "目标服务器: $REMOTE_USER@$REMOTE_HOST:$REMOTE_PORT"
    log "部署目录: $REMOTE_APP_DIR"
    log "日志文件: $LOG_FILE"
    log "=================================================="
}

# ==================== 主流程 ====================
main() {
    log "==================== 开始自动部署 ===================="
    
    # 检查前置条件
    check_prerequisites
    
    # 构建 JAR
    build_jar
    
    # 部署到服务器
    deploy_to_server
    
    # 清理
    cleanup
    
    # 显示摘要
    show_summary
    
    log "==================== 自动部署完成 ===================="
}

# 捕获退出信号，确保清理
trap cleanup EXIT

# 执行主流程
main "$@"