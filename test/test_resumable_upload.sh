#!/bin/bash
# 断点续传测试脚本
# 测试 POST /files/resumable/init, POST /files/resumable/{sessionId}/chunk/{chunkIndex}, 
# POST /files/resumable/{sessionId}/complete, GET /files/resumable/sessions

set -e

# 颜色输出
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 配置
API_BASE_URL="${API_BASE_URL:-http://localhost:8080}"
TEST_NAME="断点续传测试"
CHUNK_SIZE=$((2 * 1024 * 1024))  # 2MB per chunk

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

# 生成唯一的测试文件（模拟一个大文件，需要多块）
TIMESTAMP=$(date +%s)
RANDOM_NUM=$((RANDOM % 10000))
TEST_FILE_NAME="resumable_${TIMESTAMP}_${RANDOM_NUM}.dat"
TEST_FILE_PATH="/resumable/$TEST_FILE_NAME"

# 创建测试文件（约 5MB，需要 3 个 2MB 块）
TEMP_FILE=$(mktemp)
echo "Creating test file of approximately 5MB..."
dd if=/dev/urandom of="$TEMP_FILE" bs=1M count=5 2>/dev/null || {
    # 如果没有 dd，使用其他方法创建大文件
    for i in {1..1024}; do
        echo "Test data chunk $i - Random: $RANDOM Timestamp: $TIMESTAMP" >> "$TEMP_FILE"
    done
}

FILE_SIZE=$(wc -c < "$TEMP_FILE")
echo "文件名称: $TEST_FILE_NAME"
echo "文件大小: $FILE_SIZE 字节"
echo "预计块数: $(( (FILE_SIZE + CHUNK_SIZE - 1) / CHUNK_SIZE ))"
echo ""

# 步骤 1: 初始化断点续传会话
echo "步骤 1: 初始化断点续传会话"
INIT_BODY=$(cat <<EOF
{
  "fileName": "$TEST_FILE_NAME",
  "path": "/resumable",
  "fileSize": $FILE_SIZE
}
EOF
)

echo "发送请求: POST $API_BASE_URL/files/resumable/init"
RESPONSE=$(curl -s -w "\nHTTP_CODE:%{http_code}" \
    -X POST "$API_BASE_URL/files/resumable/init" \
    -H "Authorization: Bearer $AUTH_TOKEN" \
    -H "Content-Type: application/json" \
    -d "$INIT_BODY" || echo "HTTP_CODE:000")

HTTP_BODY=$(echo "$RESPONSE" | sed -E 's/HTTP_CODE:[0-9]+$//')
HTTP_CODE=$(echo "$RESPONSE" | grep -oE 'HTTP_CODE:[0-9]+$' | cut -d: -f2 || echo "000")

echo "HTTP 状态码: $HTTP_CODE"
echo "响应内容: $HTTP_BODY"
echo ""

if [ "$HTTP_CODE" != "200" ]; then
    echo -e "${RED}✗ 初始化会话失败${NC}"
    rm -f "$TEMP_FILE"
    exit 1
fi

SESSION_ID=$(echo "$HTTP_BODY" | grep -oE '"sessionId":\s*"[^"]*' | cut -d'"' -f4 || echo "")
TOTAL_CHUNKS=$(echo "$HTTP_BODY" | grep -oE '"totalChunks":\s*[0-9]+' | grep -oE '[0-9]+' || echo "0")

if [ -z "$SESSION_ID" ]; then
    echo -e "${RED}✗ 未找到 sessionId${NC}"
    rm -f "$TEMP_FILE"
    exit 1
fi

echo -e "${GREEN}✓ 会话初始化成功${NC}"
echo "Session ID: $SESSION_ID"
echo "总块数: $TOTAL_CHUNKS"
echo ""

# 步骤 2: 上传分块
echo "步骤 2: 上传文件分块"
CHUNK_INDEX=0
OFFSET=0
UPLOADED_CHUNKS=0

