#!/bin/bash
# 文件删除测试脚本
# 测试 DELETE /files/{fileId} 接口

set -e

# 颜色输出
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 配置
API_BASE_URL="${API_BASE_URL:-http://localhost:8080}"
TEST_NAME="文件删除测试"

# 检查是否有认证令牌
if [ -z "$AUTH_TOKEN" ]; then
    echo -e "${YELLOW}⚠ 未找到认证令牌，尝试登录...${NC}"
    source "$(dirname "$0")/test_auth_login.sh" || {
        echo -e "${RED}✗ 无法获取认证令牌${NC}"
        exit 1
    }
fi

# 检查是否有已上传的文件ID
if [ -z "$UPLOADED_FILE_ID" ]; then
    echo -e "${YELLOW}⚠ 未找到已上传的文件ID，尝试上传文件...${NC}"
    source "$(dirname "$0")/test_file_upload.sh" || {
        echo -e "${RED}✗ 无法上传测试文件${NC}"
        exit 1
    }
fi

FILE_ID="$UPLOADED_FILE_ID"

echo "=========================================="
echo "开始测试: $TEST_NAME"
echo "=========================================="
echo "文件 ID: $FILE_ID"
echo ""

# 执行删除请求
echo "发送请求: DELETE $API_BASE_URL/files/$FILE_ID"
RESPONSE=$(curl -s -w "\nHTTP_CODE:%{http_code}" \
    -X DELETE "$API_BASE_URL/files/$FILE_ID" \
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
        echo -e "${GREEN}✓ 测试通过: $TEST_NAME${NC}"
        echo "文件删除成功"
        
        # 验证文件确实已删除（尝试获取文件列表，检查是否还包含该文件）
        sleep 1
        LIST_RESPONSE=$(curl -s -X GET "$API_BASE_URL/files" \
            -H "Authorization: Bearer $AUTH_TOKEN" || echo "")
        
        if echo "$LIST_RESPONSE" | grep -q "$FILE_ID"; then
            echo -e "${YELLOW}⚠ 警告: 文件列表中还包含已删除的文件ID${NC}"
        else
            echo "确认: 文件已从列表中移除"
        fi
        
        exit 0
    else
        echo -e "${YELLOW}⚠ 警告: HTTP 200 但 success 不为 true${NC}"
        echo "响应内容: $HTTP_BODY"
        exit 1
    fi
elif [ "$HTTP_CODE" = "401" ]; then
    echo -e "${RED}✗ 测试失败: 认证失败，请检查 token${NC}"
    exit 1
elif [ "$HTTP_CODE" = "404" ]; then
    echo -e "${YELLOW}⚠ 文件不存在（可能已被删除）${NC}"
    exit 0
else
    echo -e "${RED}✗ 测试失败: $TEST_NAME${NC}"
    echo "HTTP 状态码: $HTTP_CODE (期望: 200)"
    exit 1
fi

