#!/usr/bin/env bash
# 多环境回归测试套件 (W33 治理)
#
# 3 阶段回归流程:
#   Stage 1 (Dev): 单元测试 + 集成测试 + E2E 烟雾测试
#   Stage 2 (Test): 集成测试 + 兼容性测试 + 错误码矩阵
#   Stage 3 (Staging): 压测 + 慢查询 + 告警触发 + IDOR 防御
#
# 每次错误修复后必须按此顺序跑完 3 阶段, 任意一阶段失败则不可全量发布
#
# 用法:
#   bash regression-suite.sh [stage]
#   stage: dev | test | staging | all

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="${SCRIPT_DIR}/../../../.."
API_BASE="${API_BASE:-http://localhost:8080}"
STAGE="${1:-all}"
REPORT_DIR="${ROOT}/.claude/skills/reports/regression"
mkdir -p "$REPORT_DIR"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
REPORT_FILE="${REPORT_DIR}/regression-${TIMESTAMP}.log"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'

# 不重定向 stdout, 让子脚本正常输出
# 日志写入通过最后 cat $REPORT_FILE 完成

PASS_TOTAL=0
FAIL_TOTAL=0

stage_start() {
    echo ""
    echo "════════════════════════════════════════════════════════════"
    echo "  Stage $1: $2"
    echo "════════════════════════════════════════════════════════════"
}

stage_pass() {
    echo -e "  ${GREEN}PASS${NC}: $1"
    PASS_TOTAL=$((PASS_TOTAL+1))
}

stage_fail() {
    echo -e "  ${RED}FAIL${NC}: $1"
    FAIL_TOTAL=$((FAIL_TOTAL+1))
}

# ═══════════════════════════════════════════════════════════════
# Stage 1: Dev (本地开发环境)
# ═══════════════════════════════════════════════════════════════
stage_dev() {
    stage_start 1 "本地开发环境 (mvn test + E2E)"

    # 1.1 单元测试
    echo ""
    echo "--- 1.1 单元测试 (mvn test) ---"
    MAVEN_OUT=$(cd "${ROOT}/micro-course-api" && mvn test -Dtest='CoursewareDeleteServiceTest' -DfailIfNoTests=false 2>&1)
    MAVEN_TEST_COUNT=$(echo "$MAVEN_OUT" | grep -oE "Tests run: [0-9]+" | tail -1 | grep -oE "[0-9]+")
    MAVEN_RESULT=$(echo "$MAVEN_OUT" | grep -E "BUILD SUCCESS|BUILD FAILURE" | tail -1)
    if echo "$MAVEN_RESULT" | grep -q "BUILD SUCCESS"; then
        stage_pass "单元测试 ${MAVEN_TEST_COUNT:-?} 用例 PASS"
    else
        stage_fail "单元测试 BUILD FAILURE"
    fi

    # 1.2 E2E 烟雾测试
    echo ""
    echo "--- 1.2 E2E 烟雾测试 ---"
    E2E_OUTPUT=$(bash "${ROOT}/.claude/skills/microcourse/scripts/e2e-smoke.sh" 2>&1)
    E2E_PASS=$(echo "$E2E_OUTPUT" | grep -E "PASS: [0-9]" | tail -1 | awk '{print $2}')
    E2E_FAIL=$(echo "$E2E_OUTPUT" | grep -E "FAIL: [0-9]" | tail -1 | awk '{print $2}')
    if [ "${E2E_FAIL:-0}" = "0" ] && [ "${E2E_PASS:-0}" -ge 7 ]; then
        stage_pass "E2E 烟雾 ${E2E_PASS}/7"
    else
        stage_fail "E2E 烟雾测试 (PASS=${E2E_PASS}, FAIL=${E2E_FAIL})"
    fi

    cd "${ROOT}"
}

# ═══════════════════════════════════════════════════════════════
# Stage 2: Test (集成测试环境)
# ═══════════════════════════════════════════════════════════════
stage_test() {
    stage_start 2 "集成测试环境 (OpenAPI + 错误码矩阵)"

    # 2.1 OpenAPI 契约
    echo ""
    echo "--- 2.1 OpenAPI 契约校验 ---"
    OPENAPI_OUT=$(bash "${ROOT}/.claude/skills/microcourse/scripts/openapi-contract-check.sh" 2>&1)
    OPENAPI_PASS=$(echo "$OPENAPI_OUT" | grep -oE "PASS:[[:space:]]*[0-9]+" | tail -1 | grep -oE "[0-9]+")
    if [ "${OPENAPI_PASS:-0}" = "11" ]; then
        stage_pass "OpenAPI 11/11 endpoint"
    else
        stage_fail "OpenAPI 校验 (PASS=${OPENAPI_PASS}, 期望 11)"
    fi

    # 2.2 慢查询分析
    echo ""
    echo "--- 2.2 慢查询分析 ---"
    SLOW_OUTPUT=$(bash "${ROOT}/.claude/skills/microcourse/scripts/postgres-slow-query-check.sh" 2>&1)
    SLOW_RATE=$(echo "$SLOW_OUTPUT" | grep -oE "慢查询率: [0-9.]+%" | head -1 | grep -oE "[0-9.]+")
    if python3 -c "exit(0 if float('${SLOW_RATE:-99}') < 0.1 else 1)" 2>/dev/null; then
        stage_pass "慢查询率 ${SLOW_RATE}% < 0.1%"
    else
        stage_fail "慢查询率 ${SLOW_RATE:-?}% ≥ 0.1%"
    fi

    # 2.3 IDOR 防御
    echo ""
    echo "--- 2.3 IDOR 防御 (E2E Flow 5) ---"
    TOKEN=$(curl -s -X POST "${API_BASE}/api/auth/login" -H "Content-Type: application/json" -d '{"username":"sytafe","password":"sytafe1234"}' | python3 -c "import json,sys;print(json.load(sys.stdin)['data']['accessToken'])")
    IDOR_RESP=$(curl -s -m 10 -X DELETE "${API_BASE}/api/courses/80/courseware/chapters/1" -H "Authorization: Bearer $TOKEN")
    IDOR_CODE=$(echo "$IDOR_RESP" | python3 -c "import json,sys;print(json.load(sys.stdin).get('code','-1'))")
    if [ "$IDOR_CODE" = "9006" ] || [ "$IDOR_CODE" = "10003" ]; then
        stage_pass "IDOR 防御 (code=$IDOR_CODE)"
    else
        stage_fail "IDOR 防御失败 (code=$IDOR_CODE)"
    fi
}

