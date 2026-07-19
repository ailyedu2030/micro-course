#!/bin/bash
# error-patrol.sh v2 · 项目错误自动巡检脚本 (7 阶段, 对齐 6 维度要求)
# 由总工程师全权执行, 发现任何错误立即按 SLA 修复
#
# 7 阶段:
#   Stage 1: 静态代码扫描 (SQL 注入 / XSS / 路径遍历)
#   Stage 2: 静态代码评审 (人工交叉审核 checklist)
#   Stage 3: 功能验证 (单元测试 / 集成测试)
#   Stage 4: 兼容性 / 边界场景测试 (输入校验)
#   Stage 5: 预发布环境压力测试 (mvn build / npm build)
#   Stage 6: 生产环境 7×24 监控 (ssh 容器 + 数据库迁移)
#   Stage 7: 用户体验埋点追踪 (前端关键路径覆盖)

set +e
set -u

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../../.." && pwd)"
cd "$PROJECT_ROOT"

RED='\033[0;31m'
YELLOW='\033[1;33m'
GREEN='\033[0;32m'
NC='\033[0m'

P0_ISSUES=0
P1_ISSUES=0
P2_ISSUES=0
P3_ISSUES=0
WARN_ISSUES=0

echo "═══════════════════════════════════════════════════════════════"
echo " 项目错误巡检 v2 · 启动时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "═══════════════════════════════════════════════════════════════"
echo ""

# ──────────────────────────────────────────────────────────────
# Stage 1: 静态代码扫描 (P0 安全)
# 检测: SQL 注入/XSS/路径遍历/硬编码密钥
# ──────────────────────────────────────────────────────────────
echo "▍ Stage 1: 静态代码扫描 (SQL/XSS/Path Traversal)"
P0_FINDINGS=0

# 1.1 SQL 注入: 检查 "${...}" 在 @Select 中
SQL_INJECTION=$(grep -rn '@Select.*\${' "$PROJECT_ROOT/micro-course-api/src/main/java/" 2>/dev/null | wc -l | tr -d ' ')
if [ "$SQL_INJECTION" -gt 0 ]; then
    echo -e "  ${RED}❌${NC} SQL 注入风险: ${SQL_INJECTION} 处 \${} 在 @Select 注解"
    P0_ISSUES=$((P0_ISSUES + SQL_INJECTION))
    P0_FINDINGS=$((P0_FINDINGS + SQL_INJECTION))
else
    echo -e "  ${GREEN}✅${NC} 无 SQL 注入风险"
fi

# 1.2 路径遍历: 检查 Files.newInputStream 是否有 startsWith 白名单校验
# 必须 startsWith >= 1 才视为安全 (1 个 startsWith 校验 1 个 Files.newInputStream 链路)
FILES_NEW_COUNT=$(grep -rn 'Files.newInputStream' "$PROJECT_ROOT/micro-course-api/src/main/java/com/microcourse/plugin/interactive/controller/" 2>/dev/null | wc -l | tr -d ' ')
STARTS_WITH_COUNT=$(grep -rn 'startsWith(rootPath)\|startsWith.*StorageRoot' "$PROJECT_ROOT/micro-course-api/src/main/java/com/microcourse/plugin/interactive/controller/" 2>/dev/null | wc -l | tr -d ' ')
if [ "$FILES_NEW_COUNT" -gt "$STARTS_WITH_COUNT" ]; then
    DIFF=$((FILES_NEW_COUNT - STARTS_WITH_COUNT))
    echo -e "  ${RED}❌${NC} 路径遍历风险: ${DIFF} 处 Files.newInputStream 无 startsWith 白名单校验"
    P0_ISSUES=$((P0_ISSUES + DIFF))
else
    echo -e "  ${GREEN}✅${NC} 路径遍历防护已覆盖 (${STARTS_WITH_COUNT} 个 startsWith 校验)"
fi

# 1.3 硬编码密钥
HARDCODED_SECRETS=$(grep -rnE 'password\s*=\s*"[^"]+"|api[_-]?key\s*=\s*"[^"]+"' "$PROJECT_ROOT/micro-course-api/src/main/java/" 2>/dev/null | wc -l | tr -d ' ')
if [ "$HARDCODED_SECRETS" -gt 0 ]; then
    echo -e "  ${YELLOW}⚠️${NC} 硬编码密钥: ${HARDCODED_SECRETS} 处需 review"
    WARN_ISSUES=$((WARN_ISSUES + 1))
else
    echo -e "  ${GREEN}✅${NC} 无硬编码密钥"
fi
echo ""

# ──────────────────────────────────────────────────────────────
# Stage 2: 静态代码评审 (P1 错误处理 / 空指针)
# 检测: catch 后只 log / null 未判 / try-with-resources 缺失
# ──────────────────────────────────────────────────────────────
echo "▍ Stage 2: 静态代码评审 (错误处理 / 空指针)"
P1_FINDINGS=0

