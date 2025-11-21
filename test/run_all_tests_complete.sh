#!/bin/bash



# 运行所有单元测试和集成测试
# 包括 Java 单元测试 + Bash 集成测试

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

echo "=========================================="
echo "EasyCloudDisk 完整测试套件"
echo "=========================================="
echo ""

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 计数器
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

# 测试结果数组
declare -a TEST_RESULTS

# ==========================================
# 1. Java 单元测试
# ==========================================
echo "=========================================="
echo "第一部分: Java 单元测试"
echo "=========================================="
echo ""

cd "$PROJECT_ROOT/server" || exit 1

echo "运行 Maven 测试..."
echo ""

if mvn test -q; then
    echo -e "${GREEN}✓ Java 单元测试全部通过${NC}"
    JAVA_TESTS_PASSED=true
    TEST_RESULTS+=("Java单元测试|PASSED")
else
    echo -e "${RED}✗ Java 单元测试失败${NC}"
    JAVA_TESTS_PASSED=false
    TEST_RESULTS+=("Java单元测试|FAILED")
fi

echo ""
echo "查看详细测试报告:"
echo "  cd server && mvn surefire-report:report"
echo "  报告位置: server/target/surefire-reports/"
echo ""

# ==========================================
# 2. Bash 集成测试
# ==========================================
echo "=========================================="
echo "第二部分: API 集成测试"
echo "=========================================="
echo ""

# 检查服务器是否运行
API_BASE_URL="${API_BASE_URL:-http://localhost:8080}"
echo "检查服务器连接: $API_BASE_URL/health"

if ! curl -s -f "$API_BASE_URL/health" > /dev/null 2>&1; then
    echo -e "${RED}✗ 服务器未运行，请先启动服务器${NC}"
    echo ""
    echo "启动命令:"
    echo "  cd server && mvn spring-boot:run"
    echo ""
    exit 1
fi

echo -e "${GREEN}✓ 服务器运行正常${NC}"
echo ""

# 进入测试目录
cd "$SCRIPT_DIR" || exit 1

# 定义测试脚本列表（按执行顺序）
TESTS=(
    "test_health.sh|健康检查"
    "test_auth_register.sh|用户注册"
    "test_auth_login.sh|用户登录"
    "test_file_upload.sh|文件上传"
    "test_file_list.sh|文件列表"
    "test_file_download.sh|文件下载"
    "test_file_delete.sh|文件删除"
    "test_quick_upload.sh|文件级去重（秒传）"
    "test_chunk_deduplication.sh|块级去重"
    "test_resumable_upload.sh|断点续传"
    "test_resumable_sessions.sh|断点续传会话"
    "test_delta_sync.sh|差分同步"
    "test_encryption.sh|数据加密"
    "test_file_versions.sh|文件版本控制"
    "test_collaboration_share.sh|文件共享与协同"
)

echo "开始运行 API 集成测试..."
echo ""

# 运行每个测试
for test_entry in "${TESTS[@]}"; do
    IFS='|' read -r test_script test_name <<< "$test_entry"
    
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    
    echo "----------------------------------------------"
    echo "[$TOTAL_TESTS/${#TESTS[@]}] 测试: $test_name"
    echo "----------------------------------------------"
    
    if [ -f "$test_script" ]; then
        # 运行测试
        if bash "$test_script"; then
            echo -e "${GREEN}✓ $test_name - 通过${NC}"
            PASSED_TESTS=$((PASSED_TESTS + 1))
            TEST_RESULTS+=("$test_name|PASSED")
        else
            echo -e "${RED}✗ $test_name - 失败${NC}"
            FAILED_TESTS=$((FAILED_TESTS + 1))
            TEST_RESULTS+=("$test_name|FAILED")
        fi
    else
        echo -e "${YELLOW}⚠ 测试脚本不存在: $test_script${NC}"
        FAILED_TESTS=$((FAILED_TESTS + 1))
        TEST_RESULTS+=("$test_name|SKIPPED")
    fi
    
    echo ""
done

# ==========================================
# 3. 生成测试报告
# ==========================================
echo ""
echo "=========================================="
echo "测试报告"
echo "=========================================="
echo ""

# Java 单元测试结果
echo "Java 单元测试:"
if [ "$JAVA_TESTS_PASSED" = true ]; then
    echo -e "  ${GREEN}✓ 通过${NC}"
else
    echo -e "  ${RED}✗ 失败${NC}"
fi
echo ""

# API 集成测试结果
echo "API 集成测试结果:"
echo "  总计: $TOTAL_TESTS"
echo -e "  ${GREEN}通过: $PASSED_TESTS${NC}"
if [ $FAILED_TESTS -gt 0 ]; then
    echo -e "  ${RED}失败: $FAILED_TESTS${NC}"
else
    echo -e "  失败: $FAILED_TESTS"
fi
echo ""

# 详细结果表格
echo "详细结果:"
echo "----------------------------------------------"
printf "%-40s | %s\n" "测试项" "状态"
echo "----------------------------------------------"

for result in "${TEST_RESULTS[@]}"; do
    IFS='|' read -r test_name status <<< "$result"
    
    if [ "$status" = "PASSED" ]; then
        printf "%-40s | ${GREEN}✓ 通过${NC}\n" "$test_name"
    elif [ "$status" = "FAILED" ]; then
        printf "%-40s | ${RED}✗ 失败${NC}\n" "$test_name"
    else
        printf "%-40s | ${YELLOW}⚠ 跳过${NC}\n" "$test_name"
    fi
done

echo "----------------------------------------------"
echo ""

# 保存文本报告
REPORT_FILE="$SCRIPT_DIR/test_report_$(date +%Y%m%d_%H%M%S).txt"
{
    echo "EasyCloudDisk 测试报告"
    echo "生成时间: $(date)"
    echo ""
    echo "Java 单元测试: $( [ "$JAVA_TESTS_PASSED" = true ] && echo "通过" || echo "失败" )"
    echo ""
    echo "API 集成测试:"
    echo "  总计: $TOTAL_TESTS"
    echo "  通过: $PASSED_TESTS"
    echo "  失败: $FAILED_TESTS"
    echo ""
    echo "详细结果:"
    for result in "${TEST_RESULTS[@]}"; do
        echo "  $result"
    done
} > "$REPORT_FILE"

echo "测试报告已保存: $REPORT_FILE"
echo ""

# 返回退出码
if [ "$JAVA_TESTS_PASSED" = false ] || [ $FAILED_TESTS -gt 0 ]; then
    echo -e "${RED}✗ 测试失败${NC}"
    exit 1
else
    echo -e "${GREEN}✓ 所有测试通过！${NC}"
    exit 0
fi
