#!/bin/bash

# 测试文件共享功能
# 接口: POST /collaboration/shares, GET /collaboration/shares, GET /collaboration/shared-with-me, DELETE /collaboration/shares/{shareId}

# 加载配置
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/config.sh" 2>/dev/null || true

# 使用环境变量或默认值
API_BASE_URL="${API_BASE_URL:-http://localhost:8080}"

echo "=========================================="
echo "测试: 文件共享功能"
echo "=========================================="

# 临时环境文件
ENV_FILE="${SCRIPT_DIR}/.test_env"
touch "$ENV_FILE"

# 生成唯一标识
TIMESTAMP=$(date +%s)
RANDOM_ID=$RANDOM

# 1. 注册第一个用户（文件所有者）
echo ""
echo "[步骤 1] 注册用户1（文件所有者）..."
OWNER_EMAIL="owner_${TIMESTAMP}_${RANDOM_ID}@example.com"
OWNER_PASSWORD="Test123456_${TIMESTAMP}"

REGISTER_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${API_BASE_URL}/api/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"${OWNER_EMAIL}\",\"password\":\"${OWNER_PASSWORD}\"}")

HTTP_CODE=$(echo "$REGISTER_RESPONSE" | tail -n 1)
RESPONSE_BODY=$(echo "$REGISTER_RESPONSE" | sed '$d')

if [ "$HTTP_CODE" -eq 200 ]; then
    echo "✓ 用户1注册成功"
    OWNER_TOKEN=$(echo "$RESPONSE_BODY" | grep -o '"token":"[^"]*' | cut -d'"' -f4)
    echo "  邮箱: $OWNER_EMAIL"
else
    echo "✗ 用户1注册失败 (HTTP $HTTP_CODE)"
    echo "  响应: $RESPONSE_BODY"
    exit 1
fi

# 2. 注册第二个用户（共享目标用户）
echo ""
echo "[步骤 2] 注册用户2（共享接收者）..."
SHARED_EMAIL="shared_${TIMESTAMP}_${RANDOM_ID}@example.com"
SHARED_PASSWORD="Test123456_${TIMESTAMP}"

REGISTER_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${API_BASE_URL}/api/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"${SHARED_EMAIL}\",\"password\":\"${SHARED_PASSWORD}\"}")

HTTP_CODE=$(echo "$REGISTER_RESPONSE" | tail -n 1)
RESPONSE_BODY=$(echo "$REGISTER_RESPONSE" | sed '$d')

if [ "$HTTP_CODE" -eq 200 ]; then
    echo "✓ 用户2注册成功"
    SHARED_TOKEN=$(echo "$RESPONSE_BODY" | grep -o '"token":"[^"]*' | cut -d'"' -f4)
    SHARED_USER_ID=$(echo "$RESPONSE_BODY" | grep -o '"userId":"[^"]*' | cut -d'"' -f4)
    echo "  邮箱: $SHARED_EMAIL"
    echo "  用户ID: $SHARED_USER_ID"
else
    echo "✗ 用户2注册失败 (HTTP $HTTP_CODE)"
    echo "  响应: $RESPONSE_BODY"
    exit 1
fi

# 3. 用户1上传文件
echo ""
echo "[步骤 3] 用户1上传文件..."
TEST_FILE="${SCRIPT_DIR}/.test_shared_file.txt"
echo "Shared file content - ${TIMESTAMP}" > "$TEST_FILE"

UPLOAD_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${API_BASE_URL}/api/files/upload" \
  -H "Authorization: Bearer ${OWNER_TOKEN}" \
  -F "file=@${TEST_FILE}" \
  -F "path=/shared_test.txt")

HTTP_CODE=$(echo "$UPLOAD_RESPONSE" | tail -n 1)
RESPONSE_BODY=$(echo "$UPLOAD_RESPONSE" | sed '$d')

if [ "$HTTP_CODE" -eq 200 ]; then
    echo "✓ 文件上传成功"
    FILE_ID=$(echo "$RESPONSE_BODY" | grep -o '"fileId":"[^"]*' | cut -d'"' -f4)
    echo "  文件ID: $FILE_ID"
else
    echo "✗ 文件上传失败 (HTTP $HTTP_CODE)"
    echo "  响应: $RESPONSE_BODY"
    rm -f "$TEST_FILE"
    exit 1
fi

rm -f "$TEST_FILE"

# 4. 用户1创建共享链接
echo ""
echo "[步骤 4] 用户1创建共享链接..."
SHARE_REQUEST="{\"targetEmail\":\"${SHARED_EMAIL}\",\"permission\":\"READ\"}"

SHARE_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${API_BASE_URL}/api/collaboration/shares?fileId=${FILE_ID}" \
  -H "Authorization: Bearer ${OWNER_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "$SHARE_REQUEST")

HTTP_CODE=$(echo "$SHARE_RESPONSE" | tail -n 1)
RESPONSE_BODY=$(echo "$SHARE_RESPONSE" | sed '$d')

if [ "$HTTP_CODE" -eq 200 ]; then
    echo "✓ 共享创建成功"
    SHARE_ID=$(echo "$RESPONSE_BODY" | grep -o '"shareId":"[^"]*' | cut -d'"' -f4)
    echo "  共享ID: $SHARE_ID"
else
    echo "✗ 共享创建失败 (HTTP $HTTP_CODE)"
    echo "  响应: $RESPONSE_BODY"
    exit 1
fi

# 5. 用户2查看共享给我的文件
echo ""
echo "[步骤 5] 用户2查看共享给我的文件..."
SHARED_WITH_ME_RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "${API_BASE_URL}/api/collaboration/shared-with-me" \
  -H "Authorization: Bearer ${SHARED_TOKEN}")

HTTP_CODE=$(echo "$SHARED_WITH_ME_RESPONSE" | tail -n 1)
RESPONSE_BODY=$(echo "$SHARED_WITH_ME_RESPONSE" | sed '$d')

if [ "$HTTP_CODE" -eq 200 ]; then
    echo "✓ 获取共享文件列表成功"
    SHARED_COUNT=$(echo "$RESPONSE_BODY" | grep -o '"shareId":' | wc -l)
    echo "  共享文件数量: $SHARED_COUNT"
else
    echo "✗ 获取共享文件列表失败 (HTTP $HTTP_CODE)"
    echo "  响应: $RESPONSE_BODY"
fi

# 6. 用户1删除共享
echo ""
echo "[步骤 6] 用户1删除共享..."
DELETE_RESPONSE=$(curl -s -w "\n%{http_code}" -X DELETE "${API_BASE_URL}/api/collaboration/shares/${SHARE_ID}" \
  -H "Authorization: Bearer ${OWNER_TOKEN}")

HTTP_CODE=$(echo "$DELETE_RESPONSE" | tail -n 1)
RESPONSE_BODY=$(echo "$DELETE_RESPONSE" | sed '$d')

if [ "$HTTP_CODE" -eq 200 ]; then
    echo "✓ 共享删除成功"
    echo "✓ 文件共享功能测试通过"
    exit 0
else
    echo "✗ 共享删除失败 (HTTP $HTTP_CODE)"
    echo "  响应: $RESPONSE_BODY"
    exit 1
fi