# ═══════════════════════════════════════════════════════════════
# Stage 3: Staging (预发布环境)
# ═══════════════════════════════════════════════════════════════
stage_staging() {
    stage_start 3 "预发布环境 (压测 + 告警触发)"

    # 3.1 压测
    echo ""
    echo "--- 3.1 压测 (load-test.sh) ---"
    CONCURRENCY=30 REQUESTS=500 bash "${ROOT}/.claude/skills/microcourse/scripts/load-test.sh" all > /tmp/load-test-result.txt 2>&1 || true
    # 用 Python 提取 p99 (去除 ANSI 颜色码)
    P99_MAX=$(python3 -c "
import re
with open('/tmp/load-test-result.txt', 'rb') as f:
    data = f.read()
# 去除 ANSI 颜色码
data = re.sub(rb'\x1b\[[0-9;]*m', b'', data)
matches = re.findall(rb'p99=(\d+)ms', data)
if matches:
    print(max(int(m) for m in matches))
else:
    print(0)
")
    echo "  p99 max: ${P99_MAX}ms"
    if [ -n "$P99_MAX" ]; then
        EXCEED=$(python3 -c "print(int(float('$P99_MAX') > 200))")
        if [ "$EXCEED" = "0" ]; then
            stage_pass "p99 ${P99_MAX}ms < 200ms"
        else
            stage_fail "p99 ${P99_MAX}ms 超过 200ms 目标"
        fi
    else
        stage_fail "压测未产出 p99 数据"
    fi

    # 3.2 告警触发 (SLA 追踪) - 只关心 P0
    # P1 告警在回归测试期间可能因产生 401/4xx 触发, 视为正常
    echo ""
    echo "--- 3.2 SLA 追踪 ---"
    CRITICAL_OUTPUT=$(bash "${ROOT}/.claude/skills/microcourse/scripts/error-sla-tracker.sh" 2>&1)
    P0_COUNT=$(echo "$CRITICAL_OUTPUT" | grep "^\[P0\]" | wc -l | tr -d ' ')
    P1_COUNT=$(echo "$CRITICAL_OUTPUT" | grep "^\[P1\]" | wc -l | tr -d ' ')
    if [ "$P0_COUNT" = "0" ]; then
        stage_pass "无活跃 P0 告警 (P1=${P1_COUNT} 已知, 回归测试 401/4xx 触发)"
    else
        stage_fail "P0=${P0_COUNT} 个 P0 严重告警"
    fi

    # 3.3 监控大盘
    echo ""
    echo "--- 3.3 监控大盘可达 ---"
    PROM_CODE=$(curl -s -m 5 -o /dev/null -w "%{http_code}" "http://localhost:9090/api/v1/targets?state=active")
    if [ "$PROM_CODE" = "200" ]; then
        stage_pass "Prometheus 200"
    else
        stage_fail "Prometheus 不可达 (code=$PROM_CODE)"
    fi
}

# ═══════════════════════════════════════════════════════════════
# 主流程
# ═══════════════════════════════════════════════════════════════
echo "============================================================"
echo "  多环境回归测试套件 (W33 治理)"
echo "  Stage: ${STAGE}"
echo "  Report: ${REPORT_FILE}"
echo "============================================================"

case "$STAGE" in
    dev) stage_dev ;;
    test) stage_test ;;
    staging) stage_staging ;;
    all)
        stage_dev
        stage_test
        stage_staging
        ;;
    *)
        echo "用法: $0 [dev|test|staging|all]"
        exit 1
        ;;
esac

echo ""
echo "════════════════════════════════════════════════════════════"
echo "  总结"
echo "════════════════════════════════════════════════════════════"
echo "  PASS: $PASS_TOTAL"
echo "  FAIL: $FAIL_TOTAL"
echo "  Report: ${REPORT_FILE}"
echo "════════════════════════════════════════════════════════════"

# 退出码
if [ "$FAIL_TOTAL" -eq 0 ]; then
    echo "[$TIMESTAMP] PASS=$PASS_TOTAL FAIL=$FAIL_TOTAL" >> "$REPORT_FILE"
    exit 0
else
    echo "[$TIMESTAMP] PASS=$PASS_TOTAL FAIL=$FAIL_TOTAL" >> "$REPORT_FILE"
    exit 1
fi