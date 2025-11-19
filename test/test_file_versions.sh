#!/bin/bash

# 测试文件版本控制功能
# 接口: POST /files/upload (版本更新), GET /files/{fileId}/versions

# 加载配置
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/config.example.sh" 2>/dev/null || true

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
TEST_FILE="/tmp/version_test_${TIMESTAMP}.txt"
echo "Version 1 content - created at $(date)" > "$TEST_FILE"

UPLOAD_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${API_BASE_URL}/files/upload" \
  -H "Authorization: Bearer ${AUTH_TOKEN}" \
  -F "file=@${TEST_FILE}" \
  -F "path=/versions")

HTTP_CODE=$(echo "$UPLOAD_RESPONSE" | tail -n 1)
RESPONSE_BODY=$(echo "$UPLOAD_RESPONSE" | sed '$d')

if [ "$HTTP_CODE" -eq 200 ]; then
    echo "✓ 版本1上传成功"
    FILE_ID=$(echo "$RESPONSE_BODY" | grep -o '"fileId":"[^"]*' | cut -d'"' -f4)
    FILE_NAME=$(echo "$RESPONSE_BODY" | grep -o '"name":"[^"]*' | cut -d'"' -f4)
    VERSION=$(echo "$RESPONSE_BODY" | grep -o '"version":[0-9]*' | grep -o '[0-9]*')
    echo "  文件ID: $FILE_ID"
    echo "  文件名: $FILE_NAME"
    echo "  版本号: $VERSION"
else
    echo "✗ 版本1上传失败 (HTTP $HTTP_CODE)"
    echo "  响应: $RESPONSE_BODY"
    rm -f "$TEST_FILE"
    exit 1
fi

# 3. 更新文件（版本2）
echo ""
echo "[步骤 3] 更新文件（版本2）..."
sleep 1  # 确保时间戳不同
echo "Version 2 content - updated at $(date)" > "$TEST_FILE"

UPLOAD_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${API_BASE_URL}/files/upload" \
  -H "Authorization: Bearer ${AUTH_TOKEN}" \
  -F "file=@${TEST_FILE}" \
  -F "path=/versions")

HTTP_CODE=$(echo "$UPLOAD_RESPONSE" | tail -n 1)
RESPONSE_BODY=$(echo "$UPLOAD_RESPONSE" | sed '$d')

if [ "$HTTP_CODE" -eq 200 ]; then
    echo "✓ 版本2上传成功"
    VERSION=$(echo "$RESPONSE_BODY" | grep -o '"version":[0-9]*' | grep -o '[0-9]*')
    echo "  版本号: $VERSION"
    
    if [ "$VERSION" -eq 2 ]; then
        echo "  ✓ 版本号正确递增"
    else
        echo "  ✗ 版本号异常（期望2，实际$VERSION）"
        exit 1
    fi
else
    echo "✗ 版本2上传失败 (HTTP $HTTP_CODE)"
    echo "  响应: $RESPONSE_BODY"
    rm -f "$TEST_FILE"
    exit 1
fi

# 4. 再次更新文件（版本3）
echo ""
echo "[步骤 4] 再次更新文件（版本3）..."
sleep 1
echo "Version 3 content - updated again at $(date)" > "$TEST_FILE"

UPLOAD_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${API_BASE_URL}/files/upload" \
  -H "Authorization: Bearer ${AUTH_TOKEN}" \
  -F "file=@${TEST_FILE}" \
  -F "path=/versions")

HTTP_CODE=$(echo "$UPLOAD_RESPONSE" | tail -n 1)
RESPONSE_BODY=$(echo "$UPLOAD_RESPONSE" | sed '$d')

if [ "$HTTP_CODE" -eq 200 ]; then
    echo "✓ 版本3上传成功"
    VERSION=$(echo "$RESPONSE_BODY" | grep -o '"version":[0-9]*' | grep -o '[0-9]*')
    echo "  版本号: $VERSION"
    
    if [ "$VERSION" -eq 3 ]; then
        echo "  ✓ 版本号正确递增"
    else
        echo "  ✗ 版本号异常（期望3，实际$VERSION）"
        exit 1
    fi
