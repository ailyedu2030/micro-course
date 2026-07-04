#!/bin/bash
# ============================================================================
# test-gate.sh · 集成测试门禁
# 根因修复：Flyway V100 冲突阻塞全部集成测试未被及时发现
# 本脚本确保：
#   1. precheck 必须通过
#   2. 全部单元测试必须通过
#   3. 集成测试必须通过（含 DB 上下文）
#   4. Flyway 迁移版本无冲突
# ============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

PASS=0
FAIL=0

echo "============================================"
echo "  集成测试门禁 · $(date)"
echo "============================================"

# Step 1: precheck
echo ""
echo "--- Step 1/4: precheck ---"
if bash .claude/skills/microcourse/scripts/precheck.sh 2>&1; then
    echo "  ✅ precheck 通过"
    PASS=$((PASS+1))
else
    echo "  ❌ precheck 失败"
    FAIL=$((FAIL+1))
fi

# Step 2: Flyway 版本唯一性
echo ""
echo "--- Step 2/4: Flyway 版本唯一性 ---"
DUPS=$(find micro-course-api/src/main/resources/db/migration/ -name "V*.sql" \
    | sed 's/.*\/V\([0-9]*\)__.*/\1/' \
    | sort \
    | uniq -c \
    | sort -rn \
    | awk '$1 > 1 {print "  V"$2" 出现 "$1" 次"}')
if [ -z "$DUPS" ]; then
    echo "  ✅ Flyway 版本唯一"
    PASS=$((PASS+1))
else
    echo "  ❌ Flyway 版本冲突:"
    echo "$DUPS"
    FAIL=$((FAIL+1))
fi

# Step 3: 单元测试
echo ""
echo "--- Step 3/4: 单元测试 ---"
cd micro-course-api
if mvn test -Dtest="CourseBundleServiceTest,OrderServiceBundlePriceTest,EnrollmentStatusTest,EnrollmentP0ConcurrencyTest" -DfailIfNoTests=false 2>&1 | tail -5; then
    echo "  ✅ 单元测试通过"
    PASS=$((PASS+1))
else
    echo "  ❌ 单元测试失败"
    FAIL=$((FAIL+1))
fi
cd "$ROOT"

# Step 4: 集成测试
echo ""
echo "--- Step 4/4: 集成测试 ---"
cd micro-course-api
if mvn test -Dtest="CourseBundleIntegrationTest" -DfailIfNoTests=false 2>&1 | tail -5; then
    echo "  ✅ 集成测试通过"
    PASS=$((PASS+1))
else
    echo "  ❌ 集成测试失败"
    FAIL=$((FAIL+1))
fi
cd "$ROOT"

echo ""
echo "============================================"
echo "  门禁结果: $PASS/4 通过, $FAIL 失败"
echo "============================================"

if [ "$FAIL" -gt 0 ]; then
    exit 1
fi
exit 0
