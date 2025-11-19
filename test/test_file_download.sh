#!/bin/bash
# 文件下载测试脚本
# 测试 GET /files/{fileId}/download 接口

set -e

# 颜色输出
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 配置
API_BASE_URL="${API_BASE_URL:-http://localhost:8080}"
TEST_NAME="文件下载测试"

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

# 创建临时下载文件
DOWNLOADED_FILE=$(mktemp)

# 执行下载请求
echo "发送请求: GET $API_BASE_URL/files/$FILE_ID/download"
HTTP_CODE=$(curl -s -w "%{http_code}" \
    -o "$DOWNLOADED_FILE" \
    -X GET "$API_BASE_URL/files/$FILE_ID/download" \
    -H "Authorization: Bearer $AUTH_TOKEN" || echo "000")

echo "HTTP 状态码: $HTTP_CODE"
echo "下载的文件大小: $(wc -c < "$DOWNLOADED_FILE" 2>/dev/null || echo 0) 字节"
echo ""

# 验证结果
if [ "$HTTP_CODE" = "200" ]; then
    # 检查下载的文件是否非空
    FILE_SIZE=$(wc -c < "$DOWNLOADED_FILE" 2>/dev/null || echo 0)
    if [ "$FILE_SIZE" -gt "0" ]; then
        echo -e "${GREEN}✓ 测试通过: $TEST_NAME${NC}"
        echo "文件下载成功"
        echo "文件大小: $FILE_SIZE 字节"
        echo "文件内容预览:"
        head -c 100 "$DOWNLOADED_FILE" 2>/dev/null | cat -A || echo "(无法显示)"
        echo ""
        rm -f "$DOWNLOADED_FILE"
        exit 0
    else
        echo -e "${YELLOW}⚠ 警告: HTTP 200 但下载的文件为空${NC}"
        rm -f "$DOWNLOADED_FILE"
        exit 1
    fi
elif [ "$HTTP_CODE" = "401" ]; then
    echo -e "${RED}✗ 测试失败: 认证失败，请检查 token${NC}"
    rm -f "$DOWNLOADED_FILE"
    exit 1
elif [ "$HTTP_CODE" = "404" ]; then
    echo -e "${YELLOW}⚠ 文件不存在（可能是已删除）${NC}"
    rm -f "$DOWNLOADED_FILE"
    exit 0
else
    echo -e "${RED}✗ 测试失败: $TEST_NAME${NC}"
    echo "HTTP 状态码: $HTTP_CODE (期望: 200)"
    rm -f "$DOWNLOADED_FILE"
    exit 1
fi

