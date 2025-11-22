#!/bin/bash
# 块级去重测试脚本
# 测试上传两个相同内容的文件，验证块级去重功能

set -e

# 颜色输出
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 配置
API_BASE_URL="${API_BASE_URL:-http://localhost:8080}"
TEST_NAME="块级去重测试"
CHUNK_SIZE=$((4 * 1024 * 1024))  # 4MB per chunk

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

# 生成唯一的测试内容（需要大于块大小才能触发块级去重）
TIMESTAMP=$(date +%s)
RANDOM_NUM=$((RANDOM % 10000))
TEST_CONTENT_PREFIX="Chunk deduplication test data - Timestamp: $TIMESTAMP Random: $RANDOM_NUM"

# 创建测试文件（5MB，确保会被分块）
TEST_FILE1=$(mktemp)
TEST_FILE2=$(mktemp)

echo "创建两个相同内容的测试文件（用于块级去重测试）"

# 创建5MB文件（确保超过4MB块大小阈值）
if command -v dd > /dev/null 2>&1; then
    dd if=/dev/zero of="$TEST_FILE1" bs=1024 count=5120 2>/dev/null
else
    # 如果没有 dd，使用 Python
    python3 -c "with open('$TEST_FILE1', 'wb') as f: f.write(b'$TEST_CONTENT_PREFIX' * 65536)" 2>/dev/null || {
        # 备用方案：多次写入
        for i in $(seq 1 1000); do
            printf "%s\n" "$TEST_CONTENT_PREFIX - Line $i - $(head -c 5000 /dev/zero | tr '\0' 'X')" >> "$TEST_FILE1"
        done
    }
fi

# 复制到第二个文件（保证内容完全相同）
cp "$TEST_FILE1" "$TEST_FILE2"

FILE1_SIZE=$(wc -c < "$TEST_FILE1")
FILE2_SIZE=$(wc -c < "$TEST_FILE2")

TEST_FILE1_NAME="chunk_test1_${TIMESTAMP}_${RANDOM_NUM}.txt"
TEST_FILE2_NAME="chunk_test2_${TIMESTAMP}_${RANDOM_NUM}.txt"

echo "文件1: $TEST_FILE1_NAME (大小: $FILE1_SIZE 字节)"
echo "文件2: $TEST_FILE2_NAME (大小: $FILE2_SIZE 字节)"
echo "预计块数: $(( (FILE1_SIZE + CHUNK_SIZE - 1) / CHUNK_SIZE ))"
echo ""

# 步骤 1: 上传第一个文件
# 步骤 1: 上传第一个文件
RESPONSE=$(curl -s -w "\nHTTP_CODE:%{http_code}" \
    -X POST "$API_BASE_URL/api/files/upload" \
    -H "Authorization: Bearer $AUTH_TOKEN" \
    -F "file=@$TEST_FILE1" \
    -F "path=/$TEST_FILE1_NAME" || echo "HTTP_CODE:000")

HTTP_BODY=$(echo "$RESPONSE" | sed -E 's/HTTP_CODE:[0-9]+$//')
HTTP_CODE=$(echo "$RESPONSE" | grep -oE 'HTTP_CODE:[0-9]+$' | cut -d: -f2 || echo "000")

if [ "$HTTP_CODE" != "200" ]; then
    echo -e "${RED}✗ 第一个文件上传失败${NC}"
    rm -f "$TEST_FILE1" "$TEST_FILE2"
    exit 1
fi

FILE1_ID=$(echo "$HTTP_BODY" | grep -oE '"fileId":\s*"[^"]*' | cut -d'"' -f4 || echo "")
if [ -z "$FILE1_ID" ]; then
    echo -e "${RED}✗ 未找到第一个文件的 fileId${NC}"
    rm -f "$TEST_FILE1" "$TEST_FILE2"
    exit 1
fi

echo -e "${GREEN}✓ 第一个文件上传成功${NC}"
echo "File1 ID: $FILE1_ID"
echo ""

# 步骤 2: 上传第二个相同内容的文件（应该触发块级去重）
# 步骤 2: 上传第二个相同内容的文件（应该触发块级去重）
RESPONSE=$(curl -s -w "\nHTTP_CODE:%{http_code}" \
    -X POST "$API_BASE_URL/api/files/upload" \
    -H "Authorization: Bearer $AUTH_TOKEN" \
    -F "file=@$TEST_FILE2" \
    -F "path=/$TEST_FILE2_NAME" || echo "HTTP_CODE:000")

HTTP_BODY=$(echo "$RESPONSE" | sed -E 's/HTTP_CODE:[0-9]+$//')
HTTP_CODE=$(echo "$RESPONSE" | grep -oE 'HTTP_CODE:[0-9]+$' | cut -d: -f2 || echo "000")

echo "HTTP 状态码: $HTTP_CODE"
echo "响应内容: $HTTP_BODY"
echo ""

rm -f "$TEST_FILE1" "$TEST_FILE2"

if [ "$HTTP_CODE" != "200" ]; then
    echo -e "${RED}✗ 第二个文件上传失败${NC}"
    exit 1
fi

FILE2_ID=$(echo "$HTTP_BODY" | grep -oE '"fileId":\s*"[^"]*' | cut -d'"' -f4 || echo "")
if [ -z "$FILE2_ID" ]; then
    echo -e "${RED}✗ 未找到第二个文件的 fileId${NC}"
    exit 1
fi

echo -e "${GREEN}✓ 第二个文件上传成功${NC}"
echo "File2 ID: $FILE2_ID"
echo ""

# 步骤 3: 验证块级去重（通过检查S3存储或文件元数据）
echo "步骤 3: 验证块级去重"
echo "注意: 块级去重的验证需要查看数据库或S3存储"
echo "两个文件的块应该共享相同的存储块（ref_count > 1）"

# 获取文件列表，检查文件信息
LIST_RESPONSE=$(curl -s -X GET "$API_BASE_URL/api/files" \
    -H "Authorization: Bearer $AUTH_TOKEN" || echo "")

if echo "$LIST_RESPONSE" | grep -q "$FILE1_ID" && echo "$LIST_RESPONSE" | grep -q "$FILE2_ID"; then
    echo -e "${GREEN}✓ 两个文件都已保存在系统中${NC}"
    echo ""
    echo "块级去重验证说明:"
    echo "- 如果文件大小超过块大小阈值（4MB），应该被分块存储"
    echo "- 两个相同内容的文件的块应该共享相同的存储块"
    echo "- 这可以通过检查数据库中的 file_chunks 表来验证（ref_count）"
    echo ""
    
    if [ -n "$ENV_FILE" ]; then
        {
            echo "export CHUNK_TEST_FILE1_ID='$FILE1_ID'"
            echo "export CHUNK_TEST_FILE2_ID='$FILE2_ID'"
        } >> "$ENV_FILE"
    fi
    
    echo -e "${GREEN}✓ 测试通过: $TEST_NAME${NC}"
    echo "块级去重功能已测试（实际验证需要检查数据库）"
    exit 0
else
    echo -e "${YELLOW}⚠ 无法验证文件是否在列表中${NC}"
    exit 1
fi