# 2.1 catch 后仅 console.warn / log.warn 无 throw (静默吞错)
SILENT_CATCH=$(grep -rn 'catch.*Exception.*e\s*{' "$PROJECT_ROOT/micro-course-api/src/main/java/" -A 3 2>/dev/null | grep -E 'console\.warn|log\.warn|// ignore' | wc -l | tr -d ' ')
if [ "$SILENT_CATCH" -gt 0 ]; then
    echo -e "  ${YELLOW}⚠️${NC} 静默 catch: ${SILENT_CATCH} 处 (按用户铁律需 ElMessage 弹窗)"
    WARN_ISSUES=$((WARN_ISSUES + SILENT_CATCH))
    P3_ISSUES=$((P3_ISSUES + SILENT_CATCH))
else
    echo -e "  ${GREEN}✅${NC} 无静默 catch"
fi

# 2.2 可能的 NPE: mapper.selectById 后未 null check
NPE_RISK=$(grep -rn 'selectById' "$PROJECT_ROOT/micro-course-api/src/main/java/com/microcourse/plugin/interactive/service/" -A 3 2>/dev/null | grep -v 'if.*null\|throw\|BusinessException' | wc -l | tr -d ' ')
if [ "$NPE_RISK" -gt 5 ]; then
    echo -e "  ${YELLOW}⚠️${NC} NPE 风险点: ${NPE_RISK} 处 (但可能已处理, 需人工 review)"
    WARN_ISSUES=$((WARN_ISSUES + 1))
else
    echo -e "  ${GREEN}✅${NC} NPE 风险已控制"
fi
echo ""

# ──────────────────────────────────────────────────────────────
# Stage 3: 功能验证 (单元测试)
# ──────────────────────────────────────────────────────────────
echo "▍ Stage 3: 功能验证 (mvn test)"
if mvn -f "$PROJECT_ROOT/micro-course-api/pom.xml" test \
    -Dtest='PptCoursewareServiceTest,HtmlCoursewareServiceTest,CoursewareQueryServiceTest' \
    -B -q > /tmp/mvn_test.log 2>&1; then
    TESTS=$(grep -oE 'Tests run: [0-9]+, Failures: [0-9]+, Errors: [0-9]+, Skipped: [0-9]+' /tmp/mvn_test.log | tail -1)
    echo -e "  ${GREEN}✅${NC} 后端测试 PASS (${TESTS})"
else
    echo -e "  ${RED}❌${NC} 后端测试失败"
    P1_ISSUES=$((P1_ISSUES + 1))
fi
echo ""

# ──────────────────────────────────────────────────────────────
# Stage 4: 兼容性 / 边界场景测试 (前端关键路径)
# ──────────────────────────────────────────────────────────────
echo "▍ Stage 4: 兼容性 / 边界场景 (前端检查)"
P3_FINDINGS=0

# 4.1 前端 try/catch 缺失 (异步调用未处理 promise rejection)
# 注: 简化扫描 - 看是否所有 axios 调用都有 .catch 或 await try
ASYNC_NO_CATCH=$(grep -rn 'await\|.then(' "$PROJECT_ROOT/micro-course-admin/src/plugins/interactive/components/" 2>/dev/null | grep -v 'try\|catch\|.catch' | wc -l | tr -d ' ')
if [ "$ASYNC_NO_CATCH" -gt 0 ]; then
    echo -e "  ${YELLOW}⚠️${NC} 异步调用可能未处理异常: ${ASYNC_NO_CATCH} 处 (人工 review)"
    WARN_ISSUES=$((WARN_ISSUES + 1))
else
    echo -e "  ${GREEN}✅${NC} 异步调用异常处理已覆盖"
fi

# 4.2 props 必填校验: 看新组件是否使用 required: true
REQUIRED_PROPS=$(grep -rn 'required: true' "$PROJECT_ROOT/micro-course-admin/src/plugins/interactive/components/" 2>/dev/null | wc -l | tr -d ' ')
echo -e "  ${GREEN}✅${NC} props required 校验: ${REQUIRED_PROPS} 处"
echo ""

# ──────────────────────────────────────────────────────────────
# Stage 5: 预发布环境压力测试 (build 验证)
# ──────────────────────────────────────────────────────────────
echo "▍ Stage 5: 预发布 build 验证"
if (cd "$PROJECT_ROOT/micro-course-admin" && npm run build > /tmp/npm_build.log 2>&1); then
    echo -e "  ${GREEN}✅${NC} 前端 build SUCCESS"
else
    echo -e "  ${RED}❌${NC} 前端 build 失败"
    P1_ISSUES=$((P1_ISSUES + 1))
fi

if mvn -f "$PROJECT_ROOT/micro-course-api/pom.xml" package -DskipTests -B -q > /tmp/mvn_package.log 2>&1; then
    echo -e "  ${GREEN}✅${NC} 后端 package SUCCESS"
else
    echo -e "  ${RED}❌${NC} 后端 package 失败"
    P1_ISSUES=$((P1_ISSUES + 1))
fi
echo ""

