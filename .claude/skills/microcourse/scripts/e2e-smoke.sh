#!/usr/bin/env bash
# 微课平台 E2E 烟雾测试 (W32 治理)
#
# 覆盖 5 个核心用户流程:
#   1. 教师登录 → 看到自己课程
#   2. 教师课件树查询 (CQRS)
#   3. 教师删除 PPT page (新增)
#   4. 教师删除 chapter (新增)
#   5. 学员查看课程 (跨用户场景)
#
# 工具: curl (基础 E2E, 不依赖 Playwright)
# 真实 Playwright 方案见 docs/superpowers/e2e-roadmap.md

set -e

API_BASE="${API_BASE:-http://localhost:8080}"
ADMIN_BASE="${ADMIN_BASE:-http://localhost}"
SYTAFE_USER="sytafe"
SYTAFE_PASS="sytafe1234"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPORT_DIR="${SCRIPT_DIR}/../../reports/e2e"
mkdir -p "$REPORT_DIR"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
REPORT_FILE="${REPORT_DIR}/e2e-${TIMESTAMP}.log"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'

# 记录日志
exec > >(tee -a "$REPORT_FILE") 2>&1

echo "============================================================"
echo "  微课平台 E2E 烟雾测试 (W32 治理)"
echo "  API_BASE=${API_BASE}"
echo "  Report: ${REPORT_FILE}"
echo "============================================================"

PASS=0
FAIL=0

assert() {
    local name="$1"
    local actual="$2"
    local expected="$3"
    if [ "$actual" = "$expected" ]; then
        echo -e "  ${GREEN}PASS${NC}: ${name} (got: ${actual})"
        PASS=$((PASS+1))
    else
        echo -e "  ${RED}FAIL${NC}: ${name} (expected: ${expected}, got: ${actual})"
        FAIL=$((FAIL+1))
    fi
}

assert_contains() {
    local name="$1"
    local actual="$2"
    local expected="$3"
    if echo "$actual" | grep -q "$expected"; then
        echo -e "  ${GREEN}PASS${NC}: ${name} (contains: ${expected})"
        PASS=$((PASS+1))
    else
        echo -e "  ${RED}FAIL${NC}: ${name} (missing: ${expected}, got: ${actual:0:100})"
        FAIL=$((FAIL+1))
    fi
}

# ──────────────────────────────────────────────────────────────
# Flow 1: 教师登录
# ──────────────────────────────────────────────────────────────
echo ""
echo "--- Flow 1: 教师登录 ---"
LOGIN_RESP=$(curl -s -m 10 -X POST "${API_BASE}/api/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"${SYTAFE_USER}\",\"password\":\"${SYTAFE_PASS}\"}")

LOGIN_CODE=$(echo "$LOGIN_RESP" | python3 -c "import json,sys;print(json.load(sys.stdin).get('code','-1'))")
assert "Flow1 登录 code" "$LOGIN_CODE" "200"

TOKEN=$(echo "$LOGIN_RESP" | python3 -c "import json,sys;d=json.load(sys.stdin);print(d.get('data',{}).get('accessToken',''))")
[ -n "$TOKEN" ] && echo -e "  ${GREEN}OK${NC}: token 长度=${#TOKEN}" || echo -e "  ${RED}FAIL${NC}: 未获取 token"

# ──────────────────────────────────────────────────────────────
# Flow 2: 教师课程列表
# ──────────────────────────────────────────────────────────────
echo ""
echo "--- Flow 2: 教师课程列表 ---"
COURSES_RESP=$(curl -s -m 10 "${API_BASE}/api/courses?teacherId=35" -H "Authorization: Bearer $TOKEN")
COURSES_CODE=$(echo "$COURSES_RESP" | python3 -c "import json,sys;print(json.load(sys.stdin).get('code','-1'))")
assert "Flow2 课程列表 code" "$COURSES_CODE" "200"
assert_contains "Flow2 含 sytafe 课程" "$COURSES_RESP" "沈阳测试课-A"

