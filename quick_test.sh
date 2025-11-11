#!/bin/bash

# Cloud 云盘系统 - 快速测试脚本
# 用法: ./quick_test.sh

set -e  # 遇到错误立即退出

echo "======================================"
echo "Cloud 云盘系统 - 快速测试"
echo "======================================"
echo ""

# 配置
EC2_HOST="ec2-54-95-61-230.ap-northeast-1.compute.amazonaws.com"
SSH_KEY="${SSH_KEY:-cloud2.pem}"
SERVER_URL="http://localhost:8080"

echo "测试配置:"
echo "  EC2 主机: $EC2_HOST"
echo "  SSH 密钥: $SSH_KEY"
echo "  服务地址: $SERVER_URL"
echo ""

# 函数: 彩色输出
print_success() {
    echo -e "\033[32m? $1\033[0m"
}

print_error() {
    echo -e "\033[31m? $1\033[0m"
}

print_info() {
    echo -e "\033[34m? $1\033[0m"
}

# 测试1: 注册用户
echo "测试 1: 用户注册"
REGISTER_RESPONSE=$(curl -s -X POST ${SERVER_URL}/auth/register \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"test_$(date +%s)@example.com\",\"password\":\"Test123456\"}")

if echo "$REGISTER_RESPONSE" | grep -q "success.*true"; then
    print_success "用户注册成功"
    TOKEN=$(echo "$REGISTER_RESPONSE" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
    USER_ID=$(echo "$REGISTER_RESPONSE" | grep -o '"userId":"[^"]*"' | cut -d'"' -f4)
    print_info "Token: ${TOKEN:0:20}..."
    print_info "User ID: $USER_ID"
else
    print_error "用户注册失败"
    echo "$REGISTER_RESPONSE"
    exit 1
fi
echo ""

# 测试2: 用户登录
echo "测试 2: 用户登录"
EMAIL=$(echo "$REGISTER_RESPONSE" | grep -o '"email":"[^"]*"' | cut -d'"' -f4)
LOGIN_RESPONSE=$(curl -s -X POST ${SERVER_URL}/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"Test123456\"}")

if echo "$LOGIN_RESPONSE" | grep -q "success.*true"; then
    print_success "用户登录成功"
else
    print_error "用户登录失败"
    echo "$LOGIN_RESPONSE"
    exit 1
fi
echo ""

# 测试3: 创建测试文件
echo "测试 3: 文件上传"
TEST_FILE="/tmp/test_$(date +%s).txt"
echo "Hello Cloud Disk! Timestamp: $(date)" > "$TEST_FILE"
print_info "创建测试文件: $TEST_FILE"

UPLOAD_RESPONSE=$(curl -s -X POST ${SERVER_URL}/files/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@$TEST_FILE" \
  -F "filePath=/")

if echo "$UPLOAD_RESPONSE" | grep -q "success.*true"; then
    print_success "文件上传成功"
    FILE_ID=$(echo "$UPLOAD_RESPONSE" | grep -o '"fileId":"[^"]*"' | cut -d'"' -f4)
    print_info "File ID: $FILE_ID"
else
    print_error "文件上传失败"
    echo "$UPLOAD_RESPONSE"
    exit 1
fi
echo ""

# 测试4: 获取文件列表
echo "测试 4: 文件列表"
LIST_RESPONSE=$(curl -s -X GET ${SERVER_URL}/files \
  -H "Authorization: Bearer $TOKEN")

if echo "$LIST_RESPONSE" | grep -q "success.*true"; then
    print_success "获取文件列表成功"
    FILE_COUNT=$(echo "$LIST_RESPONSE" | grep -o '"fileId"' | wc -l)
    print_info "文件数量: $FILE_COUNT"
else
    print_error "获取文件列表失败"
    echo "$LIST_RESPONSE"
    exit 1
fi
echo ""

# 测试5: 秒传（重复上传）
echo "测试 5: 秒传功能"
DUPLICATE_RESPONSE=$(curl -s -w "\nHTTP_CODE:%{http_code}" -X POST ${SERVER_URL}/files/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@$TEST_FILE" \
  -F "filePath=/")

HTTP_CODE=$(echo "$DUPLICATE_RESPONSE" | grep "HTTP_CODE:" | cut -d: -f2)
if [ "$HTTP_CODE" == "409" ]; then
    print_success "秒传功能正常（返回 409 Conflict）"
else
    print_error "秒传功能异常（应返回 409，实际返回 $HTTP_CODE）"
    echo "$DUPLICATE_RESPONSE"
fi
echo ""

# 测试6: 文件下载
echo "测试 6: 文件下载"
DOWNLOAD_FILE="/tmp/downloaded_$(date +%s).txt"
curl -s -X GET ${SERVER_URL}/files/${FILE_ID}/download \
  -H "Authorization: Bearer $TOKEN" \
  -o "$DOWNLOAD_FILE"

if [ -f "$DOWNLOAD_FILE" ]; then
    print_success "文件下载成功"
    ORIGINAL_HASH=$(sha256sum "$TEST_FILE" | cut -d' ' -f1)
    DOWNLOAD_HASH=$(sha256sum "$DOWNLOAD_FILE" | cut -d' ' -f1)
    
    if [ "$ORIGINAL_HASH" == "$DOWNLOAD_HASH" ]; then
        print_success "文件内容校验通过"
    else
        print_error "文件内容不一致"
    fi
else
    print_error "文件下载失败"
    exit 1
fi
echo ""

# 测试7: 文件删除
echo "测试 7: 文件删除"
DELETE_RESPONSE=$(curl -s -w "\nHTTP_CODE:%{http_code}" -X DELETE ${SERVER_URL}/files/${FILE_ID} \
  -H "Authorization: Bearer $TOKEN")

HTTP_CODE=$(echo "$DELETE_RESPONSE" | grep "HTTP_CODE:" | cut -d: -f2)
if [ "$HTTP_CODE" == "200" ]; then
    print_success "文件删除成功"
else
    print_error "文件删除失败（HTTP $HTTP_CODE）"
    echo "$DELETE_RESPONSE"
fi
echo ""

# 清理临时文件
rm -f "$TEST_FILE" "$DOWNLOAD_FILE"

# 总结
echo "======================================"
echo " 所有测试完成！"
echo "======================================"
echo ""
echo "测试统计:"
echo "  ? 用户注册"
echo "  ? 用户登录"
echo "  ? 文件上传"
echo "  ? 文件列表"
echo "  ? 秒传功能"
echo "  ? 文件下载"
echo "  ? 文件删除"
echo ""
echo " 提示: 检查服务器日志以查看详细信息"
echo "   tail -f ~/clouddisk/server.log"