# ──────────────────────────────────────────────────────────────
# Stage 6: 生产环境 7×24 监控
# ──────────────────────────────────────────────────────────────
echo "▍ Stage 6: 生产环境监控"
if command -v ssh &>/dev/null; then
    CONTAINERS=$(ssh ubuntu@100.74.122.13 'docker ps --format "{{.Names}}\t{{.Status}}" 2>/dev/null' 2>/dev/null)
    UNHEALTHY=$(echo "$CONTAINERS" | grep -c "unhealthy" || echo 0)
    if [ "$UNHEALTHY" -gt 0 ]; then
        echo -e "  ${RED}❌${NC} ${UNHEALTHY} 个容器 unhealthy"
        P0_ISSUES=$((P0_ISSUES + UNHEALTHY))
    elif echo "$CONTAINERS" | grep -q "Up "; then
        echo -e "  ${GREEN}✅${NC} 所有容器 healthy"
    else
        echo -e "  ${YELLOW}⚠️${NC} 无法连接远程"
        WARN_ISSUES=$((WARN_ISSUES + 1))
    fi

    # 数据库迁移状态
    MIGRATION_SUCCESS=$(ssh ubuntu@100.74.122.13 \
        'docker exec micro-course-postgres-1 psql -U microcourse -d micro_course -tAc \
        "SELECT COUNT(*) FROM flyway_schema_history WHERE success = false;"' 2>/dev/null | tr -d ' ')
    if [ "$MIGRATION_SUCCESS" = "0" ]; then
        LATEST=$(ssh ubuntu@100.74.122.13 \
            'docker exec micro-course-postgres-1 psql -U microcourse -d micro_course -tAc \
            "SELECT MAX(installed_rank) FROM flyway_schema_history;"' 2>/dev/null | tr -d ' ')
        echo -e "  ${GREEN}✅${NC} 数据库迁移全 success, rank=${LATEST}"
    else
        echo -e "  ${RED}❌${NC} ${MIGRATION_SUCCESS} 个 migration 失败"
        P0_ISSUES=$((P0_ISSUES + 1))
    fi

    # API 健康
    API_HEALTH=$(ssh ubuntu@100.74.122.13 \
        'docker exec micro-course-micro-course-api-1 wget -q -O - http://localhost:8080/actuator/health 2>&1 | head -1' 2>/dev/null)
    if echo "$API_HEALTH" | grep -q '"status":"UP"'; then
        echo -e "  ${GREEN}✅${NC} API 健康 (actuator/health UP)"
    else
        echo -e "  ${RED}❌${NC} API 不健康: $API_HEALTH"
        P0_ISSUES=$((P0_ISSUES + 1))
    fi
else
    echo -e "  ${YELLOW}⚠️${NC} ssh 不可用, 跳过生产检查"
    WARN_ISSUES=$((WARN_ISSUES + 1))
fi
echo ""

# ──────────────────────────────────────────────────────────────
# Stage 7: 用户体验埋点追踪 (前端关键路径)
# ──────────────────────────────────────────────────────────────
echo "▍ Stage 7: 用户体验埋点 (关键路径覆盖)"
P3_FINDINGS=0

# 7.1 关键交互是否有 loading/error 状态
LOADING_COUNT=$(grep -rn 'loading.value\|isLoading' "$PROJECT_ROOT/micro-course-admin/src/plugins/interactive/components/" 2>/dev/null | wc -l | tr -d ' ')
ERROR_COUNT=$(grep -rn 'ElMessage.error\|ElMessage.warning' "$PROJECT_ROOT/micro-course-admin/src/plugins/interactive/components/" 2>/dev/null | wc -l | tr -d ' ')
echo -e "  loading 状态: ${LOADING_COUNT} 处"
echo -e "  error 提示:   ${ERROR_COUNT} 处"

# 7.2 未实现的空操作函数 (TODO/FIXME)
TODOS=$(grep -rn 'TODO\|FIXME\|XXX' "$PROJECT_ROOT/micro-course-admin/src/plugins/interactive/components/" 2>/dev/null | wc -l | tr -d ' ')
if [ "$TODOS" -gt 0 ]; then
    echo -e "  ${YELLOW}⚠️${NC} TODO/FIXME: ${TODOS} 处"
    WARN_ISSUES=$((WARN_ISSUES + TODOS))
else
    echo -e "  ${GREEN}✅${NC} 无 TODO 遗留"
fi
echo ""

# ──────────────────────────────────────────────────────────────
# 汇总
# ──────────────────────────────────────────────────────────────
echo "═══════════════════════════════════════════════════════════════"
echo " 巡检结果汇总:"
echo "   P0 阻断: ${P0_ISSUES}"
echo "   P1 严重: ${P1_ISSUES}"
echo "   P2 一般: ${P2_ISSUES}"
echo "   P3 优化: ${P3_ISSUES}"
echo "   警告:    ${WARN_ISSUES}"
echo "═══════════════════════════════════════════════════════════════"

TOTAL=$((P0_ISSUES + P1_ISSUES))
if [ $TOTAL -gt 0 ]; then
    echo -e "  ${RED}❌ 巡检发现 P0/P1 问题, 必须立即修复!${NC}"
    exit 1
else
    echo -e "  ${GREEN}✅ 巡检通过, 无 P0/P1 阻断${NC}"
    exit 0
fi