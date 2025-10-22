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
SPRING_PROFILES="prod"
SERVER_PORT="8080"

# ==================== 函数定义 ====================
log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1"
}

check_java() {
    if ! command -v java &> /dev/null; then
        log "错误: Java 未安装或不在 PATH 中"
        exit 1
    fi
    
    JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2)
    log "Java 版本: $JAVA_VERSION"
}

check_jar() {
    if [ ! -f "$JAR_FILE" ]; then
        log "错误: JAR 文件不存在: $JAR_FILE"
        exit 1
    fi
    
    JAR_SIZE=$(du -h "$JAR_FILE" | cut -f1)
    log "JAR 文件: $JAR_FILE (大小: $JAR_SIZE)"
}

stop_existing() {
    if [ -f "$PID_FILE" ]; then
        OLD_PID=$(cat "$PID_FILE")
        if ps -p "$OLD_PID" > /dev/null 2>&1; then
            log "停止现有进程 (PID: $OLD_PID)..."
            kill "$OLD_PID"
            
            # 等待进程停止
            for i in {1..30}; do
                if ! ps -p "$OLD_PID" > /dev/null 2>&1; then
                    log "进程已停止"
                    break
                fi
                sleep 1
            done
            
            # 强制杀死进程
            if ps -p "$OLD_PID" > /dev/null 2>&1; then
                log "强制停止进程..."
                kill -9 "$OLD_PID"
            fi
        fi
        rm -f "$PID_FILE"
    fi
    
    # 查找并停止可能的僵尸进程
    ZOMBIE_PIDS=$(pgrep -f "$JAR_FILE" || true)
    if [ -n "$ZOMBIE_PIDS" ]; then
        log "发现僵尸进程，正在清理..."
        echo "$ZOMBIE_PIDS" | xargs kill -9 2>/dev/null || true
    fi
}

start_app() {
    log "启动应用..."
    
    # 构建启动命令
    CMD="java $JVM_OPTS"
    CMD="$CMD -Dspring.profiles.active=$SPRING_PROFILES"
    CMD="$CMD -Dserver.port=$SERVER_PORT"
    CMD="$CMD -Dfile.encoding=UTF-8"
    CMD="$CMD -Djava.security.egd=file:/dev/./urandom"
    CMD="$CMD -jar $JAR_FILE"
    
    log "启动命令: $CMD"
    
    # 启动应用
    nohup $CMD > "$LOG_FILE" 2>&1 &
    APP_PID=$!
    
    # 保存 PID
    echo "$APP_PID" > "$PID_FILE"
    log "应用已启动 (PID: $APP_PID)"
    
    # 等待应用启动
    log "等待应用启动..."
    for i in {1..60}; do
        if ps -p "$APP_PID" > /dev/null 2>&1; then
            # 检查端口是否监听
            if netstat -tlnp 2>/dev/null | grep ":$SERVER_PORT " > /dev/null; then
                log "应用启动成功，监听端口: $SERVER_PORT"
                return 0
            fi
        else
            log "应用进程已退出，请检查日志: $LOG_FILE"
            return 1
        fi
        sleep 1
    done
    
    log "应用启动超时，请检查日志: $LOG_FILE"
    return 1
}

show_status() {
    if [ -f "$PID_FILE" ]; then
        PID=$(cat "$PID_FILE")
        if ps -p "$PID" > /dev/null 2>&1; then
            log "应用正在运行 (PID: $PID)"
            
            # 显示内存使用情况
            MEMORY=$(ps -p "$PID" -o rss= | awk '{print int($1/1024)"MB"}')
            log "内存使用: $MEMORY"
            
            # 显示端口监听情况
            if netstat -tlnp 2>/dev/null | grep ":$SERVER_PORT " > /dev/null; then
                log "端口监听: $SERVER_PORT"
            fi
        else
            log "应用未运行（PID 文件存在但进程不存在）"
            rm -f "$PID_FILE"
        fi
    else
        log "应用未运行"
    fi
}

show_logs() {
    if [ -f "$LOG_FILE" ]; then
        log "最近的应用日志："
        tail -n 20 "$LOG_FILE"
    else
        log "日志文件不存在: $LOG_FILE"
    fi
}

# ==================== 主流程 ====================
case "${1:-start}" in
    start)
        log "==================== 启动 $APP_NAME ===================="
        check_java
        check_jar
        stop_existing
        start_app
        show_status
        ;;
    stop)
        log "==================== 停止 $APP_NAME ===================="
        stop_existing
        ;;
    restart)
        log "==================== 重启 $APP_NAME ===================="
        check_java
        check_jar
        stop_existing
        start_app
        show_status
        ;;
    status)
        show_status
        ;;
    logs)
        show_logs
        ;;
    *)
        echo "用法: $0 {start|stop|restart|status|logs}"
        echo "  start   - 启动应用"
        echo "  stop    - 停止应用"
        echo "  restart - 重启应用"
        echo "  status  - 查看状态"
        echo "  logs    - 查看日志"
        exit 1
        ;;
esac