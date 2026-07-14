#!/bin/bash
# 端到端测试 — 验证4条学生核心流程
# Usage: bash scripts/e2e-test.sh
set -e
BASE="http://localhost:8080"
PASS=0; FAIL=0

ok() { PASS=$((PASS+1)); echo "  ✅ $1"; }
fail() { FAIL=$((FAIL+1)); echo "  ❌ $1"; }

echo "========================================="
echo " 微课平台 端到端测试"
echo "========================================="

# 1. Login
echo ""
echo "--- Flow A: 课程发现 → 购买 ---"
LOGIN_RESP=$(curl -s -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' 2>/dev/null)
TOKEN=$(echo "$LOGIN_RESP" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("data",{}).get("accessToken",""))' 2>/dev/null || echo "")
[ -n "$TOKEN" ] && ok "Admin login" || { echo "  login resp: $LOGIN_RESP"; fail "Admin login"; }

# 2. Browse courses
CCODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/courses?page=0&size=5" -H "Authorization: Bearer $TOKEN")
[ "$CCODE" = "200" ] && ok "Browse courses (200)" || fail "Browse courses ($CCODE)"

# 3. Course detail
DETAIL_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/courses/1" -H "Authorization: Bearer $TOKEN")
[ "$DETAIL_CODE" = "200" ] && ok "Course detail (200)" || fail "Course detail ($DETAIL_CODE)"

# 4. Enrollments
ENR_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/enrollments" -H "Authorization: Bearer $TOKEN")
[ "$ENR_CODE" = "200" ] && ok "Enrollments list (200)" || fail "Enrollments list ($ENR_CODE)"

# 5. Orders (admin sees empty list)
ORD_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/api/orders" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"courseId":1}' 2>/dev/null || echo "500")
[ "$ORD_CODE" != "500" ] && ok "Create order (not 500)" || fail "Create order ($ORD_CODE)"

echo ""
echo "--- Flow B: 教学管理 ---"

# 6. Teaching classes
TCH_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/teaching-classes?page=0&size=5" -H "Authorization: Bearer $TOKEN")
[ "$TCH_CODE" = "200" ] && ok "Teaching classes (200)" || fail "Teaching classes ($TCH_CODE)"

# 7. Grades
GRD_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/grades" -H "Authorization: Bearer $TOKEN")
[ "$GRD_CODE" = "200" ] && ok "Grades list (200)" || fail "Grades list ($GRD_CODE)"

echo ""
echo "--- Flow C: 内容管理 ---"

# 8. Chapters
CHP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/chapters?page=0&size=5" -H "Authorization: Bearer $TOKEN")
[ "$CHP_CODE" = "200" ] && ok "Chapters list (200)" || fail "Chapters list ($CHP_CODE)"

# 9. Videos
VID_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/videos?page=0&size=5" -H "Authorization: Bearer $TOKEN")
[ "$VID_CODE" = "200" ] && ok "Videos list (200)" || fail "Videos list ($VID_CODE)"

# 10. Questions
QST_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/questions?page=0&size=5" -H "Authorization: Bearer $TOKEN")
[ "$QST_CODE" = "200" ] && ok "Questions list (200)" || fail "Questions list ($QST_CODE)"

echo ""
echo "--- Flow D: 互动管理 ---"

# 11. Discussions
DSC_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/discussions" -H "Authorization: Bearer $TOKEN")
[ "$DSC_CODE" = "200" ] && ok "Discussions (200)" || fail "Discussions ($DSC_CODE)"

# 12. Reviews
REV_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/reviews" -H "Authorization: Bearer $TOKEN")
[ "$REV_CODE" = "200" ] && ok "Reviews (200)" || fail "Reviews ($REV_CODE)"

# 13. Notifications
NOT_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/notifications" -H "Authorization: Bearer $TOKEN")
[ "$NOT_CODE" = "200" ] && ok "Notifications (200)" || fail "Notifications ($NOT_CODE)"

# 14. Admin stats
STA_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/admin/stats/overview" -H "Authorization: Bearer $TOKEN")
[ "$STA_CODE" = "200" ] && ok "Admin stats (200)" || fail "Admin stats ($STA_CODE)"

# 15. Operation logs
LOG_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/operation-logs" -H "Authorization: Bearer $TOKEN")
[ "$LOG_CODE" = "200" ] && ok "Operation logs (200)" || fail "Operation logs ($LOG_CODE)"

echo ""
echo "========================================="
echo " 结果: $PASS 通过 / $FAIL 失败 / $((PASS+FAIL)) 总计"
echo "========================================="
[ "$FAIL" = "0" ] && exit 0 || exit 1
