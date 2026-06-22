#!/bin/bash
#
# 微课平台冒烟测试脚本
# 部署后立即运行，验证核心流程是否正常
#
# 使用方式: bash SMOKE_TEST.sh
#

set -e

API="http://localhost:8080/api"
ADMIN_API="http://localhost:8080/api/admin"

echo "=========================================="
echo "  微课平台冒烟测试"
echo "  $(date '+%Y-%m-%d %H:%M:%S')"
echo "=========================================="

PASS=0
FAIL=0

check() {
    local name="$1"
    local result="$2"
    if [ "$result" -eq 0 ]; then
        echo "  ✅ $name"
        ((PASS++))
    else
        echo "  ❌ $name"
        ((FAIL++))
    fi
}

# ------------------------------------------------------------------------------
# 1. 健康检查
# ------------------------------------------------------------------------------
echo ""
echo "[1/10] 健康检查..."
HEALTH=$(curl -s http://localhost:8080/actuator/health)
if echo "$HEALTH" | grep -q "UP"; then
    check "健康检查" 0
else
    echo "  响应: $HEALTH"
    check "健康检查" 1
    echo ""
    echo "应用未正常运行，退出冒烟测试"
    exit 1
fi

# ------------------------------------------------------------------------------
# 2. 学生注册（如果用户不存在）
# ------------------------------------------------------------------------------
echo ""
echo "[2/10] 学生注册..."
REGISTER_RESP=$(curl -s -X POST "$API/auth/register" \
    -H "Content-Type: application/json" \
    -d '{"username":"smoke_test_student","password":"Test123456","realName":"冒烟测试学生","studentNo":"SMOKE001","role":"STUDENT"}')
REGISTER_CODE=$(echo "$REGISTER_RESP" | grep -o '"code":[0-9]*' | grep -o '[0-9]*' | head -1)
if [ "$REGISTER_CODE" = "200" ] || [ "$REGISTER_CODE" = "201" ]; then
    echo "  (新用户注册成功)"
    check "学生注册" 0
elif echo "$REGISTER_RESP" | grep -q "already"; then
    echo "  (用户已存在，跳过注册)"
    check "学生注册(已存在)" 0
else
    echo "  响应: $REGISTER_RESP"
    check "学生注册" 1
fi

# ------------------------------------------------------------------------------
# 3. 学生登录
# ------------------------------------------------------------------------------
echo ""
echo "[3/10] 学生登录..."
LOGIN_RESP=$(curl -s -X POST "$API/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"smoke_test_student","password":"Test123456"}')
TOKEN=$(echo "$LOGIN_RESP" | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4)
if [ -n "$TOKEN" ] && [ "$TOKEN" != "null" ]; then
    echo "  Token获取成功: ${TOKEN:0:20}..."
    check "学生登录" 0
else
    echo "  响应: $LOGIN_RESP"
    check "学生登录" 1
    echo ""
    echo "学生登录失败，无法继续测试"
    exit 1
fi

# ------------------------------------------------------------------------------
# 4. 课程列表
# ------------------------------------------------------------------------------
echo ""
echo "[4/10] 课程列表..."
COURSES_RESP=$(curl -s "$API/courses?page=0&size=5" -H "Authorization: Bearer $TOKEN")
COURSES_CODE=$(echo "$COURSES_RESP" | grep -o '"code":[0-9]*' | head -1 | grep -o '[0-9]*')
if [ "$COURSES_CODE" = "200" ]; then
    check "课程列表" 0
else
    echo "  响应: $COURSES_RESP"
    check "课程列表" 1
fi

# ------------------------------------------------------------------------------
# 5. 我的课程（选课列表）
# ------------------------------------------------------------------------------
echo ""
echo "[5/10] 我的课程..."
MY_COURSES_RESP=$(curl -s "$API/enrollments/my" -H "Authorization: Bearer $TOKEN")
MY_CODE=$(echo "$MY_COURSES_RESP" | grep -o '"code":[0-9]*' | head -1 | grep -o '[0-9]*')
if [ "$MY_CODE" = "200" ]; then
    check "我的课程" 0
else
    echo "  响应: $MY_COURSES_RESP"
    check "我的课程" 1
fi

# ------------------------------------------------------------------------------
# 6. 学习进度
# ------------------------------------------------------------------------------
echo ""
echo "[6/10] 学习进度..."
PROGRESS_RESP=$(curl -s "$API/learning-progress/progress" -H "Authorization: Bearer $TOKEN")
PROGRESS_CODE=$(echo "$PROGRESS_RESP" | grep -o '"code":[0-9]*' | head -1 | grep -o '[0-9]*')
if [ "$PROGRESS_CODE" = "200" ]; then
    check "学习进度" 0
else
    echo "  响应: $PROGRESS_RESP"
    check "学习进度" 1
fi

# ------------------------------------------------------------------------------
# 7. 今日打卡
# ------------------------------------------------------------------------------
echo ""
echo "[7/10] 今日打卡..."
CHECKIN_RESP=$(curl -s -X POST "$API/check-ins" -H "Authorization: Bearer $TOKEN")
CHECKIN_CODE=$(echo "$CHECKIN_RESP" | grep -o '"code":[0-9]*' | head -1 | grep -o '[0-9]*')
if [ "$CHECKIN_CODE" = "200" ]; then
    check "今日打卡" 0
elif echo "$CHECKIN_RESP" | grep -q "already"; then
    echo "  (今日已打卡)"
    check "今日打卡(已打卡)" 0
else
    echo "  响应: $CHECKIN_RESP"
    check "今日打卡" 1
fi

# ------------------------------------------------------------------------------
# 8. 视频播放（获取视频URL）
# ------------------------------------------------------------------------------
echo ""
echo "[8/10] 视频播放..."
VIDEO_RESP=$(curl -s "$API/videos/1" -H "Authorization: Bearer $TOKEN")
VIDEO_CODE=$(echo "$VIDEO_RESP" | grep -o '"code":[0-9]*' | head -1 | grep -o '[0-9]*')
if [ "$VIDEO_CODE" = "200" ]; then
    check "视频播放" 0
else
    echo "  响应: $VIDEO_RESP"
    check "视频播放" 1
fi

# ------------------------------------------------------------------------------
# 9. 教师登录
# ------------------------------------------------------------------------------
echo ""
echo "[9/10] 教师登录..."

# 注册教师（如果不存在）
TEACHER_REG=$(curl -s -X POST "$API/auth/register" \
    -H "Content-Type: application/json" \
    -d '{"username":"smoke_test_teacher","password":"Test123456","realName":"冒烟测试教师","role":"TEACHER"}')

TEACHER_LOGIN=$(curl -s -X POST "$API/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"smoke_test_teacher","password":"Test123456"}')
TEACHER_TOKEN=$(echo "$TEACHER_LOGIN" | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4)
if [ -n "$TEACHER_TOKEN" ] && [ "$TEACHER_TOKEN" != "null" ]; then
    echo "  教师Token获取成功"
    check "教师登录" 0
else
    echo "  响应: $TEACHER_LOGIN"
    check "教师登录" 1
fi

# ------------------------------------------------------------------------------
# 10. 管理员登录
# ------------------------------------------------------------------------------
echo ""
echo "[10/10] 管理员登录..."

# 注册管理员（如果不存在）
ADMIN_REG=$(curl -s -X POST "$API/auth/register" \
    -H "Content-Type: application/json" \
    -d '{"username":"smoke_test_admin","password":"Test123456","realName":"冒烟测试管理员","role":"ADMIN"}')

ADMIN_LOGIN=$(curl -s -X POST "$API/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"smoke_test_admin","password":"Test123456"}')
ADMIN_TOKEN=$(echo "$ADMIN_LOGIN" | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4)
if [ -n "$ADMIN_TOKEN" ] && [ "$ADMIN_TOKEN" != "null" ]; then
    echo "  管理员Token获取成功"
    check "管理员登录" 0
else
    echo "  响应: $ADMIN_LOGIN"
    check "管理员登录" 1
fi

# 管理员统计概览
if [ -n "$ADMIN_TOKEN" ]; then
    STATS_RESP=$(curl -s "$ADMIN_API/stats/overview" -H "Authorization: Bearer $ADMIN_TOKEN")
    STATS_CODE=$(echo "$STATS_RESP" | grep -o '"code":[0-9]*' | head -1 | grep -o '[0-9]*')
    if [ "$STATS_CODE" = "200" ]; then
        check "管理员统计概览" 0
    else
        echo "  响应: $STATS_RESP"
        check "管理员统计概览" 1
    fi
fi

# ------------------------------------------------------------------------------
# 汇总
# ------------------------------------------------------------------------------
echo ""
echo "=========================================="
echo "  冒烟测试完成"
echo "  $(date '+%Y-%m-%d %H:%M:%S')"
echo "=========================================="
echo "  通过: $PASS"
echo "  失败: $FAIL"
echo "=========================================="

if [ $FAIL -eq 0 ]; then
    echo "  ✅ 全部通过！可以继续部署验证。"
    exit 0
else
    echo "  ❌ 有测试失败，请检查后重试。"
    exit 1
fi
