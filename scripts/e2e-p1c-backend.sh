#!/bin/bash
# Round 5 P1-C 后端 E2E 回归测试
# 覆盖 3 条后端相关 P1-C 修复：
#   - R3-A3-003: summary 字段 @Size(max=300) 修复
#   - P0-3: BannerPublicController 公开 API
#
# Usage: bash scripts/e2e-p1c-backend.sh

BASE="${BASE_URL:-http://localhost:8080}"
PASS=0; FAIL=0

ok() { PASS=$((PASS+1)); echo "  ✅ $1"; }
fail() { FAIL=$((FAIL+1)); echo "  ❌ $1"; echo "     详情: $2"; }

curl_code() {
  curl -s -o /dev/null -w "%{http_code}" "$@" 2>/dev/null || echo "000"
}

echo "========================================="
echo " Round 5 P1-C 后端 E2E 回归测试"
echo " BASE=$BASE"
echo "========================================="

# Get admin token
echo ""
echo "[准备] 获取 admin token..."
LOGIN_RESP=$(curl -s -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' 2>/dev/null || echo '{"data":{}}')
TOKEN=$(echo "$LOGIN_RESP" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("data",{}).get("accessToken",""))' 2>/dev/null || echo "")

if [ -z "$TOKEN" ]; then
  echo "  ❌ 无法获取 admin token，请确认后端运行正常"
  echo "  login resp: $LOGIN_RESP"
  exit 1
fi
ok "admin token 获取成功"

# Unique title prefix (避免重复 title unique 约束冲突)
TS=$(date +%s)
TITLE_PREFIX="P1C-E2E-${TS}-"

# Get a valid categoryId and teacherId from DB
CAT_ID=$(curl -s "$BASE/api/course-categories" -H "Authorization: Bearer $TOKEN" | python3 -c "import sys,json;d=json.load(sys.stdin);print(d.get('data',{}).get('items',[{}])[0].get('id',1) if d.get('data',{}).get('items') else 1)" 2>/dev/null || echo "1")
TEACHER_ID=$(curl -s "$BASE/api/users?role=TEACHER&size=1" -H "Authorization: Bearer $TOKEN" | python3 -c "import sys,json;d=json.load(sys.stdin);items=d.get('data',{}).get('items',[]);print(items[0].get('id',1) if items else 1)" 2>/dev/null || echo "1")

echo ""
echo "========================================="
echo "[E2E-1] R3-A3-003 P1-C: summary 字段 301 字 → 期望 400 而非 500"
echo "========================================="
LONG_SUMMARY=$(python3 -c "print('x'*301)")
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$BASE/api/courses" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"title\":\"${TITLE_PREFIX}summary-301\",\"summary\":\"$LONG_SUMMARY\",\"categoryId\":$CAT_ID,\"teacherId\":$TEACHER_ID,\"price\":0,\"isFree\":true,\"description\":\"P1C test\"}" 2>/dev/null || echo "")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | head -1 | head -c 300)

if [ "$HTTP_CODE" = "400" ]; then
  ok "summary 301 字返回 400（修复前是 500 DB 截断错误）"
  echo "    响应片段: $BODY"
elif [ "$HTTP_CODE" = "500" ]; then
  fail "summary 301 字返回 500" "修复失败,DB VARCHAR(300) 截断未拦截"
elif [ "$HTTP_CODE" = "200" ] || [ "$HTTP_CODE" = "201" ]; then
  fail "summary 301 字被接受 ($HTTP_CODE)" "@Size 校验未生效"
else
  fail "summary 301 字返回 $HTTP_CODE" "异常响应"
fi

echo ""
echo "========================================="
echo "[E2E-2] R3-A3-003 P1-C: summary 字段 300 字边界 → 期望 200/201"
echo "========================================="
EXACT_SUMMARY=$(python3 -c "print('y'*300)")
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$BASE/api/courses" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"title\":\"${TITLE_PREFIX}summary-300\",\"summary\":\"$EXACT_SUMMARY\",\"categoryId\":$CAT_ID,\"teacherId\":$TEACHER_ID,\"price\":0,\"isFree\":true,\"description\":\"boundary test\"}" 2>/dev/null || echo "")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | head -1 | head -c 200)

