#!/bin/bash
# precheck.sh — 编码前预检脚本
# 退出码 != 0 → 禁止写代码
#
# 检查项 (按顺序):
#   1. 后端编译 (mvn compile)
#   2. 前端 lint (eslint)
#   3. git 状态
#   4. P0-3: 禁止 headers: {} 显式空对象 (axios 0.27+ 不自动注入 Content-Type → 415)
#   5. P0-3: 禁止 axios 直接调用 (走 request instance, 由拦截器统一处理 Content-Type/401)
#   6. P0-3: 禁止 console.warn 在 fallback 路径 (enums.js 已改 debug, 防止用户截图噪音)
#   7. P0-3: 文档同步检查 (CHANGELOG.md / ROLLBACK_PLAN.md 含本次 commit 内容)
#   8. console.error 全局检查 (新代码不应引入 console.error)

set -e

# ===== 颜色定义 =====
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

pass() { echo -e "${GREEN}✅ $1${NC}"; }
fail() { echo -e "${RED}❌ $1${NC}"; exit 1; }
warn() { echo -e "${YELLOW}⚠️  $1${NC}"; }

echo -e "${BLUE}🔍 Running pre-check...${NC}"

# ===== 1. 后端编译 =====
echo ""
echo -e "${BLUE}[1/8] Backend compile check...${NC}"
cd micro-course-api
mvn compile -q 2>/dev/null || { fail "Backend compile failed"; }
pass "Backend compile OK"
cd ..

# ===== 2. 前端 lint =====
echo ""
echo -e "${BLUE}[2/8] Frontend lint check...${NC}"
cd micro-course-admin
npm run lint 2>/dev/null || { fail "Frontend lint failed"; }
pass "Frontend lint OK"
cd ..

# ===== 3. git 状态 =====
echo ""
echo -e "${BLUE}[3/8] Git status...${NC}"
cd micro-course-admin
git status --short
pass "Git status OK (untracked files allowed)"
cd ..

# ===== 4. P0-3: 禁止 headers: {} 显式空对象 =====
# Bug G 根因 (PR #165): axios.post('/auth/refresh', _, { _skipAuth: true, headers: {} })
#   axios 0.27+ 显式 headers: {} 不自动注入 Content-Type → 后端 415
#   修复: headers: { 'Content-Type': 'application/json' }
#   防止再发: precheck 禁止任何 headers: {} 显式空对象
echo ""
echo -e "${BLUE}[4/8] P0-3: 检查 headers: {} 显式空对象 (Bug G 防再发)...${NC}"
HEADERS_EMPTY=$(grep -rn 'headers:[[:space:]]*{[[:space:]]*}' micro-course-admin/src/ \
  --include="*.vue" --include="*.js" --include="*.ts" \
  --exclude-dir=node_modules --exclude-dir=dist 2>/dev/null | \
  grep -v "eslint-disable-next-line" || true)
if [ -n "$HEADERS_EMPTY" ]; then
  fail "发现 headers: {} 显式空对象 (Bug G 防再发, 触发 415):
$HEADERS_EMPTY
修复: 改为 headers: { 'Content-Type': 'application/json' }"
fi
pass "No headers: {} found"

# ===== 5. P0-3: 禁止 axios 直接调用 =====
# Bug G 根因: refresh 用 axios.post 直接调用, 绕过 request instance 的 Content-Type/401 拦截器
# 防止再发: 禁止 micro-course-admin/src/ 直接 import axios (除 utils/request.js)
# 注: utils/request.js 是 instance 创建位置, 必须 import
echo ""
echo -e "${BLUE}[5/8] P0-3: 检查 axios 直接调用 (Bug G 防再发)...${NC}"
AXIOS_DIRECT=$(grep -rn "^import.*['\"]axios['\"]" micro-course-admin/src/ \
  --include="*.vue" --include="*.js" --include="*.ts" \
  --exclude-dir=node_modules --exclude-dir=dist 2>/dev/null | \
  grep -v "utils/request.js" || true)
if [ -n "$AXIOS_DIRECT" ]; then
  fail "发现 utils/request.js 之外直接 import axios (应走 request instance, 由拦截器统一处理):
