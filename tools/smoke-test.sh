#!/bin/bash
# ===================================================================
# smoke-test.sh — 微课平台 API 烟雾测试
#
# 验证关键 API 端点是否正常响应，覆盖 4 种用户角色。
# 用法: bash tools/smoke-test.sh [--verbose]
# ===================================================================

set -euo pipefail

BASE_URL="${API_BASE_URL:-http://localhost:8080}"
VERBOSE=false
[[ "${1:-}" == "--verbose" ]] && VERBOSE=true

PASS=0
FAIL=0
TOTAL=0

GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

check() {
    local name="$1"
    local result="$2"
    TOTAL=$((TOTAL + 1))
    if [ "$result" = "true" ]; then
        echo -e "  [${GREEN}PASS${NC}] $name"
        PASS=$((PASS + 1))
    else
        echo -e "  [${RED}FAIL${NC}] $name"
        FAIL=$((FAIL + 1))
        FAILED_NAMES+=("$name")
    fi
}

echo "========================================"
echo "  微课平台 API 烟雾测试"
echo "  URL: $BASE_URL"
echo "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "========================================"
echo ""

# ---- 1. 登录测试 ----
echo "[1] 登录认证"
for cred in "admin:admin123:管理员" "teacher:123456:教师" "student:123456:学生" "academic:123456:教务处"; do
    USER=$(echo "$cred" | cut -d: -f1)
    PASS_WORD=$(echo "$cred" | cut -d: -f2)
    ROLE=$(echo "$cred" | cut -d: -f3)
    RESP=$(curl -s -X POST "$BASE_URL/api/auth/login" -H "Content-Type: application/json" \
        -d "{\"username\":\"$USER\",\"password\":\"$PASS_WORD\"}" 2>/dev/null || echo "{\"code\":0}")
    CODE=$(echo "$RESP" | python3 -c "import sys,json;print(json.load(sys.stdin).get('code',0))" 2>/dev/null || echo "0")
    TOKEN=$(echo "$RESP" | python3 -c "import sys,json;print(json.load(sys.stdin).get('data',{}).get('accessToken',''))" 2>/dev/null || echo "")
    if [ "$CODE" = "200" ] && [ -n "$TOKEN" ]; then
        check "$ROLE($USER) 登录 → 200" "true"
        # 存储 token 供后续测试
        eval "TOKEN_${USER}=\$TOKEN"
    else
        check "$ROLE($USER) 登录 → $CODE" "false"
    fi
done

# ---- 2. 公开 API ----
echo ""
echo "[2] 公开/认证 API"
TOKEN_ADMIN="${TOKEN_admin:-}"
if [ -n "$TOKEN_ADMIN" ]; then
    for spec in \
        "GET:/api/courses?page=0&size=5:课程列表" \
        "GET:/api/course-categories?size=1000:课程分类" \
        "GET:/api/chapters?courseId=1&size=1000:章节列表"; do
        METHOD=$(echo "$spec" | cut -d: -f1)
        PATH_URL=$(echo "$spec" | cut -d: -f2)
        NAME=$(echo "$spec" | cut -d: -f3)
        HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X "$METHOD" "$BASE_URL$PATH_URL" \
            -H "Authorization: Bearer $TOKEN_ADMIN" 2>/dev/null || echo "000")
        if [ "$HTTP_CODE" = "200" ]; then
            check "$NAME → $HTTP_CODE" "true"
        else
            check "$NAME → $HTTP_CODE" "false"
        fi
    done

    # 管理员专用 API
    for spec in \
        "GET:/api/admin/settings/cas:CAS设置" \
        "GET:/api/admin/stats/health:健康检查" \
        "GET:/api/users?page=0&size=5:用户列表"; do
        METHOD=$(echo "$spec" | cut -d: -f1)
        PATH_URL=$(echo "$spec" | cut -d: -f2)
        NAME=$(echo "$spec" | cut -d: -f3)
        HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X "$METHOD" "$BASE_URL$PATH_URL" \
            -H "Authorization: Bearer $TOKEN_ADMIN" 2>/dev/null || echo "000")
        if [ "$HTTP_CODE" = "200" ] || [ "$HTTP_CODE" = "403" ]; then
            check "$NAME → $HTTP_CODE" "true"
        else
            check "$NAME → $HTTP_CODE" "false"
        fi
    done
fi

# ---- 3. 不同角色权限验证 ----
echo ""
echo "[3] 角色权限"
TOKEN_STUDENT="${TOKEN_student:-}"
if [ -n "$TOKEN_STUDENT" ]; then
    # 学生访问管理端 API 应返回 403
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/admin/settings/cas" \
        -H "Authorization: Bearer $TOKEN_STUDENT" 2>/dev/null || echo "000")
    if [ "$HTTP_CODE" = "403" ]; then
        check "学生无权访问管理端 → 403" "true"
    else
        check "学生无权访问管理端 → $HTTP_CODE" "false"
    fi
fi

# ---- 4. 幻灯片上传 ----
echo ""
echo "[4] 幻灯片上传"
TOKEN_TEACHER="${TOKEN_teacher:-}"
if [ -n "$TOKEN_TEACHER" ]; then
    # 先用学生 token 测试权限
    if [ -n "$TOKEN_STUDENT" ]; then
        HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/courses/1/slides/upload" \
            -H "Authorization: Bearer $TOKEN_STUDENT" -F "file=@/dev/null" 2>/dev/null || echo "000")
        if [ "$HTTP_CODE" = "403" ]; then
            check "学生无权上传课件 → 403" "true"
        else
            check "学生无权上传课件 → $HTTP_CODE" "false"
        fi
    fi

    # 教师上传（用最小 valid PPTX）
    if python3 -c "
import zipfile, io
buf = io.BytesIO()
with zipfile.ZipFile(buf, 'w') as z:
    z.writestr('[Content_Types].xml', '<?xml version=\"1.0\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"/>')
    z.writestr('ppt/presentation.xml', '<?xml version=\"1.0\"?><p:presentation xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\"/>')
    z.writestr('_rels/.rels', '<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"/>')
with open('/tmp/smoke_test.pptx', 'wb') as f:
    f.write(buf.getvalue())
print('ok')
" 2>/dev/null; then
        HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/courses/1/slides/upload" \
            -H "Authorization: Bearer $TOKEN_TEACHER" -F "file=@/tmp/smoke_test.pptx" 2>/dev/null || echo "000")
        if [ "$HTTP_CODE" = "200" ] || [ "$HTTP_CODE" = "400" ]; then
            check "教师上传课件 → $HTTP_CODE" "true"
        else
            check "教师上传课件 → $HTTP_CODE" "false"
        fi
    fi
fi

# ---- 5. 讲述稿设置 ----
echo ""
echo "[5] 讲述稿设置"
if [ -n "$TOKEN_TEACHER" ]; then
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/courses/1/narration-settings" \
        -H "Authorization: Bearer $TOKEN_TEACHER" 2>/dev/null || echo "000")
    check "讲述稿设置 → $HTTP_CODE" "$([ "$HTTP_CODE" = "200" ] && echo "true" || echo "false")"
fi

# ---- 结果汇总 ----
echo ""
echo "========================================"
echo "  烟雾测试完成: $PASS/$TOTAL 通过, $FAIL 失败"
echo "========================================"
for name in "${FAILED_NAMES[@]:-}"; do
    echo "  ❌ $name"
done

[ "$FAIL" -eq 0 ] && exit 0 || exit 1
