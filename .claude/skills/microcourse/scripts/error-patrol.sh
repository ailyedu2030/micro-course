#!/bin/bash
# error-patrol.sh · 项目错误自动巡检脚本 (5 阶段)
# 由总工程师全权执行, 发现任何错误立即按 SLA 修复

set +e
set -u

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../../.." && pwd)"
cd "$PROJECT_ROOT"

RED='\033[0;31m'
YELLOW='\033[1;33m'
GREEN='\033[0;32m'
NC='\033[0m' # No Color

P0_ISSUES=0
P1_ISSUES=0
P2_ISSUES=0
P3_ISSUES=0
WARN_ISSUES=0

echo "═══════════════════════════════════════════════════════════════"
echo " 项目错误巡检 · 启动时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "═══════════════════════════════════════════════════════════════"
echo ""

# Stage 1: precheck.sh (基线检查)
echo "▍ Stage 1: 代码评审基线 (precheck.sh)"
if bash "$SCRIPT_DIR/precheck.sh" > /tmp/precheck.log 2>&1; then
    echo -e "  ${GREEN}✅${NC} precheck 通过"
else
    echo -e "  ${RED}❌${NC} precheck 失败, 检查 /tmp/precheck.log"
    P0_ISSUES=$((P0_ISSUES + 1))
fi
echo ""

# Stage 2: mvn test (后端单元测试)
echo "▍ Stage 2: 后端单元测试 (mvn test)"
if mvn -f "$PROJECT_ROOT/micro-course-api/pom.xml" test \
    -Dtest='PptCoursewareServiceTest,HtmlCoursewareServiceTest,CoursewareQueryServiceTest' \
    -B -q > /tmp/mvn_test.log 2>&1; then
    TESTS=$(grep -c 'Tests run:.*Failures: 0' /tmp/mvn_test.log 2>/dev/null || echo "0")
    echo -e "  ${GREEN}✅${NC} 后端测试 PASS (${TESTS} classes)"
else
    FAILED=$(grep -E 'Tests run:.*Failures: [^0]' /tmp/mvn_test.log | head -1)
    echo -e "  ${RED}❌${NC} 后端测试失败: $FAILED"
    P1_ISSUES=$((P1_ISSUES + 1))
fi
echo ""

# Stage 3: npm build (前端编译)
echo "▍ Stage 3: 前端编译 (npm build)"
if (cd "$PROJECT_ROOT/micro-course-admin" && npm run build > /tmp/npm_build.log 2>&1); then
    echo -e "  ${GREEN}✅${NC} 前端 build SUCCESS"
else
    echo -e "  ${RED}❌${NC} 前端 build 失败, 检查 /tmp/npm_build.log"
    P1_ISSUES=$((P1_ISSUES + 1))
fi
echo ""

# Stage 4: 生产容器健康
echo "▍ Stage 4: 生产容器健康 (ssh remote)"
if command -v ssh &>/dev/null; then
    CONTAINERS=$(ssh ubuntu@100.74.122.13 'docker ps --format "{{.Names}}\t{{.Status}}" 2>/dev/null' 2>/dev/null)
    if echo "$CONTAINERS" | grep -q "unhealthy"; then
        UNHEALTHY=$(echo "$CONTAINERS" | grep -c "unhealthy")
        echo -e "  ${RED}❌${NC} ${UNHEALTHY} 个容器 unhealthy"
        P0_ISSUES=$((P0_ISSUES + UNHEALTHY))
    elif echo "$CONTAINERS" | grep -q "Up "; then
        echo -e "  ${GREEN}✅${NC} 所有容器 healthy"
    else
        echo -e "  ${YELLOW}⚠️${NC} 无法连接远程 (ssh 失败)"
        WARN_ISSUES=$((WARN_ISSUES + 1))
    fi
else
    echo -e "  ${YELLOW}⚠️${NC} ssh 不可用, 跳过生产检查"
    WARN_ISSUES=$((WARN_ISSUES + 1))
fi
echo ""

# Stage 5: 数据库迁移状态
echo "▍ Stage 5: 数据库迁移状态"
if command -v ssh &>/dev/null; then
    MIGRATION_SUCCESS=$(ssh ubuntu@100.74.122.13 \
        'docker exec micro-course-postgres-1 psql -U microcourse -d micro_course -tAc \
        "SELECT COUNT(*) FROM flyway_schema_history WHERE success = false;"' 2>/dev/null | tr -d ' ')
    if [ "$MIGRATION_SUCCESS" = "0" ]; then
        LATEST=$(ssh ubuntu@100.74.122.13 \
            'docker exec micro-course-postgres-1 psql -U microcourse -d micro_course -tAc \
            "SELECT MAX(installed_rank) FROM flyway_schema_history;"' 2>/dev/null | tr -d ' ')
        echo -e "  ${GREEN}✅${NC} 数据库迁移全 success, 最新 rank=${LATEST}"
    else
        echo -e "  ${RED}❌${NC} 数据库迁移失败: ${MIGRATION_SUCCESS} 条 success=false"
        P0_ISSUES=$((P0_ISSUES + 1))
    fi
else
    echo -e "  ${YELLOW}⚠️${NC} ssh 不可用, 跳过数据库检查"
fi
echo ""

# Summary
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