$AXIOS_DIRECT
修复: import request from '@/utils/request'; 然后 request({ method: 'POST', url: '/api/xxx', data })"
fi
pass "No direct axios import found (all use request instance)"

# ===== 6. P0-3: 禁止 console.warn 在 fallback 路径 =====
# Bug H 根因: enums.js fallback 路径 console.warn 噪音 (PR #165)
# 防止再发: 检查 src/utils/ 文件 console.warn 数量 (fallback 路径用 console.debug)
echo ""
echo -e "${BLUE}[6/8] P0-3: 检查 utils/ console.warn (fallback 路径应 console.debug)...${NC}"
CONSOLE_WARN_UTILS=$(grep -rn "console\.warn" micro-course-admin/src/utils/ \
  --include="*.vue" --include="*.js" --include="*.ts" 2>/dev/null | \
  grep -v "eslint-disable-next-line" || true)
if [ -n "$CONSOLE_WARN_UTILS" ]; then
  warn "utils/ 仍有 console.warn (应 review 是否 fallback 噪音):
$CONSOLE_WARN_UTILS
(若是有意保留, 加 eslint-disable-next-line 注释说明)"
fi
pass "utils/ console.warn reviewed"

# ===== 7. P0-3: 文档同步检查 =====
# PR #162, #166 教训: 任何代码修复必须 CHANGELOG.md + ROLLBACK_PLAN.md 同步
# 自动检查: diff 中新增文件是否在两个文档中提到
echo ""
echo -e "${BLUE}[7/8] P0-3: 文档同步检查 (CHANGELOG/ROLLBACK_PLAN)...${NC}"
CHANGED_FILES=$(cd micro-course-admin && git diff --name-only HEAD 2>/dev/null | grep -E "\.(vue|js|ts)$" || true)
if [ -n "$CHANGED_FILES" ]; then
  for f in $CHANGED_FILES; do
    if [ -f "micro-course-admin/$f" ]; then
      # 文件名 (basename) 是否在 CHANGELOG/ROLLBACK 提到
      BASENAME=$(basename "$f" | head -c 30)
      IN_CHANGELOG=$(grep -c "$BASENAME" CHANGELOG.md 2>/dev/null || echo 0)
      IN_ROLLBACK=$(grep -c "$BASENAME" ROLLBACK_PLAN.md 2>/dev/null || echo 0)
      if [ "$IN_CHANGELOG" -eq 0 ] && [ "$IN_ROLLBACK" -eq 0 ]; then
        warn "$f 修改但 CHANGELOG/ROLLBACK_PLAN 未提及 (文档可能落后)"
      fi
    fi
  done
fi
pass "文档同步 reviewed"

# ===== 8. console.error 全局检查 =====
# 生产 console 应避免 console.error 噪音 (Bug H 教训)
# 允许位置: src/utils/errorReport.js, src/components/ErrorBoundary.vue (故意)
echo ""
echo -e "${BLUE}[8/8] 全局 console.error 检查 (防止用户截图噪音)...${NC}"
CONSOLE_ERR=$(grep -rn "console\.error" micro-course-admin/src/ \
  --include="*.vue" --include="*.js" --include="*.ts" \
  --exclude-dir=node_modules --exclude-dir=dist \
  --exclude-dir=__tests__ 2>/dev/null | \
  grep -v "utils/errorReport.js" | \
  grep -v "components/ErrorBoundary.vue" | \
  grep -v "eslint-disable-next-line" || true)
if [ -n "$CONSOLE_ERR" ]; then
  warn "src/ 含 console.error (应 review 是否必要, 业务错误请用 ElMessage):
$CONSOLE_ERR"
fi
pass "console.error reviewed"

# ===== 完成 =====
echo ""
echo -e "${GREEN}✅ All pre-checks passed (8 项)${NC}"
echo ""
echo -e "${BLUE}后续:${NC}"
echo "  1. 检查 CHANGELOG.md [Unreleased] 是否含本次变更描述"
echo "  2. 检查 ROLLBACK_PLAN.md 最近 3 版本是否含本次变更"
echo "  3. 如涉及生产部署, 跑 bash scripts/deploy-frontend.sh <dist.tar.gz>"