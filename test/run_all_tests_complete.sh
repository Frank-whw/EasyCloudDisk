#!/bin/bash

# 杩愯鎵€鏈夊崟鍏冩祴璇曞拰闆嗘垚娴嬭瘯
# 鍖呮嫭 Java 鍗曞厓娴嬭瘯 + Bash 闆嗘垚娴嬭瘯

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

echo "=========================================="
echo "EasyCloudDisk 瀹屾暣娴嬭瘯濂椾欢"
echo "=========================================="
echo ""

# 棰滆壊瀹氫箟
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 璁℃暟鍣?
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

# 娴嬭瘯缁撴灉鏁扮粍
declare -a TEST_RESULTS

# ==========================================
# 1. Java 鍗曞厓娴嬭瘯
# ==========================================
echo "=========================================="
echo "绗竴閮ㄥ垎: Java 鍗曞厓娴嬭瘯"
echo "=========================================="
echo ""

cd "$PROJECT_ROOT/server" || exit 1

echo "杩愯 Maven 娴嬭瘯..."
echo ""

if mvn test -q; then
    echo -e "${GREEN}鉁?Java 鍗曞厓娴嬭瘯鍏ㄩ儴閫氳繃${NC}"
    JAVA_TESTS_PASSED=true
    TEST_RESULTS+=("Java鍗曞厓娴嬭瘯|PASSED")
else
    echo -e "${RED}鉁?Java 鍗曞厓娴嬭瘯澶辫触${NC}"
    JAVA_TESTS_PASSED=false
    TEST_RESULTS+=("Java鍗曞厓娴嬭瘯|FAILED")
fi

echo ""
echo "鏌ョ湅璇︾粏娴嬭瘯鎶ュ憡:"
echo "  cd server && mvn surefire-report:report"
echo "  鎶ュ憡浣嶇疆: server/target/surefire-reports/"
echo ""

# ==========================================
# 2. Bash 闆嗘垚娴嬭瘯
# ==========================================
echo "=========================================="
echo "绗簩閮ㄥ垎: API 闆嗘垚娴嬭瘯"
echo "=========================================="
echo ""

# 妫€鏌ユ湇鍔″櫒鏄惁杩愯
API_BASE_URL="${API_BASE_URL:-http://localhost:8080}"
echo "妫€鏌ユ湇鍔″櫒杩炴帴: $API_BASE_URL/health"

if ! curl -s -f "$API_BASE_URL/health" > /dev/null 2>&1; then
    echo -e "${RED}鉁?鏈嶅姟鍣ㄦ湭杩愯锛岃鍏堝惎鍔ㄦ湇鍔″櫒${NC}"
    echo ""
    echo "鍚姩鍛戒护:"
    echo "  cd server && mvn spring-boot:run"
    echo ""
    exit 1
fi

echo -e "${GREEN}鉁?鏈嶅姟鍣ㄨ繍琛屾甯?{NC}"
echo ""

# 杩涘叆娴嬭瘯鐩綍
cd "$SCRIPT_DIR" || exit 1

# 瀹氫箟娴嬭瘯鑴氭湰鍒楄〃锛堟寜鎵ц椤哄簭锛?
TESTS=(
    "test_health.sh|鍋ュ悍妫€鏌?
    "test_auth_register.sh|鐢ㄦ埛娉ㄥ唽"
    "test_auth_login.sh|鐢ㄦ埛鐧诲綍"
    "test_file_upload.sh|鏂囦欢涓婁紶"
    "test_file_list.sh|鏂囦欢鍒楄〃"
    "test_file_download.sh|鏂囦欢涓嬭浇"
    "test_file_delete.sh|鏂囦欢鍒犻櫎"
    "test_quick_upload.sh|鏂囦欢绾у幓閲嶏紙绉掍紶锛?
    "test_chunk_deduplication.sh|鍧楃骇鍘婚噸"
    "test_resumable_upload.sh|鏂偣缁紶"
    "test_resumable_sessions.sh|鏂偣缁紶浼氳瘽"
    "test_delta_sync.sh|宸垎鍚屾"
    "test_encryption.sh|鏁版嵁鍔犲瘑"
    "test_file_versions.sh|鏂囦欢鐗堟湰鎺у埗"
    "test_collaboration_share.sh|鏂囦欢鍏变韩涓庡崗鍚?
)

echo "寮€濮嬭繍琛?API 闆嗘垚娴嬭瘯..."
echo ""

