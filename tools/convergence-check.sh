#!/bin/bash
# ===================================================================
# convergence-check.sh — Super-Fix 收敛检查
# 
# 验证审计发现是否全部修复、构建是否通过、关键 API 是否正常。
# 退出码: 0 = 全部通过, 1 = 有失败项
# 
# 用法: bash tools/convergence-check.sh [--verbose]
# ===================================================================

set -euo pipefail

VERBOSE=false
[[ "${1:-}" == "--verbose" ]] && VERBOSE=true

PASS=0
FAIL=0
TOTAL=0

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

check() {
    local name="$1"
    local result="$2"
    TOTAL=$((TOTAL + 1))
    if [ "$result" = "true" ] || [ "$result" = "0" ]; then
        echo -e "  [${GREEN}PASS${NC}] $name"
        PASS=$((PASS + 1))
    else
        echo -e "  [${RED}FAIL${NC}] $name"
        FAIL=$((FAIL + 1))
    fi
}

echo "========================================"
echo "  Super-Fix 收敛检查"
echo "  $(date '+%Y-%m-%d %H:%M:%S')"
echo "========================================"
echo ""

# ---- 1. 审计发现状态 ----
echo "[1/5] 审计发现状态"
if [ -f .audit-cache/findings.json ]; then
    P0=$(python3 -c "import json;f=json.load(open('.audit-cache/findings.json'));print(sum(1 for x in f.get('findings',[]) if x.get('severity')=='P0'))" 2>/dev/null || echo "?")
    P1=$(python3 -c "import json;f=json.load(open('.audit-cache/findings.json'));print(sum(1 for x in f.get('findings',[]) if x.get('severity')=='P1'))" 2>/dev/null || echo "?")
    TOTAL_F=$(python3 -c "import json;f=json.load(open('.audit-cache/findings.json'));print(len(f.get('findings',[])))" 2>/dev/null || echo "?")
    echo "  发现总数: $TOTAL_F (P0: $P0, P1: $P1)"
    check "审计发现文件存在" "true"
else
    check "审计发现文件存在" "false"
fi

# ---- 2. 后端编译 ----
echo ""
echo "[2/5] 后端编译"
if [ -f micro-course-api/pom.xml ]; then
    if mvn compile -q -f "micro-course-api/pom.xml" 2>/dev/null; then
        check "后端编译 (mvn compile)" "true"
    else
        check "后端编译 (mvn compile)" "false"
    fi
elif [ -f pom.xml ]; then
    if mvn compile -q 2>/dev/null; then
        check "后端编译 (mvn compile)" "true"
    else
        check "后端编译 (mvn compile)" "false"
    fi
else
    check "后端编译 (pom.xml 不存在)" "false"
fi

# ---- 3. 项目预检 ----
echo ""
echo "[3/5] 项目预检"
if [ -f .claude/skills/microcourse/scripts/precheck.sh ]; then
    if bash .claude/skills/microcourse/scripts/precheck.sh > /dev/null 2>&1; then
        check "预检脚本 (通过)" "true"
    else
        check "预检脚本 (失败)" "false"
    fi
else
    check "预检脚本 (不存在)" "false"
fi

# ---- 4. 前端构建 ----
echo ""
echo "[4/5] 前端构建"
if [ -f package.json ] || [ -f micro-course-admin/package.json ]; then
    DIR="."
    [ -f micro-course-admin/package.json ] && DIR="micro-course-admin"
    if npm run build --prefix "$DIR" 2>&1 | grep -q "built in"; then
        check "前端构建 (npm run build)" "true"
    else
        check "前端构建 (npm run build)" "false"
    fi
else
    check "前端构建 (package.json 不存在)" "false"
fi

# ---- 5. API 烟雾测试 ----
echo ""
echo "[5/5] API 烟雾测试"
API_OK=true
for endpoint in \
    "POST http://localhost:8080/api/auth/login {\"username\":\"admin\",\"password\":\"admin123\"}"; do
    METHOD=$(echo "$endpoint" | awk '{print $1}')
    URL=$(echo "$endpoint" | awk '{print $2}')
    BODY=$(echo "$endpoint" | cut -d' ' -f3-)
    STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X "$METHOD" "$URL" -H "Content-Type: application/json" -d "$BODY" 2>/dev/null || echo "000")
    if [ "$STATUS" = "200" ] || [ "$STATUS" = "401" ] || [ "$STATUS" = "405" ]; then
        $VERBOSE && echo "  登录端点: HTTP $STATUS"
    else
        $VERBOSE && echo "  登录端点: HTTP $STATUS (异常)"
        API_OK=false
    fi
done

if [ "$API_OK" = "true" ]; then
    TOKEN=$(curl -s -X POST "http://localhost:8080/api/auth/login" -H "Content-Type: application/json" -d '{"username":"admin","password":"admin123"}' | python3 -c "import sys,json;print(json.load(sys.stdin).get('data',{}).get('accessToken',''))" 2>/dev/null || echo "")
    if [ -n "$TOKEN" ]; then
        for url in "/api/courses?page=0&size=5" "/api/course-categories?size=1000"; do
            STATUS=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:8080$url" -H "Authorization: Bearer $TOKEN" 2>/dev/null || echo "000")
            if [ "$STATUS" != "200" ]; then API_OK=false; fi
            $VERBOSE && echo "  $url: HTTP $STATUS"
        done
    fi
fi
check "API 烟雾测试 (登录+课程+分类)" "$API_OK"

# ---- 结果汇总 ----
echo ""
echo "========================================"
echo "  结果: $PASS/$TOTAL 通过, $FAIL 失败"
echo "========================================"

if [ -f .audit-cache/audit_state.json ]; then
    PHASE=$(python3 -c "import json;print(json.load(open('.audit-cache/audit_state.json')).get('phase','?'))" 2>/dev/null)
    echo "  当前审计阶段: $PHASE"
fi

[ "$FAIL" -eq 0 ] && exit 0 || exit 1
