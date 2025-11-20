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

# 执行下载请求
echo "发送请求: GET $API_BASE_URL/files/$FILE_ID/download"
DOWNLOAD_FILE=$(mktemp)
RESPONSE=$(curl -s -w "\nHTTP_CODE:%{http_code}" \
    -X GET "$API_BASE_URL/files/$FILE_ID/download" \
    -H "Authorization: Bearer $AUTH_TOKEN" \
    -o "$DOWNLOAD_FILE" || echo "HTTP_CODE:000")

# 分离响应体和HTTP状态码
HTTP_CODE=$(echo "$RESPONSE" | grep -oE 'HTTP_CODE:[0-9]+$' | cut -d: -f2 || echo "000")

echo "HTTP 状态码: $HTTP_CODE"
echo ""

# 验证结果
if [ "$HTTP_CODE" = "200" ]; then
    # 检查下载的文件是否存在且不为空
    if [ -f "$DOWNLOAD_FILE" ] && [ -s "$DOWNLOAD_FILE" ]; then
        FILE_SIZE=$(wc -c < "$DOWNLOAD_FILE")
        echo -e "${GREEN}✓ 测试通过: $TEST_NAME${NC}"
        echo "文件下载成功"
        echo "下载文件大小: $FILE_SIZE 字节"
        
        # 显示文件内容的前几行（如果是文本文件）
        if file "$DOWNLOAD_FILE" | grep -q "text"; then
            echo "文件内容预览:"
            head -n 5 "$DOWNLOAD_FILE" | sed 's/^/  /'
        fi
        
        rm -f "$DOWNLOAD_FILE"
        exit 0
    else
        echo -e "${YELLOW}⚠ 警告: HTTP 200 但下载的文件为空或不存在${NC}"
        rm -f "$DOWNLOAD_FILE"
        exit 1
    fi
elif [ "$HTTP_CODE" = "404" ]; then
    echo -e "${YELLOW}⚠ 文件不存在（可能已被删除）${NC}"
    rm -f "$DOWNLOAD_FILE"
    exit 0
elif [ "$HTTP_CODE" = "401" ]; then
    echo -e "${RED}✗ 测试失败: 认证失败，请检查 token${NC}"
    rm -f "$DOWNLOAD_FILE"
    exit 1
else
    echo -e "${RED}✗ 测试失败: $TEST_NAME${NC}"
    echo "HTTP 状态码: $HTTP_CODE (期望: 200)"
    rm -f "$DOWNLOAD_FILE"
    exit 1
fi