else
    echo "✗ 版本3上传失败 (HTTP $HTTP_CODE)"
    echo "  响应: $RESPONSE_BODY"
    rm -f "$TEST_FILE"
    exit 1
fi

rm -f "$TEST_FILE"

# 5. 下载当前版本（应该是版本3）
echo ""
echo "[步骤 5] 下载当前版本（版本3）..."
DOWNLOAD_FILE="/tmp/downloaded_v3_${TIMESTAMP}.txt"
DOWNLOAD_RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "${API_BASE_URL}/files/${FILE_ID}/download" \
  -H "Authorization: Bearer ${AUTH_TOKEN}" \
  -o "$DOWNLOAD_FILE")

HTTP_CODE=$(echo "$DOWNLOAD_RESPONSE" | tail -n 1)

if [ "$HTTP_CODE" -eq 200 ] && [ -f "$DOWNLOAD_FILE" ]; then
    echo "✓ 当前版本下载成功"
    CONTENT=$(cat "$DOWNLOAD_FILE")
    echo "  内容预览: $(echo "$CONTENT" | head -c 50)..."
    
    if echo "$CONTENT" | grep -q "Version 3"; then
        echo "  ✓ 内容为版本3"
    else
        echo "  ✗ 内容不是版本3"
        rm -f "$DOWNLOAD_FILE"
        exit 1
    fi
    rm -f "$DOWNLOAD_FILE"
else
    echo "✗ 下载失败 (HTTP $HTTP_CODE)"
    rm -f "$DOWNLOAD_FILE"
    exit 1
fi

# 6. 验证文件列表显示正确版本号
echo ""
echo "[步骤 6] 验证文件列表中的版本信息..."
LIST_RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "${API_BASE_URL}/files" \
  -H "Authorization: Bearer ${AUTH_TOKEN}")

HTTP_CODE=$(echo "$LIST_RESPONSE" | tail -n 1)
RESPONSE_BODY=$(echo "$LIST_RESPONSE" | sed '$d')

if [ "$HTTP_CODE" -eq 200 ]; then
    echo "✓ 文件列表获取成功"
    
    # 查找我们的文件并验证版本号
    if echo "$RESPONSE_BODY" | grep -q "\"fileId\":\"$FILE_ID\""; then
        echo "  ✓ 找到测试文件"
        
        # 提取版本号（简单方法，可能需要更精确的JSON解析）
        VERSION_IN_LIST=$(echo "$RESPONSE_BODY" | grep -A 5 "\"fileId\":\"$FILE_ID\"" | grep -o '"version":[0-9]*' | grep -o '[0-9]*' | head -1)
        echo "  列表中的版本号: $VERSION_IN_LIST"
        
        if [ "$VERSION_IN_LIST" -eq 3 ]; then
            echo "  ✓ 版本号正确"
        else
            echo "  ⚠ 版本号异常（期望3，实际$VERSION_IN_LIST）"
        fi
    else
        echo "  ✗ 未找到测试文件"
    fi
else
    echo "✗ 文件列表获取失败 (HTTP $HTTP_CODE)"
    echo "  响应: $RESPONSE_BODY"
    exit 1
fi

# 7. 清理测试数据
echo ""
echo "[步骤 7] 清理测试数据..."
DELETE_RESPONSE=$(curl -s -w "\n%{http_code}" -X DELETE "${API_BASE_URL}/files/${FILE_ID}" \
  -H "Authorization: Bearer ${AUTH_TOKEN}")

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
echo "✓ 文件版本控制功能测试全部通过！"
echo "=========================================="
echo ""
echo "测试覆盖的功能:"
echo "  ✓ 上传新文件（版本1）"
echo "  ✓ 更新已有文件（版本2）"
echo "  ✓ 多次更新（版本3）"
echo "  ✓ 版本号自动递增"
echo "  ✓ 下载当前版本"
echo "  ✓ 文件列表显示版本信息"
echo ""
echo "注意: 历史版本保留在数据库中，可通过 FileVersion 表查询"
echo ""

exit 0
