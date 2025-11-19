#!/bin/bash

# 测试文件共享功能
# 接口: POST /collaboration/shares, GET /collaboration/shares, GET /collaboration/shared-with-me, DELETE /collaboration/shares/{shareId}

# 加载配置
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/config.example.sh" 2>/dev/null || true

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

REGISTER_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${API_BASE_URL}/auth/register" \
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
TARGET_EMAIL="target_${TIMESTAMP}_${RANDOM_ID}@example.com"
TARGET_PASSWORD="Test123456_${TIMESTAMP}"

REGISTER_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${API_BASE_URL}/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"${TARGET_EMAIL}\",\"password\":\"${TARGET_PASSWORD}\"}")

HTTP_CODE=$(echo "$REGISTER_RESPONSE" | tail -n 1)
RESPONSE_BODY=$(echo "$REGISTER_RESPONSE" | sed '$d')

if [ "$HTTP_CODE" -eq 200 ]; then
    echo "✓ 用户2注册成功"
    TARGET_TOKEN=$(echo "$RESPONSE_BODY" | grep -o '"token":"[^"]*' | cut -d'"' -f4)
    echo "  邮箱: $TARGET_EMAIL"
else
    echo "✗ 用户2注册失败 (HTTP $HTTP_CODE)"
    echo "  响应: $RESPONSE_BODY"
    exit 1
fi

# 3. 用户1上传文件
echo ""
echo "[步骤 3] 用户1上传文件..."
TEST_FILE="/tmp/share_test_${TIMESTAMP}.txt"
echo "This is a shared test file created at $(date)" > "$TEST_FILE"

UPLOAD_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${API_BASE_URL}/files/upload" \
  -H "Authorization: Bearer ${OWNER_TOKEN}" \
  -F "file=@${TEST_FILE}" \
  -F "path=/shared")

HTTP_CODE=$(echo "$UPLOAD_RESPONSE" | tail -n 1)
RESPONSE_BODY=$(echo "$UPLOAD_RESPONSE" | sed '$d')

if [ "$HTTP_CODE" -eq 200 ]; then
    echo "✓ 文件上传成功"
    FILE_ID=$(echo "$RESPONSE_BODY" | grep -o '"fileId":"[^"]*' | cut -d'"' -f4)
    FILE_NAME=$(echo "$RESPONSE_BODY" | grep -o '"name":"[^"]*' | cut -d'"' -f4)
    echo "  文件ID: $FILE_ID"
    echo "  文件名: $FILE_NAME"
else
    echo "✗ 文件上传失败 (HTTP $HTTP_CODE)"
    echo "  响应: $RESPONSE_BODY"
    rm -f "$TEST_FILE"
    exit 1
fi

rm -f "$TEST_FILE"

# 4. 创建共享（READ权限）
echo ""
echo "[步骤 4] 用户1将文件共享给用户2（READ权限）..."
SHARE_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${API_BASE_URL}/collaboration/shares?fileId=${FILE_ID}" \
  -H "Authorization: Bearer ${OWNER_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{\"targetEmail\":\"${TARGET_EMAIL}\",\"permission\":\"READ\"}")

HTTP_CODE=$(echo "$SHARE_RESPONSE" | tail -n 1)
RESPONSE_BODY=$(echo "$SHARE_RESPONSE" | sed '$d')

if [ "$HTTP_CODE" -eq 200 ]; then
    echo "✓ 共享创建成功"
    SHARE_ID=$(echo "$RESPONSE_BODY" | grep -o '"shareId":"[^"]*' | cut -d'"' -f4)
    echo "  共享ID: $SHARE_ID"
    echo "  权限: READ"
    echo "  目标用户: $TARGET_EMAIL"
else
    echo "✗ 共享创建失败 (HTTP $HTTP_CODE)"
    echo "  响应: $RESPONSE_BODY"
    exit 1
fi

# 5. 列出文件的所有共享
echo ""
echo "[步骤 5] 用户1查看文件的共享列表..."
LIST_RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "${API_BASE_URL}/collaboration/shares?fileId=${FILE_ID}" \
  -H "Authorization: Bearer ${OWNER_TOKEN}")

HTTP_CODE=$(echo "$LIST_RESPONSE" | tail -n 1)
RESPONSE_BODY=$(echo "$LIST_RESPONSE" | sed '$d')

if [ "$HTTP_CODE" -eq 200 ]; then
    echo "✓ 共享列表获取成功"
    SHARE_COUNT=$(echo "$RESPONSE_BODY" | grep -o '"shareId"' | wc -l)
    echo "  共享数量: $SHARE_COUNT"
else
    echo "✗ 共享列表获取失败 (HTTP $HTTP_CODE)"
    echo "  响应: $RESPONSE_BODY"
    exit 1
fi

# 6. 用户2查看"与我共享"的文件
echo ""
echo "[步骤 6] 用户2查看共享给我的文件..."
SHARED_WITH_ME_RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "${API_BASE_URL}/collaboration/shared-with-me" \
  -H "Authorization: Bearer ${TARGET_TOKEN}")