while [ $OFFSET -lt $FILE_SIZE ]; do
    CHUNK_END=$((OFFSET + CHUNK_SIZE))
    if [ $CHUNK_END -gt $FILE_SIZE ]; then
        CHUNK_END=$FILE_SIZE
    fi
    CHUNK_LEN=$((CHUNK_END - OFFSET))
    
    # 提取当前块
    CHUNK_FILE=$(mktemp)
    if command -v dd > /dev/null 2>&1; then
        dd if="$TEMP_FILE" of="$CHUNK_FILE" bs=1 skip=$OFFSET count=$CHUNK_LEN 2>/dev/null || {
            # 如果 dd 失败，使用 tail + head
            tail -c +$((OFFSET + 1)) "$TEMP_FILE" | head -c $CHUNK_LEN > "$CHUNK_FILE" 2>/dev/null || {
                # 如果 tail/head 也失败，使用 Python
                python3 -c "
import sys
with open('$TEMP_FILE', 'rb') as f:
    f.seek($OFFSET)
    data = f.read($CHUNK_LEN)
with open('$CHUNK_FILE', 'wb') as f:
    f.write(data)
" 2>/dev/null || {
                    echo -e "${RED}✗ 无法提取块数据${NC}"
                    rm -f "$CHUNK_FILE" "$TEMP_FILE"
                    exit 1
                }
            }
        }
    else
        # 使用 Python 作为备选
        python3 -c "
import sys
with open('$TEMP_FILE', 'rb') as f:
    f.seek($OFFSET)
    data = f.read($CHUNK_LEN)
with open('$CHUNK_FILE', 'wb') as f:
    f.write(data)
" 2>/dev/null || {
            echo -e "${RED}✗ 无法提取块数据（需要 dd 或 Python3）${NC}"
            rm -f "$CHUNK_FILE" "$TEMP_FILE"
            exit 1
        }
    fi
    
    echo "上传块 $CHUNK_INDEX: 偏移=$OFFSET, 大小=$CHUNK_LEN 字节"
    
    RESPONSE=$(curl -s -w "\nHTTP_CODE:%{http_code}" \
        -X POST "$API_BASE_URL/files/resumable/$SESSION_ID/chunk/$CHUNK_INDEX" \
        -H "Authorization: Bearer $AUTH_TOKEN" \
        -F "chunk=@$CHUNK_FILE" || echo "HTTP_CODE:000")
    
    HTTP_BODY=$(echo "$RESPONSE" | sed -E 's/HTTP_CODE:[0-9]+$//')
    HTTP_CODE=$(echo "$RESPONSE" | grep -oE 'HTTP_CODE:[0-9]+$' | cut -d: -f2 || echo "000")
    
    rm -f "$CHUNK_FILE"
    
    if [ "$HTTP_CODE" = "200" ]; then
        UPLOADED_CHUNKS=$(echo "$HTTP_BODY" | grep -oE '"uploadedChunks":\s*[0-9]+' | grep -oE '[0-9]+' || echo "$((CHUNK_INDEX + 1))")
        echo "  块 $CHUNK_INDEX 上传成功 (已上传 $UPLOADED_CHUNKS/$TOTAL_CHUNKS)"
        ((CHUNK_INDEX++))
        OFFSET=$CHUNK_END
    else
        echo -e "${RED}✗ 块 $CHUNK_INDEX 上传失败 (HTTP $HTTP_CODE)${NC}"
        rm -f "$TEMP_FILE"
        exit 1
    fi
done

rm -f "$TEMP_FILE"
echo ""

# 步骤 3: 完成断点续传
echo "步骤 3: 完成断点续传，合并所有分块"
RESPONSE=$(curl -s -w "\nHTTP_CODE:%{http_code}" \
    -X POST "$API_BASE_URL/files/resumable/$SESSION_ID/complete" \
    -H "Authorization: Bearer $AUTH_TOKEN" || echo "HTTP_CODE:000")

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
            echo "断点续传完成，文件已成功上传"
            echo "File ID: $FILE_ID"
            echo "上传块数: $CHUNK_INDEX"
            
            # 如果有 ENV_FILE，保存文件ID
            if [ -n "$ENV_FILE" ]; then
                {
                    echo "export RESUMABLE_FILE_ID='$FILE_ID'"
                    echo "export RESUMABLE_SESSION_ID='$SESSION_ID'"
                } >> "$ENV_FILE"
            fi
            exit 0
        else
            echo -e "${YELLOW}⚠ 上传完成但未找到 fileId${NC}"
            exit 1
        fi
    else
        echo -e "${YELLOW}⚠ HTTP 200 但 success 不为 true${NC}"
        exit 1
    fi
else
    echo -e "${RED}✗ 测试失败: 完成断点续传失败${NC}"
    echo "HTTP 状态码: $HTTP_CODE (期望: 200)"
    exit 1
fi
