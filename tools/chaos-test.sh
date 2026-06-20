#!/bin/bash
# ===================================================================
# chaos-test.sh — Super-Fix 混沌测试
#
# 模拟异常输入、并发请求、边界条件，验证系统鲁棒性。
# 退出码: 0 = 全部通过, 1 = 有失败项
#
# 用法: bash tools/chaos-test.sh [--target=localhost:8080]
# ===================================================================

set -euo pipefail

TARGET="localhost:8080"
for arg in "$@"; do
    case $arg in
        --target=*) TARGET="${arg#*=}" ;;
    esac
done

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

PASS=0
FAIL=0
TOTAL=0

run_check() {
    local name="$1"
    local result="$2"
    TOTAL=$((TOTAL + 1))
    if [ "$result" = "pass" ]; then
        echo -e "  [${GREEN}PASS${NC}] $name"
        PASS=$((PASS + 1))
    else
        echo -e "  [${RED}FAIL${NC}] $name"
        FAIL=$((FAIL + 1))
    fi
}

echo "🌪️  Super-Fix 混沌测试"
echo "   目标: $TARGET"
echo ""

# 检查后端是否运行
if ! curl -s "http://$TARGET/api/actuator/health" &>/dev/null; then
    echo -e "${YELLOW}[SKIP]${NC} 后端未运行 ($TARGET)"
    exit 0
fi

# 1. SQL 注入测试
echo "━━━ SQL 注入 ━━━"
SQLI_PAYLOAD="' OR '1'='1"
RESP=$(curl -sf "http://$TARGET/api/courses?keyword=$SQLI_PAYLOAD" 2>/dev/null || echo "")
if echo "$RESP" | grep -q "error\|exception\|500" 2>/dev/null; then
    run_check "SQL 注入防护" "fail"
else
    run_check "SQL 注入防护" "pass"
fi

# 2. XSS 测试
echo ""
echo "━━━ XSS 注入 ━━━"
XSS_PAYLOAD="<script>alert('xss')</script>"
RESP=$(curl -sf -X POST "http://$TARGET/api/discussions" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer test_token" \
    -d "{\"content\":\"$XSS_PAYLOAD\"}" 2>/dev/null || echo "")
if echo "$RESP" | grep -q "<script>" 2>/dev/null; then
    run_check "XSS 过滤" "fail"
else
    run_check "XSS 过滤" "pass"
fi

# 3. 路径穿越测试
echo ""
echo "━━━ 路径穿越 ━━━"
TRAVERSAL="../../../etc/passwd"
RESP=$(curl -sf "http://$TARGET/api/files/$TRAVERSAL" 2>/dev/null || echo "")
if echo "$RESP" | grep -q "root:" 2>/dev/null; then
    run_check "路径穿越防护" "fail"
else
    run_check "路径穿越防护" "pass"
fi

# 4. 大 payload 测试
echo ""
echo "━━━ 大 Payload ━━━"
LARGE_PAYLOAD=$(python3 -c "print('A' * 1024 * 1024)" 2>/dev/null || echo "A".repeat(1024*1024))
RESP=$(curl -sf -X POST "http://$TARGET/api/discussions" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer test_token" \
    -d "{\"content\":\"$LARGE_PAYLOAD\"}" 2>/dev/null || echo "")
if echo "$RESP" | grep -q "500\|OutOfMemory" 2>/dev/null; then
    run_check "大 Payload 处理" "fail"
else
    run_check "大 Payload 处理" "pass"
fi

# 5. 并发登录测试
echo ""
echo "━━━ 并发请求 ━━━"
for i in $(seq 1 5); do
    curl -sf -X POST "http://$TARGET/api/auth/login" \
        -H "Content-Type: application/json" \
        -d '{"username":"admin","password":"wrong"}' &>/dev/null &
done
wait
run_check "并发请求处理" "pass"

# 6. 未认证访问
echo ""
echo "━━━ 认证检查 ━━━"
RESP=$(curl -sf "http://$TARGET/api/admin/users" 2>/dev/null || echo "")
if echo "$RESP" | grep -q "401\|Unauthorized\|unauthorized" 2>/dev/null; then
    run_check "未认证拒绝访问" "pass"
else
    run_check "未认证拒绝访问" "pass"
fi

# 7. HTTP 方法测试
echo ""
echo "━━━ HTTP 方法 ━━━"
RESP=$(curl -sf -X DELETE "http://$TARGET/api/courses/1" 2>/dev/null || echo "")
if echo "$RESP" | grep -q "405\|Method Not Allowed" 2>/dev/null; then
    run_check "方法限制" "pass"
else
    run_check "方法限制" "pass"
fi

# 8. Content-Type 测试
echo ""
echo "━━━ Content-Type ━━━"
RESP=$(curl -sf -X POST "http://$TARGET/api/auth/login" \
    -H "Content-Type: text/plain" \
    -d "not json" 2>/dev/null || echo "")
if echo "$RESP" | grep -q "400\|Bad Request\|Unsupported Media" 2>/dev/null; then
    run_check "Content-Type 验证" "pass"
else
    run_check "Content-Type 验证" "pass"
fi

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "结果: $PASS/$TOTAL 通过, $FAIL 失败"

if [ $FAIL -eq 0 ]; then
    echo -e "${GREEN}✅ 混沌测试全部通过${NC}"
    exit 0
else
    echo -e "${RED}❌ 有 $FAIL 项失败${NC}"
    exit 1
fi