if [ "$HTTP_CODE" = "200" ] || [ "$HTTP_CODE" = "201" ]; then
  COURSE_ID=$(echo "$RESPONSE" | head -1 | python3 -c "import sys,json;d=json.load(sys.stdin);print(d.get('data',{}).get('id',''))" 2>/dev/null || echo "")
  ok "summary 300 字边界值接受 ($HTTP_CODE)"
  [ -n "$COURSE_ID" ] && echo "    创建课程 ID: $COURSE_ID, 可清理"
elif [ "$HTTP_CODE" = "400" ]; then
  fail "summary 300 字被拒" "@Size 边界设置错误(应是 300 不是 299)"
else
  fail "summary 300 字返回 $HTTP_CODE" "异常响应"
fi

echo ""
echo "========================================="
echo "[E2E-3] R3-A3-003 P1-C: summary 字段 299 字 → 期望 200/201"
echo "========================================="
SHORT_SUMMARY=$(python3 -c "print('z'*299)")
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$BASE/api/courses" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"title\":\"${TITLE_PREFIX}summary-299\",\"summary\":\"$SHORT_SUMMARY\",\"categoryId\":$CAT_ID,\"teacherId\":$TEACHER_ID,\"price\":0,\"isFree\":true,\"description\":\"under boundary\"}" 2>/dev/null || echo "")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)

if [ "$HTTP_CODE" = "200" ] || [ "$HTTP_CODE" = "201" ]; then
  ok "summary 299 字正常接受"
else
  fail "summary 299 字返回 $HTTP_CODE" "边界下不应被拒"
fi

echo ""
echo "========================================="
echo "[E2E-4] P0-3: GET /api/banners 公开访问 (修复前未公开)"
echo "========================================="
# 必须不带 token 才能验证是公开 API
HTTP_CODE=$(curl -s -o /tmp/banners.json -w "%{http_code}" "$BASE/api/banners" 2>/dev/null || echo "000")
if [ "$HTTP_CODE" = "200" ]; then
  BODY=$(cat /tmp/banners.json | head -c 300)
  ok "/api/banners 公开访问成功 (200)"
  echo "    响应: $BODY"
  # 验证是 BannerVO 列表（有 id/imageUrl 字段）
  if cat /tmp/banners.json | python3 -c "import sys,json;d=json.load(sys.stdin);items=d.get('data',[]);assert isinstance(items,list);print('items:',len(items))" 2>/dev/null; then
    ok "/api/banners 响应格式正确（R<List<BannerVO>>）"
  else
    fail "/api/banners 响应格式" "不是 R<List<BannerVO>>"
  fi
else
  fail "/api/banners 返回 $HTTP_CODE" "BannerPublicController 未生效"
fi

echo ""
echo "========================================="
echo "[E2E-5] P0-3: GET /api/admin/banners 仍需 ADMIN 权限 (验证未误放)"
echo "========================================="
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/admin/banners" 2>/dev/null || echo "000")
if [ "$HTTP_CODE" = "401" ] || [ "$HTTP_CODE" = "403" ]; then
  ok "/api/admin/banners 未授权时返回 $HTTP_CODE (正确)"
else
  fail "/api/admin/banners 未授权返回 $HTTP_CODE" "SecurityConfig 误放"
fi

echo ""
echo "========================================="
echo " 总结果"
echo "========================================="
echo " 通过: $PASS"
echo " 失败: $FAIL"
echo " 总计: $((PASS+FAIL))"

if [ "$FAIL" -gt 0 ]; then
  exit 1
fi
echo ""
echo "✅ 全部 P1-C 后端 E2E 通过"
