#!/bin/bash

# OSS 连接诊断脚本
# 用于检查阿里云 OSS 配置和连接问题

echo "=========================================="
echo "EasyCloudDisk OSS 连接诊断"
echo "=========================================="

# 加载配置
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/config.sh" 2>/dev/null || true

# 使用环境变量或默认值
API_BASE_URL="${API_BASE_URL:-http://localhost:8080}"
OSS_ACCESS_KEY_ID="${OSS_ACCESS_KEY_ID:-LTAI5tDAGmspKbptW5Jo1P8w}"
OSS_ACCESS_KEY_SECRET="${OSS_ACCESS_KEY_SECRET:-请替换为你的真实OSS AccessKey Secret}"
OSS_REGION="${OSS_REGION:-cn-beijing}"
OSS_BUCKET="${OSS_BUCKET:-hppnw}"

echo "配置信息:"
echo "  API_BASE_URL: $API_BASE_URL"
echo "  OSS_REGION: $OSS_REGION"
echo "  OSS_BUCKET: $OSS_BUCKET"
echo "  OSS_ACCESS_KEY_ID: ${OSS_ACCESS_KEY_ID:0:8}..."
echo ""

# 1. 检查服务器健康状态
echo "1. 检查服务器健康状态..."
HEALTH_RESPONSE=$(curl -s -w "\n%{http_code}" "$API_BASE_URL/api/health" 2>/dev/null)

if [ $? -eq 0 ]; then
    HTTP_CODE=$(echo "$HEALTH_RESPONSE" | tail -n 1)
    RESPONSE_BODY=$(echo "$HEALTH_RESPONSE" | sed '$d')

    if [ "$HTTP_CODE" -eq 200 ]; then
        echo "✓ 服务器响应正常"
        echo "  响应: $RESPONSE_BODY"

        # 解析健康状态
        DATABASE_STATUS=$(echo "$RESPONSE_BODY" | grep -o '"database":[^,}]*' | cut -d':' -f2)
        STORAGE_STATUS=$(echo "$RESPONSE_BODY" | grep -o '"storage":[^,}]*' | cut -d':' -f2)

        echo "  数据库: $DATABASE_STATUS"
        echo "  存储: $STORAGE_STATUS"

        if [ "$STORAGE_STATUS" = "true" ]; then
            echo "✓ OSS 连接正常"
        else
            echo "✗ OSS 连接失败"
        fi
    else
        echo "✗ 服务器响应错误 (HTTP $HTTP_CODE)"
        echo "  响应: $RESPONSE_BODY"
    fi
else
    echo "✗ 无法连接到服务器: $API_BASE_URL"
fi

echo ""

# 2. 检查 OSS CLI（如果安装了）
echo "2. 检查 OSS CLI..."
if command -v ossutil >/dev/null 2>&1; then
    echo "✓ OSS CLI 已安装"

    # 配置 OSS CLI
    export OSS_ACCESS_KEY_ID
    export OSS_ACCESS_KEY_SECRET
    export OSS_REGION

    echo "  测试 OSS 连接..."

    # 测试列出 buckets
    if ossutil ls >/dev/null 2>&1; then
        echo "✓ OSS 凭证有效"

        # 检查 bucket 存在
        if ossutil ls "oss://$OSS_BUCKET" >/dev/null 2>&1; then
            echo "✓ OSS bucket '$OSS_BUCKET' 存在且可访问"
        else
            echo "✗ OSS bucket '$OSS_BUCKET' 不存在或无权限访问"
            echo "  请检查 bucket 名称和权限设置"
        fi
    else
        echo "✗ OSS 凭证无效或网络连接问题"
        echo "  错误详情:"
        ossutil ls 2>&1 | head -5
    fi
else
    echo "⚠ OSS CLI 未安装，跳过本地测试"
    echo "  安装命令: 下载并安装阿里云 OSS CLI"
fi

echo ""

# 3. 诊断建议
echo "3. 诊断建议:"
echo ""

if [ "$STORAGE_STATUS" = "false" ]; then
    echo "OSS 连接问题排查步骤:"
    echo ""
    echo "1. 检查 OSS 凭证:"
    echo "   - 确认 Access Key ID 和 Secret Access Key 正确"
    echo "   - 检查凭证是否过期"
    echo "   - 确认用户有 OSS 权限"
    echo ""
    echo "2. 检查 OSS Bucket:"
    echo "   - 确认 bucket 名称正确"
    echo "   - 确认 bucket 在指定区域 ($OSS_REGION)"
    echo "   - 检查 bucket 权限设置"
    echo ""
    echo "3. 检查网络连接:"
    echo "   - 确认服务器可以访问阿里云 OSS 端点"
    echo "   - 检查防火墙和代理设置"
    echo ""
    echo "4. 测试命令:"
    echo "   OSS_ACCESS_KEY_ID=$OSS_ACCESS_KEY_ID \\"
    echo "   OSS_ACCESS_KEY_SECRET=$OSS_ACCESS_KEY_SECRET \\"
    echo "   ossutil ls --region $OSS_REGION"
    echo ""
    echo "5. 或者使用 MinIO 本地测试:"
    echo "   - 设置 OSS_ENDPOINT=http://localhost:9000"
    echo "   - 启动本地 MinIO 服务器进行测试"
else
    echo "✓ OSS 配置正常，无需修复"
fi

echo ""
echo "=========================================="