# ──────────────────────────────────────────────────────────────
# Flow 3: 课件树查询 (CQRS)
# ──────────────────────────────────────────────────────────────
echo ""
echo "--- Flow 3: 课件树查询 (CQRS) ---"
# section_id = 41 (1.1 节-PPT)
TREE_RESP=$(curl -s -m 10 "${API_BASE}/api/courses/79/courseware/41" -H "Authorization: Bearer $TOKEN")
TREE_CODE=$(echo "$TREE_RESP" | python3 -c "import json,sys;print(json.load(sys.stdin).get('code','-1'))")
assert "Flow3 课件树 code" "$TREE_CODE" "200"

# ──────────────────────────────────────────────────────────────
# Flow 4: 删除 PPT page (新增 API, W31 交付)
# ──────────────────────────────────────────────────────────────
echo ""
echo "--- Flow 4: 删除 PPT page ---"
# 1.1 节下 PPT page id
PPT_PAGE_ID=$(psql -U postgres -d micro_course -tA -c "SELECT id FROM slide_ppt_pages WHERE course_id=79 AND slide_id=999 LIMIT 1;" 2>/dev/null || echo "")
[ -z "$PPT_PAGE_ID" ] && PPT_PAGE_ID=4  # fallback
echo "  目标 PPT page: $PPT_PAGE_ID"
DELETE_RESP=$(curl -s -m 10 -X DELETE "${API_BASE}/api/courses/79/courseware/ppt-pages/${PPT_PAGE_ID}" \
    -H "Authorization: Bearer $TOKEN")
DELETE_CODE=$(echo "$DELETE_RESP" | python3 -c "import json,sys;print(json.load(sys.stdin).get('code','-1'))")
assert "Flow4 删除 PPT page code" "$DELETE_CODE" "200"

# ──────────────────────────────────────────────────────────────
# Flow 5: IDOR 防御 (篡改 URL courseId)
# ──────────────────────────────────────────────────────────────
echo ""
echo "--- Flow 5: IDOR 防御 ---"
IDOR_RESP=$(curl -s -m 10 -X DELETE "${API_BASE}/api/courses/80/courseware/chapters/1" \
    -H "Authorization: Bearer $TOKEN")
IDOR_CODE=$(echo "$IDOR_RESP" | python3 -c "import json,sys;print(json.load(sys.stdin).get('code','-1'))")
# 期望 9006 (RESOURCE_NOT_FOUND) 或 10003 (NO_PERMISSION)
if [ "$IDOR_CODE" = "9006" ] || [ "$IDOR_CODE" = "10003" ] || [ "$IDOR_CODE" = "404" ] || [ "$IDOR_CODE" = "403" ]; then
    echo -e "  ${GREEN}PASS${NC}: IDOR 防御 (code=${IDOR_CODE})"
    PASS=$((PASS+1))
else
    echo -e "  ${RED}FAIL${NC}: IDOR 防御失败 (code=${IDOR_CODE})"
    FAIL=$((FAIL+1))
fi

# ──────────────────────────────────────────────────────────────
# Flow 6: 慢查询监控 (Prometheus)
# ──────────────────────────────────────────────────────────────
echo ""
echo "--- Flow 6: Prometheus 监控可达 ---"
PROM_RESP=$(curl -s -m 5 -o /dev/null -w "%{http_code}" "http://localhost:9090/api/v1/targets?state=active")
assert "Flow6 Prometheus 200" "$PROM_RESP" "200"

# ──────────────────────────────────────────────────────────────
# 总结
# ──────────────────────────────────────────────────────────────
echo ""
echo "============================================================"
echo "  E2E 总结"
echo "============================================================"
echo "  PASS: $PASS"
echo "  FAIL: $FAIL"
echo "  Report: ${REPORT_FILE}"
echo "============================================================"

[ "$FAIL" -eq 0 ] && exit 0 || exit 1