# 杩愯姣忎釜娴嬭瘯
for test_entry in "${TESTS[@]}"; do
    IFS='|' read -r test_script test_name <<< "$test_entry"
    
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    
    echo "----------------------------------------------"
    echo "[$TOTAL_TESTS/${#TESTS[@]}] 娴嬭瘯: $test_name"
    echo "----------------------------------------------"
    
    if [ -f "$test_script" ]; then
        # 杩愯娴嬭瘯
        if bash "$test_script"; then
            echo -e "${GREEN}鉁?$test_name - 閫氳繃${NC}"
            PASSED_TESTS=$((PASSED_TESTS + 1))
            TEST_RESULTS+=("$test_name|PASSED")
        else
            echo -e "${RED}鉁?$test_name - 澶辫触${NC}"
            FAILED_TESTS=$((FAILED_TESTS + 1))
            TEST_RESULTS+=("$test_name|FAILED")
        fi
    else
        echo -e "${YELLOW}鈿?娴嬭瘯鑴氭湰涓嶅瓨鍦? $test_script${NC}"
        FAILED_TESTS=$((FAILED_TESTS + 1))
        TEST_RESULTS+=("$test_name|SKIPPED")
    fi
    
    echo ""
done

# ==========================================
# 3. 鐢熸垚娴嬭瘯鎶ュ憡
# ==========================================
echo ""
echo "=========================================="
echo "娴嬭瘯鎶ュ憡"
echo "=========================================="
echo ""

# Java 鍗曞厓娴嬭瘯缁撴灉
echo "Java 鍗曞厓娴嬭瘯:"
if [ "$JAVA_TESTS_PASSED" = true ]; then
    echo -e "  ${GREEN}鉁?閫氳繃${NC}"
else
    echo -e "  ${RED}鉁?澶辫触${NC}"
fi
echo ""

# API 闆嗘垚娴嬭瘯缁撴灉
echo "API 闆嗘垚娴嬭瘯缁撴灉:"
echo "  鎬昏: $TOTAL_TESTS"
echo -e "  ${GREEN}閫氳繃: $PASSED_TESTS${NC}"
if [ $FAILED_TESTS -gt 0 ]; then
    echo -e "  ${RED}澶辫触: $FAILED_TESTS${NC}"
else
    echo -e "  澶辫触: $FAILED_TESTS"
fi
echo ""

# 璇︾粏缁撴灉琛ㄦ牸
echo "璇︾粏缁撴灉:"
echo "----------------------------------------------"
printf "%-40s | %s\n" "娴嬭瘯椤? "鐘舵€?
echo "----------------------------------------------"

for result in "${TEST_RESULTS[@]}"; do
    IFS='|' read -r test_name status <<< "$result"
    
    if [ "$status" = "PASSED" ]; then
        printf "%-40s | ${GREEN}鉁?閫氳繃${NC}\n" "$test_name"
    elif [ "$status" = "FAILED" ]; then
        printf "%-40s | ${RED}鉁?澶辫触${NC}\n" "$test_name"
    else
        printf "%-40s | ${YELLOW}鈿?璺宠繃${NC}\n" "$test_name"
    fi
done

echo "----------------------------------------------"
echo ""

# 淇濆瓨鏂囨湰鎶ュ憡
REPORT_FILE="$SCRIPT_DIR/test_report_$(date +%Y%m%d_%H%M%S).txt"
{
    echo "EasyCloudDisk 娴嬭瘯鎶ュ憡"
    echo "鐢熸垚鏃堕棿: $(date)"
    echo ""
    echo "Java 鍗曞厓娴嬭瘯: $( [ "$JAVA_TESTS_PASSED" = true ] && echo "閫氳繃" || echo "澶辫触" )"
    echo ""
    echo "API 闆嗘垚娴嬭瘯:"
    echo "  鎬昏: $TOTAL_TESTS"
    echo "  閫氳繃: $PASSED_TESTS"
    echo "  澶辫触: $FAILED_TESTS"
    echo ""
    echo "璇︾粏缁撴灉:"
    for result in "${TEST_RESULTS[@]}"; do
        echo "  $result"
    done
} > "$REPORT_FILE"

echo "娴嬭瘯鎶ュ憡宸蹭繚瀛? $REPORT_FILE"
echo ""

# 杩斿洖閫€鍑虹爜
if [ "$JAVA_TESTS_PASSED" = false ] || [ $FAILED_TESTS -gt 0 ]; then
    echo -e "${RED}鉁?娴嬭瘯澶辫触${NC}"
    exit 1
else
    echo -e "${GREEN}鉁?鎵€鏈夋祴璇曢€氳繃锛?{NC}"
    exit 0
fi
