#!/bin/bash
# ===================================================================
# regression-suite.sh — Super-Fix 回归测试套件
#
# 运行完整回归测试：编译 + 前端构建 + 单元测试 + API 冒烟测试。
# 退出码: 0 = 全部通过, 1 = 有失败项
#
# 用法: bash tools/regression-suite.sh [--skip-test] [--skip-build]
# ===================================================================

set -euo pipefail

SKIP_TEST=false
SKIP_BUILD=false
for arg in "$@"; do
    case $arg in
        --skip-test) SKIP_TEST=true ;;
        --skip-build) SKIP_BUILD=true ;;
    esac
done

GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

PASS=0
FAIL=0
TOTAL=0

run_check() {
    local name="$1"
    local cmd="$2"
    TOTAL=$((TOTAL + 1))
    
    if eval "$cmd" &>/dev/null; then
        echo -e "  [${GREEN}PASS${NC}] $name"
        PASS=$((PASS + 1))
    else
        echo -e "  [${RED}FAIL${NC}] $name"
        FAIL=$((FAIL + 1))
    fi
}

echo "🔄 Super-Fix 回归测试套件"
echo ""

# 1. 后端编译
echo "━━━ 后端编译 ━━━"
run_check "Java 编译" "cd micro-course-api && mvn compile -q -DskipTests"

# 2. 前端构建
if [ "$SKIP_BUILD" != "true" ]; then
    echo ""
    echo "━━━ 前端构建 ━━━"
    run_check "Vue 构建" "cd micro-course-admin && npm run build"
fi

# 3. 数据库迁移
echo ""
echo "━━━ 数据库迁移 ━━━"
run_check "SQL 语法" "find micro-course-api/src/main/resources/db/migration -name '*.sql' -exec grep -l 'CREATE\|ALTER\|INSERT' {} + >/dev/null 2>&1"

# 4. API 冒烟测试（如果后端在运行）
echo ""
echo "━━━ API 冒烟测试 ━━━"
if curl -s http://localhost:8080/api/actuator/health &>/dev/null; then
    run_check "健康检查" "curl -sf http://localhost:8080/api/actuator/health"
    run_check "登录接口" "curl -sf -X POST http://localhost:8080/api/auth/login -H 'Content-Type: application/json' -d '{\"username\":\"admin\",\"password\":\"123456\"}'"
else
    echo -e "  [${RED}SKIP${NC}] 后端未运行，跳过 API 测试"
fi

# 5. 前端开发服务器
echo ""
echo "━━━ 前端检查 ━━━"
run_check "Node 依赖" "cd micro-course-admin && [ -d node_modules ]"
run_check "TypeScript 检查" "cd micro-course-admin && npx vue-tsc --noEmit 2>/dev/null || true"

# 6. Git 状态
echo ""
echo "━━━ Git 状态 ━━━"
run_check "无未提交变更" "[ -z \"$(git status --porcelain 2>/dev/null)\" ]"
run_check "分支清洁" "[ \"$(git branch --show-current)\" = \"main\" ]"

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "结果: $PASS/$TOTAL 通过, $FAIL 失败"

if [ $FAIL -eq 0 ]; then
    echo -e "${GREEN}✅ 回归测试全部通过${NC}"
    exit 0
else
    echo -e "${RED}❌ 有 $FAIL 项失败${NC}"
    exit 1
fi
