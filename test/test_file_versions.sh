#!/bin/bash

# 测试文件版本控制功能
# 接口: POST /files/upload (版本更新), GET /files/{fileId}/versions

# 加载配置
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/config.sh" 2>/dev/null || true

# 使用环境变量或默认值
API_BASE_URL="${API_BASE_URL:-http://localhost:8080}"

echo "=========================================="
echo "测试: 文件版本控制功能"
echo "=========================================="

# 临时环境文件
ENV_FILE="${SCRIPT_DIR}/.test_env"
touch "$ENV_FILE"

# 生成唯一标识
TIMESTAMP=$(date +%s)
RANDOM_ID=$RANDOM

# 1. 注册用户
echo ""
echo "[步骤 1] 注册测试用户..."
TEST_EMAIL="version_test_${TIMESTAMP}_${RANDOM_ID}@example.com"
TEST_PASSWORD="Test123456_${TIMESTAMP}"

REGISTER_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${API_BASE_URL}/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"${TEST_EMAIL}\",\"password\":\"${TEST_PASSWORD}\"}")

HTTP_CODE=$(echo "$REGISTER_RESPONSE" | tail -n 1)
RESPONSE_BODY=$(echo "$REGISTER_RESPONSE" | sed '$d')

if [ "$HTTP_CODE" -eq 200 ]; then
    echo "✓ 用户注册成功"
    AUTH_TOKEN=$(echo "$RESPONSE_BODY" | grep -o '"token":"[^"]*' | cut -d'"' -f4)
    echo "  邮箱: $TEST_EMAIL"
else
    echo "✗ 用户注册失败 (HTTP $HTTP_CODE)"
    echo "  响应: $RESPONSE_BODY"
    exit 1
fi

# 2. 上传文件（版本1）
echo ""
echo "[步骤 2] 上传文件（版本1）..."
TEST_FILE="${SCRIPT_DIR}/.test_version_v1.txt"
echo "Version 1 content - ${TIMESTAMP}" > "$TEST_FILE"

UPLOAD_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${API_BASE_URL}/files/upload" \
  -H "Authorization: Bearer ${AUTH_TOKEN}" \
  -F "file=@${TEST_FILE}" \
  -F "path=/test_versions.txt")

HTTP_CODE=$(echo "$UPLOAD_RESPONSE" | tail -n 1)
RESPONSE_BODY=$(echo "$UPLOAD_RESPONSE" | sed '$d')

if [ "$HTTP_CODE" -eq 200 ]; then
    echo "✓ 版本1上传成功"
    FILE_ID=$(echo "$RESPONSE_BODY" | grep -o '"fileId":"[^"]*' | cut -d'"' -f4)
    echo "  文件ID: $FILE_ID"
else
    echo "✗ 版本1上传失败 (HTTP $HTTP_CODE)"
    echo "  响应: $RESPONSE_BODY"
    rm -f "$TEST_FILE"
    exit 1
fi

rm -f "$TEST_FILE"

# 等待1秒，确保版本号递增
sleep 1

# 3. 更新文件（版本2）
echo ""
echo "[步骤 3] 更新文件（版本2）..."
TEST_FILE="${SCRIPT_DIR}/.test_version_v2.txt"
echo "Version 2 content - updated at ${TIMESTAMP}" > "$TEST_FILE"

UPDATE_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${API_BASE_URL}/files/upload" \
  -H "Authorization: Bearer ${AUTH_TOKEN}" \
  -F "file=@${TEST_FILE}" \
  -F "path=/test_versions.txt")

HTTP_CODE=$(echo "$UPDATE_RESPONSE" | tail -n 1)
RESPONSE_BODY=$(echo "$UPDATE_RESPONSE" | sed '$d')

if [ "$HTTP_CODE" -eq 200 ]; then
    echo "✓ 版本2上传成功"
else
    echo "✗ 版本2上传失败 (HTTP $HTTP_CODE)"
    echo "  响应: $RESPONSE_BODY"
    rm -f "$TEST_FILE"
    exit 1
fi

rm -f "$TEST_FILE"

# 4. 获取版本历史
echo ""
echo "[步骤 4] 获取文件版本历史..."
VERSIONS_RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "${API_BASE_URL}/files/${FILE_ID}/versions" \
  -H "Authorization: Bearer ${AUTH_TOKEN}")

HTTP_CODE=$(echo "$VERSIONS_RESPONSE" | tail -n 1)
RESPONSE_BODY=$(echo "$VERSIONS_RESPONSE" | sed '$d')

if [ "$HTTP_CODE" -eq 200 ]; then
    echo "✓ 版本历史获取成功"
    VERSION_COUNT=$(echo "$RESPONSE_BODY" | grep -o '"version":' | wc -l)
    echo "  版本数量: $VERSION_COUNT"
    
    if [ "$VERSION_COUNT" -ge 2 ]; then
        echo "✓ 文件版本控制测试通过"
        exit 0
    else
        echo "✗ 版本数量不符合预期（期望>=2，实际=$VERSION_COUNT）"
        exit 1
    fi
else
    echo "✗ 获取版本历史失败 (HTTP $HTTP_CODE)"
    echo "  响应: $RESPONSE_BODY"
    exit 1
fi