HTTP_CODE=$(echo "$SHARED_WITH_ME_RESPONSE" | tail -n 1)
RESPONSE_BODY=$(echo "$SHARED_WITH_ME_RESPONSE" | sed '$d')

if [ "$HTTP_CODE" -eq 200 ]; then
    echo "✓ 共享文件列表获取成功"
    FILE_COUNT=$(echo "$RESPONSE_BODY" | grep -o '"fileId"' | wc -l)
    echo "  共享文件数量: $FILE_COUNT"
    
    # 验证是否包含我们共享的文件
    if echo "$RESPONSE_BODY" | grep -q "$FILE_ID"; then
        echo "  ✓ 包含刚共享的文件"
    else
        echo "  ✗ 未找到刚共享的文件"
    fi
else
    echo "✗ 共享文件列表获取失败 (HTTP $HTTP_CODE)"
    echo "  响应: $RESPONSE_BODY"
    exit 1
fi

# 7. 用户2尝试下载共享文件（有READ权限）
echo ""
echo "[步骤 7] 用户2下载共享文件（验证READ权限）..."
DOWNLOAD_FILE="/tmp/downloaded_shared_${TIMESTAMP}.txt"
DOWNLOAD_RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "${API_BASE_URL}/files/${FILE_ID}/download" \
  -H "Authorization: Bearer ${TARGET_TOKEN}" \
  -o "$DOWNLOAD_FILE")

HTTP_CODE=$(echo "$DOWNLOAD_RESPONSE" | tail -n 1)

if [ "$HTTP_CODE" -eq 200 ] && [ -f "$DOWNLOAD_FILE" ]; then
    echo "✓ 文件下载成功（READ权限生效）"
    echo "  文件大小: $(wc -c < "$DOWNLOAD_FILE") 字节"
    rm -f "$DOWNLOAD_FILE"
else
    echo "✗ 文件下载失败 (HTTP $HTTP_CODE)"
    rm -f "$DOWNLOAD_FILE"
    exit 1
fi

# 8. 用户2尝试删除共享文件（应该失败，因为只有READ权限）
echo ""
echo "[步骤 8] 用户2尝试删除共享文件（应该失败）..."
DELETE_RESPONSE=$(curl -s -w "\n%{http_code}" -X DELETE "${API_BASE_URL}/files/${FILE_ID}" \
  -H "Authorization: Bearer ${TARGET_TOKEN}")

HTTP_CODE=$(echo "$DELETE_RESPONSE" | tail -n 1)

if [ "$HTTP_CODE" -ne 200 ]; then
    echo "✓ 删除被拒绝（权限控制正常）"
else
    echo "✗ 删除成功（权限控制异常！）"
    exit 1
fi

# 9. 撤销共享
echo ""
echo "[步骤 9] 用户1撤销共享..."
REVOKE_RESPONSE=$(curl -s -w "\n%{http_code}" -X DELETE "${API_BASE_URL}/collaboration/shares/${SHARE_ID}" \
  -H "Authorization: Bearer ${OWNER_TOKEN}")

HTTP_CODE=$(echo "$REVOKE_RESPONSE" | tail -n 1)
RESPONSE_BODY=$(echo "$REVOKE_RESPONSE" | sed '$d')

if [ "$HTTP_CODE" -eq 200 ]; then
    echo "✓ 共享撤销成功"
else
    echo "✗ 共享撤销失败 (HTTP $HTTP_CODE)"
    echo "  响应: $RESPONSE_BODY"
    exit 1
fi

# 10. 验证撤销后用户2无法访问
echo ""
echo "[步骤 10] 验证撤销后用户2无法访问文件..."
ACCESS_RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "${API_BASE_URL}/files/${FILE_ID}/download" \
  -H "Authorization: Bearer ${TARGET_TOKEN}")

HTTP_CODE=$(echo "$ACCESS_RESPONSE" | tail -n 1)

if [ "$HTTP_CODE" -ne 200 ]; then
    echo "✓ 访问被拒绝（撤销生效）"
else
    echo "✗ 仍可访问（撤销未生效）"
    exit 1
fi

# 11. 清理：用户1删除文件
echo ""
echo "[步骤 11] 清理测试数据..."
DELETE_RESPONSE=$(curl -s -w "\n%{http_code}" -X DELETE "${API_BASE_URL}/files/${FILE_ID}" \
  -H "Authorization: Bearer ${OWNER_TOKEN}")

HTTP_CODE=$(echo "$DELETE_RESPONSE" | tail -n 1)

if [ "$HTTP_CODE" -eq 200 ]; then
    echo "✓ 测试文件已清理"
else
    echo "⚠ 测试文件清理失败 (HTTP $HTTP_CODE)"
fi

# 清理临时文件
rm -f "$ENV_FILE"

echo ""
echo "=========================================="
echo "✓ 文件共享功能测试全部通过！"
echo "=========================================="
echo ""
echo "测试覆盖的功能:"
echo "  ✓ 创建文件共享"
echo "  ✓ 列出文件的共享"
echo "  ✓ 查看共享给我的文件"
echo "  ✓ 权限验证（READ权限可下载，不可删除）"
echo "  ✓ 撤销共享"
echo "  ✓ 撤销后访问控制"
echo ""

exit 0
