#!/bin/bash
# 运行所有测试的主脚本
# 依次执行所有测试脚本并生成测试报告

set +e

# 加载配置文件（如果存在）
TEST_DIR="$(cd "$(dirname "$0")" && pwd)"
if [ -f "$TEST_DIR/config.sh" ]; then
    source "$TEST_DIR/config.sh"
fi

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' 

# 配置
API_BASE_URL="${API_BASE_URL:-http://localhost:8080}"
REPORT_FILE="$TEST_DIR/test_report_$(date +%Y%m%d_%H%M%S).txt"

TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0
SKIPPED_TESTS=0

TEST_SCRIPTS=(
    "test_health.sh"
    "test_auth_register.sh"
    "test_auth_login.sh"
    "test_file_upload.sh"
    "test_file_list.sh"
    "test_file_download.sh"
    "test_file_delete.sh"
    "test_quick_upload.sh"
    "test_chunk_deduplication.sh"
    "test_resumable_upload.sh"
    "test_resumable_sessions.sh"
    "test_delta_sync.sh"
    "test_encryption.sh"
)

echo "=========================================="
echo "    EasyCloudDisk API 测试套件"
echo "=========================================="
echo "API 地址: $API_BASE_URL"
echo "测试目录: $TEST_DIR"
echo "报告文件: $REPORT_FILE"
echo "开始时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "=========================================="
echo ""

cat > "$REPORT_FILE" <<EOF
EasyCloudDisk API 测试报告
============================
测试时间: $(date '+%Y-%m-%d %H:%M:%S')
API 地址: $API_BASE_URL
============================

EOF

echo -e "${BLUE}检查服务器连接...${NC}"
if curl -s -f "$API_BASE_URL/api/health" > /dev/null 2>&1; then
    echo -e "${GREEN}✓ 服务器连接正常${NC}"
    echo ""
else
    echo -e "${RED}✗ 服务器连接失败: $API_BASE_URL${NC}"
    echo "请确保服务器正在运行"
    exit 1
fi

# 设置全局环境变量
export API_BASE_URL
export AUTH_TOKEN=""
export TEST_EMAIL=""
export TEST_PASSWORD=""
export UPLOADED_FILE_ID=""
export UPLOADED_FILE_NAME=""
export UPLOADED_FILE_PATH=""

# 创建临时文件用于传递环境变量
ENV_FILE=$(mktemp)
trap "rm -f $ENV_FILE" EXIT

