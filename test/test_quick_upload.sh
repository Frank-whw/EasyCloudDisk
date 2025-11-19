#!/bin/bash
# 文件级去重（秒传）测试脚本
# 测试 POST /files/quick-check 和 POST /files/quick-upload 接口

set -e

# 颜色输出
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 配置
API_BASE_URL="${API_BASE_URL:-http://localhost:8080}"
TEST_NAME="文件级去重（秒传）测试"

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

# 第一步：先上传一个文件，用于后续秒传测试
echo "步骤 1: 上传原始文件用于秒传测试"
TIMESTAMP=$(date +%s)
RANDOM_NUM=$((RANDOM % 10000))
TEST_FILE_NAME="original_${TIMESTAMP}_${RANDOM_NUM}.txt"
TEST_FILE_CONTENT="This is a test file for quick upload testing. Timestamp: $TIMESTAMP Random: $RANDOM_NUM"

TEMP_FILE=$(mktemp)
echo "$TEST_FILE_CONTENT" > "$TEMP_FILE"

# 计算文件的 SHA-256 哈希
if command -v sha256sum > /dev/null 2>&1; then
    FILE_HASH=$(sha256sum "$TEMP_FILE" | cut -d' ' -f1)
elif command -v shasum > /dev/null 2>&1; then
    FILE_HASH=$(shasum -a 256 "$TEMP_FILE" | cut -d' ' -f1)
else
    echo -e "${RED}✗ 无法计算文件哈希（需要 sha256sum 或 shasum 命令）${NC}"
    rm -f "$TEMP_FILE"
    exit 1
fi

FILE_SIZE=$(wc -c < "$TEMP_FILE")

echo "文件名: $TEST_FILE_NAME"
echo "文件哈希: $FILE_HASH"
echo "文件大小: $FILE_SIZE 字节"
echo ""

# 上传原始文件
UPLOAD_RESPONSE=$(curl -s -X POST "$API_BASE_URL/files/upload" \
    -H "Authorization: Bearer $AUTH_TOKEN" \
    -F "file=@$TEMP_FILE" \
    -F "path=/$TEST_FILE_NAME")

if echo "$UPLOAD_RESPONSE" | grep -qE '"success":\s*true'; then
    echo -e "${GREEN}✓ 原始文件上传成功${NC}"
else
    echo -e "${RED}✗ 原始文件上传失败${NC}"
    echo "响应: $UPLOAD_RESPONSE"
    rm -f "$TEMP_FILE"
    exit 1
fi

rm -f "$TEMP_FILE"
echo ""

# 第二步：测试 quick-check 接口
echo "步骤 2: 检查文件是否可以秒传"
QUICK_CHECK_BODY=$(cat <<EOF
{
  "hash": "$FILE_HASH"
}
EOF
)

echo "发送请求: POST $API_BASE_URL/files/quick-check"
RESPONSE=$(curl -s -w "\nHTTP_CODE:%{http_code}" \
    -X POST "$API_BASE_URL/files/quick-check" \
    -H "Authorization: Bearer $AUTH_TOKEN" \
    -H "Content-Type: application/json" \
    -d "$QUICK_CHECK_BODY" || echo "HTTP_CODE:000")

HTTP_BODY=$(echo "$RESPONSE" | sed -E 's/HTTP_CODE:[0-9]+$//')
HTTP_CODE=$(echo "$RESPONSE" | grep -oE 'HTTP_CODE:[0-9]+$' | cut -d: -f2 || echo "000")

echo "HTTP 状态码: $HTTP_CODE"
echo "响应内容: $HTTP_BODY"
echo ""

if [ "$HTTP_CODE" = "200" ]; then
    if echo "$HTTP_BODY" | grep -qE '"canQuickUpload":\s*true'; then
        echo -e "${GREEN}✓ 文件可以秒传（quick-check 成功）${NC}"
    else
        echo -e "${YELLOW}⚠ 文件不可秒传（可能服务器未找到相同哈希）${NC}"
    fi
else
    echo -e "${RED}✗ quick-check 失败${NC}"
    exit 1
fi

echo ""

# 第三步：测试 quick-upload 接口（秒传）
echo "步骤 3: 执行秒传"
NEW_FILE_NAME="quick_uploaded_${TIMESTAMP}_${RANDOM_NUM}.txt"
QUICK_UPLOAD_BODY=$(cat <<EOF
{
  "hash": "$FILE_HASH",
  "fileName": "$NEW_FILE_NAME",
  "path": "/",
  "fileSize": $FILE_SIZE
}
EOF
)

echo "发送请求: POST $API_BASE_URL/files/quick-upload"
RESPONSE=$(curl -s -w "\nHTTP_CODE:%{http_code}" \
    -X POST "$API_BASE_URL/files/quick-upload" \
    -H "Authorization: Bearer $AUTH_TOKEN" \
    -H "Content-Type: application/json" \
    -d "$QUICK_UPLOAD_BODY" || echo "HTTP_CODE:000")

HTTP_BODY=$(echo "$RESPONSE" | sed -E 's/HTTP_CODE:[0-9]+$//')
HTTP_CODE=$(echo "$RESPONSE" | grep -oE 'HTTP_CODE:[0-9]+$' | cut -d: -f2 || echo "000")

echo "HTTP 状态码: $HTTP_CODE"
echo "响应内容: $HTTP_BODY"
echo ""

if [ "$HTTP_CODE" = "200" ]; then
    if echo "$HTTP_BODY" | grep -qE '"success":\s*true'; then
        FILE_ID=$(echo "$HTTP_BODY" | grep -oE '"fileId":\s*"[^"]*' | cut -d'"' -f4 || echo "")
        if [ -n "$FILE_ID" ]; then
            echo -e "${GREEN}✓ 测试通过: $TEST_NAME${NC}"
            echo "秒传成功，无需上传文件内容"
            echo "新文件 ID: $FILE_ID"
            echo "新文件名: $NEW_FILE_NAME"
            
            # 如果有 ENV_FILE，保存文件ID
            if [ -n "$ENV_FILE" ]; then
                {
                    echo "export QUICK_UPLOAD_FILE_ID='$FILE_ID'"
                } >> "$ENV_FILE"
            fi
            exit 0
        else
            echo -e "${YELLOW}⚠ 秒传成功但未找到 fileId${NC}"
            exit 1
        fi
    else
        echo -e "${YELLOW}⚠ HTTP 200 但 success 不为 true${NC}"
        exit 1
    fi
else
    echo -e "${RED}✗ 测试失败: $TEST_NAME${NC}"
    echo "HTTP 状态码: $HTTP_CODE (期望: 200)"
    exit 1
fi

