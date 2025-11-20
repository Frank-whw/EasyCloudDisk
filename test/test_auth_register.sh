#!/bin/bash
# 用户注册测试脚本
# 测试 POST /auth/register 接口

set -e

# 颜色输出
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 配置
API_BASE_URL="${API_BASE_URL:-http://localhost:8080}"
TEST_NAME="用户注册测试"

# 生成唯一的测试数据（时间戳或随机数）
TIMESTAMP=$(date +%s)
RANDOM_NUM=$((RANDOM % 10000))
TEST_EMAIL="test_${TIMESTAMP}_${RANDOM_NUM}@example.com"
TEST_PASSWORD="Test123456_${TIMESTAMP}"

echo "=========================================="
echo "开始测试: $TEST_NAME"
echo "=========================================="
echo "测试邮箱: $TEST_EMAIL"
echo "测试密码: ${TEST_PASSWORD:0:10}..."
echo ""

# 导出测试数据，供其他脚本使用
export TEST_EMAIL
export TEST_PASSWORD

# 准备请求数据
REQUEST_BODY=$(cat <<EOF
{
  "email": "$TEST_EMAIL",
  "password": "$TEST_PASSWORD"
}
EOF
)

# 执行注册请求
echo "发送请求: POST $API_BASE_URL/auth/register"
RESPONSE=$(curl -s -w "\nHTTP_CODE:%{http_code}" \
    -X POST "$API_BASE_URL/auth/register" \
    -H "Content-Type: application/json" \
    -d "$REQUEST_BODY" || echo "HTTP_CODE:000")

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
        # 提取 token
        TOKEN=$(echo "$HTTP_BODY" | grep -oE '"token":\s*"[^"]*' | cut -d'"' -f4 || echo "")
        if [ -n "$TOKEN" ]; then
            export AUTH_TOKEN="$TOKEN"
            # 如果有 ENV_FILE，将环境变量写入文件供主脚本使用
            if [ -n "$ENV_FILE" ]; then
                {
                    echo "export AUTH_TOKEN='$AUTH_TOKEN'"
                    echo "export TEST_EMAIL='$TEST_EMAIL'"
                    echo "export TEST_PASSWORD='$TEST_PASSWORD'"
                } >> "$ENV_FILE"
            fi
            echo -e "${GREEN}✓ 测试通过: $TEST_NAME${NC}"
            echo "注册成功，已保存认证令牌"
            echo "Token: ${TOKEN:0:50}..."
            exit 0
        else
            echo -e "${YELLOW}⚠ 警告: 注册成功但未找到 token${NC}"
            exit 1
        fi
    else
        echo -e "${YELLOW}⚠ 警告: HTTP 200 但 success 不为 true${NC}"
        echo "响应内容: $HTTP_BODY"
        exit 1
    fi
elif [ "$HTTP_CODE" = "400" ]; then
    # 可能是邮箱已存在，这是可以接受的（说明接口工作正常）
    if echo "$HTTP_BODY" | grep -qE '"code":\s*"EMAIL_EXISTS"|"message".*已存在'; then
        echo -e "${YELLOW}⚠ 邮箱已存在（接口正常工作）${NC}"
        exit 0
    else
        echo -e "${RED}✗ 测试失败: 参数验证失败${NC}"
        exit 1
    fi
else
    echo -e "${RED}✗ 测试失败: $TEST_NAME${NC}"
    echo "HTTP 状态码: $HTTP_CODE (期望: 200)"
    exit 1
fi