# 执行每个测试
for TEST_SCRIPT in "${TEST_SCRIPTS[@]}"; do
    TEST_PATH="$TEST_DIR/$TEST_SCRIPT"
    
    if [ ! -f "$TEST_PATH" ]; then
        echo -e "${YELLOW}⚠ 跳过: $TEST_SCRIPT (文件不存在)${NC}"
        SKIPPED_TESTS=$((SKIPPED_TESTS + 1))
        echo "[SKIPPED] $TEST_SCRIPT - 文件不存在" >> "$REPORT_FILE"
        continue
    fi
    
    # 确保脚本可执行
    chmod +x "$TEST_PATH" 2>/dev/null || true
    
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    
    echo -e "${BLUE}----------------------------------------${NC}"
    echo -e "${BLUE}执行测试 [$TOTAL_TESTS/${#TEST_SCRIPTS[@]}]: $TEST_SCRIPT${NC}"
    echo -e "${BLUE}----------------------------------------${NC}"
    
    # 记录开始时间
    START_TIME=$(date +%s)
    
    # 导出当前环境变量到临时文件，供测试脚本使用
    {
        echo "export API_BASE_URL='$API_BASE_URL'"
        echo "export AUTH_TOKEN='$AUTH_TOKEN'"
        echo "export TEST_EMAIL='$TEST_EMAIL'"
        echo "export TEST_PASSWORD='$TEST_PASSWORD'"
        echo "export UPLOADED_FILE_ID='$UPLOADED_FILE_ID'"
        echo "export UPLOADED_FILE_NAME='$UPLOADED_FILE_NAME'"
        echo "export UPLOADED_FILE_PATH='$UPLOADED_FILE_PATH'"
        echo "export ENV_FILE='$ENV_FILE'"
    } > "$ENV_FILE"
    
    # 执行测试（捕获输出）
    # 注意：不使用 set -e，所以需要手动检查退出码
    # 先加载环境变量，然后执行测试
    # 测试脚本会将新导出的环境变量写入 ENV_FILE
    (source "$ENV_FILE" && bash "$TEST_PATH" >> "$REPORT_FILE" 2>&1 && source "$ENV_FILE" 2>/dev/null || true)
    EXIT_CODE=$?
    
    # 重新加载环境变量（从测试脚本可能写入的）
    if [ -f "$ENV_FILE" ]; then
        source "$ENV_FILE" 2>/dev/null || true
    fi
    
    END_TIME=$(date +%s)
    DURATION=$((END_TIME - START_TIME))
    
    if [ $EXIT_CODE -eq 0 ]; then
        echo -e "${GREEN}✓ 测试通过: $TEST_SCRIPT (耗时: ${DURATION}s)${NC}"
        PASSED_TESTS=$((PASSED_TESTS + 1))
        echo "[PASS] $TEST_SCRIPT (${DURATION}s)" >> "$REPORT_FILE"
    else
        echo -e "${RED}✗ 测试失败: $TEST_SCRIPT (退出码: $EXIT_CODE, 耗时: ${DURATION}s)${NC}"
        FAILED_TESTS=$((FAILED_TESTS + 1))
        echo "[FAIL] $TEST_SCRIPT (退出码: $EXIT_CODE, ${DURATION}s)" >> "$REPORT_FILE"
        
        # 如果关键测试失败，停止后续测试
        if [[ "$TEST_SCRIPT" == "test_health.sh" ]]; then
            echo -e "${RED}健康检查失败，停止后续测试${NC}"
            break
        fi
    fi
    
    echo ""
done

# 生成测试摘要
echo "=========================================="
echo "           测试执行摘要"
echo "=========================================="
echo "总测试数: $TOTAL_TESTS"
echo -e "${GREEN}通过: $PASSED_TESTS${NC}"
echo -e "${RED}失败: $FAILED_TESTS${NC}"
echo -e "${YELLOW}跳过: $SKIPPED_TESTS${NC}"
echo "=========================================="
echo ""

# 计算成功率
if [ $TOTAL_TESTS -gt 0 ]; then
    # 如果没有 bc 命令，使用 awk 或纯 bash 计算
    if command -v bc > /dev/null 2>&1; then
        SUCCESS_RATE=$(echo "scale=2; $PASSED_TESTS * 100 / $TOTAL_TESTS" | bc)
    else
        # 使用 awk 计算百分比
        SUCCESS_RATE=$(awk "BEGIN {printf \"%.2f\", ($PASSED_TESTS * 100) / $TOTAL_TESTS}")
    fi
    echo "成功率: ${SUCCESS_RATE}%"
    echo ""
else
    SUCCESS_RATE="0.00"
fi

# 将摘要写入报告文件
cat >> "$REPORT_FILE" <<EOF

============================
测试摘要
============================
总测试数: $TOTAL_TESTS
通过: $PASSED_TESTS
失败: $FAILED_TESTS
跳过: $SKIPPED_TESTS
成功率: ${SUCCESS_RATE}%
结束时间: $(date '+%Y-%m-%d %H:%M:%S')
============================
EOF

# 显示报告文件位置
echo -e "${BLUE}详细测试报告已保存至: $REPORT_FILE${NC}"
echo ""

# 根据测试结果返回退出码
if [ $FAILED_TESTS -eq 0 ]; then
    echo -e "${GREEN}所有测试通过！${NC}"
    exit 0
else
    echo -e "${RED}部分测试失败，请查看报告文件获取详细信息${NC}"
    exit 1
fi
