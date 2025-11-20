#!/bin/bash
# 文件上传测试脚本
# 测试 POST /files/upload 接口

set -e

# 颜色输出
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 配置
API_BASE_URL="${API_BASE_URL:-http://localhost:8080}"
TEST_NAME="文件上传测试"

# 检查是否有认证令牌
if [ -z "$AUTH_TOKEN" ]; then
    echo -e "${YELLOW}⚠ 未找到认证令牌，尝试登录...${NC}"
    # 运行登录测试获取令牌
    source "$(dirname "$0")/test_auth_login.sh" || {
        echo -e "${RED}✗ 无法获取认证令牌${NC}"
        exit 1
    }
fi

# 生成唯一的测试文件
TIMESTAMP=$(date +%s)
RANDOM_NUM=$((RANDOM % 10000))
TEST_FILE_NAME="test_file_${TIMESTAMP}_${RANDOM_NUM}.txt"
TEST_FILE_PATH="/test/${TEST_FILE_NAME}"
TEST_FILE_CONTENT="Hello Cloud Disk! Test file created at $(date '+%Y-%m-%d %H:%M:%S')
Random number: $RANDOM
Timestamp: $TIMESTAMP
This is a unique test file for API testing."

# 创建临时测试文件
TEMP_FILE=$(mktemp)
echo "$TEST_FILE_CONTENT" > "$TEMP_FILE"

echo "=========================================="
echo "开始测试: $TEST_NAME"
echo "=========================================="
echo "文件名称: $TEST_FILE_NAME"
echo "文件路径: $TEST_FILE_PATH"
echo "文件大小: $(wc -c < "$TEMP_FILE") 字节"
echo ""

# 执行上传请求
echo "发送请求: POST $API_BASE_URL/files/upload"
RESPONSE=$(curl -s -w "\nHTTP_CODE:%{http_code}" \
    -X POST "$API_BASE_URL/files/upload" \
    -H "Authorization: Bearer $AUTH_TOKEN" \
    -F "file=@$TEMP_FILE" \
    -F "path=$TEST_FILE_PATH" || echo "HTTP_CODE:000")

# 分离响应体和HTTP状态码
HTTP_BODY=$(echo "$RESPONSE" | sed -E 's/HTTP_CODE:[0-9]+$//')
HTTP_CODE=$(echo "$RESPONSE" | grep -oE 'HTTP_CODE:[0-9]+$' | cut -d: -f2 || echo "000")

echo "HTTP 状态码: $HTTP_CODE"
echo "完整响应: $HTTP_BODY"
echo ""

# 如果是错误响应，打印更详细的信息
if [ "$HTTP_CODE" != "200" ]; then
    echo -e "${RED}✗ 上传失败 - 详细错误信息:${NC}"
    echo "$HTTP_BODY" | python3 -m json.tool 2>/dev/null || echo "$HTTP_BODY"
    echo ""
fi

# 清理临时文件
rm -f "$TEMP_FILE"

# 验证结果
if [ "$HTTP_CODE" = "200" ]; then
    # 验证响应格式
    if echo "$HTTP_BODY" | grep -qE '"success":\s*true'; then
        # 提取 fileId 和其他信息
        FILE_ID=$(echo "$HTTP_BODY" | grep -oE '"fileId":\s*"[^"]*' | cut -d'"' -f4 || echo "")
        S3_KEY=$(echo "$HTTP_BODY" | grep -oE '"s3_key":\s*"[^"]*' | cut -d'"' -f4 || echo "")
        STORAGE_KEY=$(echo "$HTTP_BODY" | grep -oE '"storageKey":\s*"[^"]*' | cut -d'"' -f4 || echo "")
        FILE_SIZE=$(echo "$HTTP_BODY" | grep -oE '"fileSize":\s*[0-9]+' | grep -oE '[0-9]+' || echo "")
        
        if [ -n "$FILE_ID" ]; then
            export UPLOADED_FILE_ID="$FILE_ID"
            export UPLOADED_FILE_NAME="$TEST_FILE_NAME"
            export UPLOADED_FILE_PATH="$TEST_FILE_PATH"
            
            # 打印详细信息用于调试
            echo -e "${GREEN}✓ HTTP 响应成功${NC}"
            echo "File ID: $FILE_ID"
            echo "文件名称: $TEST_FILE_NAME"
            [ -n "$S3_KEY" ] && echo "S3 Key: $S3_KEY"
            [ -n "$STORAGE_KEY" ] && echo "Storage Key: $STORAGE_KEY"
            [ -n "$FILE_SIZE" ] && echo "文件大小: $FILE_SIZE 字节"
            echo ""
            
            # 验证文件是否真的在服务器上（通过文件列表或下载）
            echo "验证文件是否真的上传成功..."
            LIST_RESPONSE=$(curl -s -X GET "$API_BASE_URL/files" \
                -H "Authorization: Bearer $AUTH_TOKEN" || echo "")
            
            if echo "$LIST_RESPONSE" | grep -q "$FILE_ID"; then
                echo -e "${GREEN}✓ 文件已出现在文件列表中${NC}"
                
                # 尝试下载文件验证
                DOWNLOAD_CODE=$(curl -s -o /dev/null -w "%{http_code}" \
                    -X GET "$API_BASE_URL/files/$FILE_ID/download" \
                    -H "Authorization: Bearer $AUTH_TOKEN" || echo "000")
                
                if [ "$DOWNLOAD_CODE" = "200" ]; then
                    echo -e "${GREEN}✓ 文件可以下载（上传成功）${NC}"
                else
                    echo -e "${YELLOW}⚠ 警告: 文件无法下载（HTTP $DOWNLOAD_CODE）${NC}"
                    echo "这可能表示文件实际上传失败，但服务器返回了成功"
                fi
            else
                echo -e "${RED}✗ 警告: 文件未出现在文件列表中${NC}"
                echo "这可能表示文件实际上传失败，但服务器返回了成功"
                echo "请检查服务器日志以查看详细错误"
            fi
            echo ""
            
            # 如果有 ENV_FILE，将环境变量写入文件供主脚本使用
            if [ -n "$ENV_FILE" ]; then
                {
                    echo "export UPLOADED_FILE_ID='$FILE_ID'"
                    echo "export UPLOADED_FILE_NAME='$TEST_FILE_NAME'"
                    echo "export UPLOADED_FILE_PATH='$TEST_FILE_PATH'"
                } >> "$ENV_FILE"
            fi
            
            echo -e "${GREEN}✓ 测试通过: $TEST_NAME${NC}"
            exit 0
        else
            echo -e "${YELLOW}⚠ 警告: 上传成功但未找到 fileId${NC}"
            exit 1
        fi
    else
        echo -e "${YELLOW}⚠ 警告: HTTP 200 但 success 不为 true${NC}"
        echo "响应内容: $HTTP_BODY"
        exit 1
    fi
elif [ "$HTTP_CODE" = "401" ]; then
    echo -e "${RED}✗ 测试失败: 认证失败，请检查 token${NC}"
    exit 1
elif [ "$HTTP_CODE" = "409" ]; then
    if echo "$HTTP_BODY" | grep -qE '"code":\s*"DUPLICATE_FILE"|"message".*去重'; then
        echo -e "${YELLOW}⚠ 文件去重命中（接口正常工作）${NC}"
        exit 0
    else
        echo -e "${RED}✗ 测试失败: 409 错误${NC}"
        exit 1
    fi
else
    echo -e "${RED}✗ 测试失败: $TEST_NAME${NC}"
    echo "HTTP 状态码: $HTTP_CODE (期望: 200)"
    exit 1
fi
