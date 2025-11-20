#!/bin/bash

# 快速测试API脚本

echo "========================================"
echo "测试 EasyCloudDisk API"
echo "========================================"
echo ""

BASE_URL="http://localhost:8080"

# 1. 健康检查
echo "1. 健康检查..."
curl -s "$BASE_URL/health" | python3 -m json.tool
echo ""
echo ""

# 2. 注册用户（如果还没有）
echo "2. 注册测试用户..."
REGISTER_RESPONSE=$(curl -s -X POST "$BASE_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"Test123456"}')

echo "$REGISTER_RESPONSE" | python3 -m json.tool
echo ""

# 提取token（如果注册成功）
TOKEN=$(echo "$REGISTER_RESPONSE" | python3 -c "import sys, json; data=json.load(sys.stdin); print(data.get('data', {}).get('token', ''))" 2>/dev/null)

if [ -z "$TOKEN" ]; then
    # 如果注册失败（可能用户已存在），尝试登录
    echo "用户可能已存在，尝试登录..."
    LOGIN_RESPONSE=$(curl -s -X POST "$BASE_URL/auth/login" \
      -H "Content-Type: application/json" \
      -d '{"email":"test@example.com","password":"Test123456"}')
    
    echo "$LOGIN_RESPONSE" | python3 -m json.tool
    TOKEN=$(echo "$LOGIN_RESPONSE" | python3 -c "import sys, json; data=json.load(sys.stdin); print(data.get('data', {}).get('token', ''))" 2>/dev/null)
fi

echo ""
echo "========================================"
if [ -n "$TOKEN" ]; then
    echo "✅ API测试成功！"
    echo "Token: ${TOKEN:0:20}..."
    echo ""
    echo "3. 获取文件列表..."
    curl -s -X GET "$BASE_URL/files" \
      -H "Authorization: Bearer $TOKEN" | python3 -m json.tool
else
    echo "⚠️  无法获取Token，请检查注册/登录是否成功"
fi

echo ""
echo "========================================"
echo "测试完成！"
echo ""

