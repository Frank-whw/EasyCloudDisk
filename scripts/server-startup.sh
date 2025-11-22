#!/bin/bash

# EasyCloudDisk Server 启动脚本
# 用于在服务器上启动 Spring Boot 应用

set -e

# ==================== 配置区域 ====================
APP_NAME="clouddisk-server"
JAR_FILE="${APP_NAME}.jar"
PID_FILE="${APP_NAME}.pid"
LOG_FILE="app.log"

# JVM 参数
JVM_OPTS="-Xms512m -Xmx1024m"
JVM_OPTS="$JVM_OPTS -XX:+UseG1GC"
JVM_OPTS="$JVM_OPTS -XX:MaxGCPauseMillis=200"
JVM_OPTS="$JVM_OPTS -XX:+UnlockExperimentalVMOptions"
JVM_OPTS="$JVM_OPTS -XX:+UseContainerSupport"

# Spring Boot 配置
SERVER_PORT="8080"

# ==================== 函数定义 ====================
log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1"
}

ENV_FILE="/etc/default/clouddisk"
APP_ENV_FILE="$(dirname "$0")/.env"
if [ -f "$ENV_FILE" ]; then
    log "加载环境文件: $ENV_FILE"
    set -a
    . "$ENV_FILE"
    set +a
elif [ -f "$APP_ENV_FILE" ]; then
    log "加载环境文件: $APP_ENV_FILE"
    set -a
    . "$APP_ENV_FILE"
    set +a
else
    log "未检测到环境文件，使用当前会话变量"
fi

check_java() {
    if ! command -v java &> /dev/null; then
        log "错误: Java 未安装或不在 PATH 中"
        exit 1
    fi
    
    JAVA_VERSION=$(java -version 2>&1 | head -n 1)
    log "Java 版本: $JAVA_VERSION"
}

get_pid() {
    if [ -f "$PID_FILE" ]; then
        cat "$PID_FILE"
    else
        echo ""
    fi
}

is_running() {
    local pid=$(get_pid)
    if [ -n "$pid" ] && ps -p "$pid" > /dev/null 2>&1; then
        return 0
    else
        return 1
    fi
}

start() {
    log "========== 启动应用 =========="
    
    check_java
    
    if is_running; then
        log "应用已经在运行 (PID: $(get_pid))"
        return 0
    fi
    
    if [ ! -f "$JAR_FILE" ]; then
        log "错误: JAR 文件不存在: $JAR_FILE"
        exit 1
    fi
    
    log "启动应用: $JAR_FILE"
    log "JVM 参数: $JVM_OPTS"
    log "日志文件: $LOG_FILE"
    
    # 启动应用
    nohup java $JVM_OPTS -jar "$JAR_FILE" \
        --server.port=$SERVER_PORT \
        > "$LOG_FILE" 2>&1 &
    
    local pid=$!
    echo $pid > "$PID_FILE"
    
    log "应用已启动 (PID: $pid)"
    
    # 等待启动
    sleep 5
    
    if is_running; then
        log "✓ 应用启动成功"
        log "端口: $SERVER_PORT"
        log "PID: $(get_pid)"
        log "日志: tail -f $LOG_FILE"
    else
        log "✗ 应用启动失败"
        log "查看日志:"
        tail -n 50 "$LOG_FILE"
        exit 1
    fi
}

stop() {
    log "========== 停止应用 =========="
    
    if ! is_running; then
        log "应用未运行"
        rm -f "$PID_FILE"
        return 0
    fi
    
    local pid=$(get_pid)
    log "停止应用 (PID: $pid)"
    
    # 优雅停止
    kill $pid
    
    # 等待停止
    local count=0
    while is_running && [ $count -lt 30 ]; do
        sleep 1
        count=$((count + 1))
        log "等待应用停止... ($count/30)"
    done
    
    # 强制停止
    if is_running; then
        log "强制停止应用"
        kill -9 $pid
        sleep 2
    fi
    
    rm -f "$PID_FILE"
    
    if is_running; then
        log "✗ 应用停止失败"
        exit 1
    else
        log "✓ 应用已停止"
    fi
}

restart() {
    log "========== 重启应用 =========="
    stop
    sleep 2
    start
}

status() {
    log "========== 应用状态 =========="
    
    if is_running; then
        local pid=$(get_pid)
        log "✓ 应用正在运行"
        log "PID: $pid"
        log "端口: $SERVER_PORT"
        
        # 显示进程信息
        ps -p $pid -o pid,ppid,cmd,%mem,%cpu,etime
        
        # 检查端口
        if netstat -tuln | grep ":$SERVER_PORT " > /dev/null 2>&1; then
            log "✓ 端口 $SERVER_PORT 正在监听"
        else
            log "✗ 端口 $SERVER_PORT 未监听"
        fi
    else
        log "✗ 应用未运行"
        if [ -f "$PID_FILE" ]; then
            log "发现残留的 PID 文件，已清理"
            rm -f "$PID_FILE"
        fi
    fi
}

logs() {
    if [ ! -f "$LOG_FILE" ]; then
        log "日志文件不存在: $LOG_FILE"
        exit 1
    fi
    
    tail -f "$LOG_FILE"
}

show_help() {
    echo "EasyCloudDisk Server 启动脚本"
    echo ""
    echo "用法: $0 {start|stop|restart|status|logs}"
    echo ""
    echo "命令:"
    echo "  start   - 启动应用"
    echo "  stop    - 停止应用"
    echo "  restart - 重启应用"
    echo "  status  - 查看应用状态"
    echo "  logs    - 查看应用日志"
    echo ""
    echo "配置:"
    echo "  JAR 文件: $JAR_FILE"
    echo "  PID 文件: $PID_FILE"
    echo "  日志文件: $LOG_FILE"
    echo "  服务端口: $SERVER_PORT"
}

# ==================== 主流程 ====================
case "$1" in
    start)
        start
        ;;
    stop)
        stop
        ;;
    restart)
        restart
        ;;
    status)
        status
        ;;
    logs)
        logs
        ;;
    *)
        show_help
        exit 1
        ;;
esac

exit 0
