#!/bin/bash
# 健康检查测试脚本
# 测试 GET /health 接口

set -e

# 颜色输出
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 配置
API_BASE_URL="${API_BASE_URL:-http://localhost:8080}"
TEST_NAME="健康检查测试"

echo "=========================================="
echo "开始测试: $TEST_NAME"
echo "=========================================="

# 执行健康检查
echo "发送请求: GET $API_BASE_URL/api/health"
RESPONSE=$(curl -s -w "\nHTTP_CODE:%{http_code}" "$API_BASE_URL/api/health" || echo "HTTP_CODE:000")

# 分离响应体和HTTP状态码
HTTP_BODY=$(echo "$RESPONSE" | sed -E 's/HTTP_CODE:[0-9]+$//')
HTTP_CODE=$(echo "$RESPONSE" | grep -oE 'HTTP_CODE:[0-9]+$' | cut -d: -f2 || echo "000")

echo "HTTP 状态码: $HTTP_CODE"
echo "响应内容: $HTTP_BODY"

# 验证结果
if [ "$HTTP_CODE" = "200" ]; then
    # 验证响应格式（JSON格式，包含status字段）
    if echo "$HTTP_BODY" | grep -qE '"status"|"database"|"storage"'; then
        echo -e "${GREEN}✓ 测试通过: $TEST_NAME${NC}"
        echo "响应格式正确，包含必要的状态信息"
        exit 0
    else
        echo -e "${YELLOW}⚠ 警告: HTTP 200 但响应格式可能不正确${NC}"
        echo "响应内容: $HTTP_BODY"
        exit 1
    fi
else
    echo -e "${RED}✗ 测试失败: $TEST_NAME${NC}"
    echo "HTTP 状态码: $HTTP_CODE (期望: 200)"
    exit 1
fi
