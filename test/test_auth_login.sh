#!/bin/bash
# 用户登录测试脚本
# 测试 POST /auth/login 接口
# 注意：此脚本需要在 test_auth_register.sh 之后运行，或使用已存在的用户

set -e

# 颜色输出
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 配置
API_BASE_URL="${API_BASE_URL:-http://localhost:8080}"
TEST_NAME="用户登录测试"

# 使用测试用户数据（如果已注册）
TEST_EMAIL="${TEST_EMAIL:-test@example.com}"
TEST_PASSWORD="${TEST_PASSWORD:-Test123456}"

# 如果环境变量中没有，创建一个新的测试用户先
if [ "$TEST_EMAIL" = "test@example.com" ] && [ "$TEST_PASSWORD" = "Test123456" ]; then
    TIMESTAMP=$(date +%s)
    RANDOM_NUM=$((RANDOM % 10000))
    TEST_EMAIL="test_${TIMESTAMP}_${RANDOM_NUM}@example.com"
    TEST_PASSWORD="Test123456_${TIMESTAMP}"
    
    echo "创建测试用户: $TEST_EMAIL"
    # 先注册用户
    REGISTER_RESPONSE=$(curl -s -X POST "$API_BASE_URL/auth/register" \
        -H "Content-Type: application/json" \
        -d "{\"email\":\"$TEST_EMAIL\",\"password\":\"$TEST_PASSWORD\"}")
    
    if echo "$REGISTER_RESPONSE" | grep -qE '"success":\s*true'; then
        echo "用户注册成功"
    else
        echo -e "${YELLOW}⚠ 用户注册失败，尝试直接登录${NC}"
    fi
fi

echo "=========================================="
echo "开始测试: $TEST_NAME"
echo "=========================================="
echo "测试邮箱: $TEST_EMAIL"
echo ""

# 准备请求数据
REQUEST_BODY=$(cat <<EOF
{
  "email": "$TEST_EMAIL",
  "password": "$TEST_PASSWORD"
}
EOF
)

# 执行登录请求
echo "发送请求: POST $API_BASE_URL/auth/login"
RESPONSE=$(curl -s -w "\nHTTP_CODE:%{http_code}" \
    -X POST "$API_BASE_URL/auth/login" \
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
            export TEST_EMAIL
            export TEST_PASSWORD
            # 如果有 ENV_FILE，将环境变量写入文件供主脚本使用
            if [ -n "$ENV_FILE" ]; then
                {
                    echo "export AUTH_TOKEN='$AUTH_TOKEN'"
                    echo "export TEST_EMAIL='$TEST_EMAIL'"
                    echo "export TEST_PASSWORD='$TEST_PASSWORD'"
                } >> "$ENV_FILE"
            fi
            echo -e "${GREEN}✓ 测试通过: $TEST_NAME${NC}"
            echo "登录成功，已保存认证令牌"
            echo "Token: ${TOKEN:0:50}..."
            exit 0
        else
            echo -e "${YELLOW}⚠ 警告: 登录成功但未找到 token${NC}"
            exit 1
        fi
    else
        echo -e "${YELLOW}⚠ 警告: HTTP 200 但 success 不为 true${NC}"
        echo "响应内容: $HTTP_BODY"
        exit 1
    fi
elif [ "$HTTP_CODE" = "401" ]; then
    # 用户名或密码错误
    if echo "$HTTP_BODY" | grep -qE '"code":\s*"INVALID_CREDENTIALS"|"message".*错误'; then
        echo -e "${YELLOW}⚠ 用户名或密码错误（接口正常工作）${NC}"
        exit 0
    else
        echo -e "${RED}✗ 测试失败: 认证失败但响应格式不正确${NC}"
        exit 1
    fi
else
    echo -e "${RED}✗ 测试失败: $TEST_NAME${NC}"
    echo "HTTP 状态码: $HTTP_CODE (期望: 200 或 401)"
    exit 1
fi

