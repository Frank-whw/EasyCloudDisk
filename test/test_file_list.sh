#!/bin/bash
# 文件列表测试脚本
# 测试 GET /files 接口

set -e

# 颜色输出
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 配置
API_BASE_URL="${API_BASE_URL:-http://localhost:8080}"
TEST_NAME="文件列表测试"

# 检查是否有认证令牌
if [ -z "$AUTH_TOKEN" ]; then
    echo -e "${YELLOW}⚠ 未找到认证令牌，尝试登录...${NC}"
    source "$(dirname "$0")/test_auth_login.sh" || {
        echo -e "${RED}✗ 无法获取认证令牌${NC}"
        exit 1
    }
fi

echo "=========================================="
echo "开始测试: $TEST_NAME"
echo "=========================================="
echo ""

# 执行文件列表请求
echo "发送请求: GET $API_BASE_URL/files"
RESPONSE=$(curl -s -w "\nHTTP_CODE:%{http_code}" \
    -X GET "$API_BASE_URL/files" \
    -H "Authorization: Bearer $AUTH_TOKEN" || echo "HTTP_CODE:000")

# 分离响应体和HTTP状态码
HTTP_BODY=$(echo "$RESPONSE" | sed -E 's/HTTP_CODE:[0-9]+$//')
HTTP_CODE=$(echo "$RESPONSE" | grep -oE 'HTTP_CODE:[0-9]+$' | cut -d: -f2 || echo "000")

echo "HTTP 状态码: $HTTP_CODE"
echo "响应内容: $HTTP_BODY"
echo ""

# 验证结果
if [ "$HTTP_CODE" = "200" ]; then
    # 验证响应格式
    if echo "$HTTP_BODY" | grep -qE '"success":\s*true'; then
        # 检查是否包含 data 字段（数组格式）
        if echo "$HTTP_BODY" | grep -qE '"data":\s*\[|"data":\s*null'; then
            # 尝试提取文件数量
            FILE_COUNT=$(echo "$HTTP_BODY" | grep -oE '"fileId":' | wc -l || echo "0")
            echo -e "${GREEN}✓ 测试通过: $TEST_NAME${NC}"
            echo "文件列表获取成功"
            echo "文件数量: $FILE_COUNT"
            if [ "$FILE_COUNT" -gt "0" ]; then
                echo "已找到 $FILE_COUNT 个文件"
            fi
            exit 0
        else
            echo -e "${YELLOW}⚠ 警告: 响应成功但 data 格式可能不正确${NC}"
            exit 1
        fi
    else
        echo -e "${YELLOW}⚠ 警告: HTTP 200 但 success 不为 true${NC}"
        echo "响应内容: $HTTP_BODY"
        exit 1
    fi
elif [ "$HTTP_CODE" = "401" ]; then
    echo -e "${RED}✗ 测试失败: 认证失败，请检查 token${NC}"
    exit 1
else
    echo -e "${RED}✗ 测试失败: $TEST_NAME${NC}"
    echo "HTTP 状态码: $HTTP_CODE (期望: 200)"
    exit 1
fi

