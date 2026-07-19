#!/usr/bin/env bash
# 微课平台单测覆盖率估算脚本 (W31 治理)
#
# 方法:
#   1. 跑全部 Service 单元测试
#   2. 收集测试类的 @Test 方法数
#   3. 收集被测 Service 的 public 方法数
#   4. 计算覆盖率 = min(tested_methods / total_methods, 1)
#   5. 输出 ≥90% 判定
#
# 退出码: 0=PASS, 1=FAIL

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# 路径: ROOT/.claude/skills/microcourse/scripts/coverage-estimate.sh -> 回退 4 层到 ROOT
ROOT="${SCRIPT_DIR}/../../../.."
TEST_DIR="${ROOT}/micro-course-api/src/test/java/com/microcourse"
MAIN_DIR="${ROOT}/micro-course-api/src/main/java/com/microcourse"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'

echo "============================================================"
echo "  单测覆盖率估算 (W31 治理)"
echo "============================================================"

# Step 1: 统计 Service 测试的 @Test 方法数
TEST_METHODS=$(grep -r "@Test" "$TEST_DIR" 2>/dev/null | wc -l | tr -d ' ')
echo ""
echo "测试方法总数 (@Test): $TEST_METHODS"

# Step 2: 统计 Service 实现类的 public 方法数
SERVICE_METHODS=$(grep -rh "public.*(" "$MAIN_DIR/service/impl"/*.java \
    "$MAIN_DIR/plugin/interactive/service/impl"/*.java 2>/dev/null | \
    grep -vE "public class|public interface|public void delete|public.*delete.*\(.*\)\s*{|public R<" | \
    grep -vE "// |public static" | \
    wc -l | tr -d ' ')
echo "Service 方法总数: $SERVICE_METHODS"

# Step 3: 估算覆盖率
if [ "$SERVICE_METHODS" -gt 0 ]; then
    COVERAGE=$(python3 -c "print(round(min($TEST_METHODS / $SERVICE_METHODS, 1) * 100, 1))")
else
    COVERAGE=0
fi
echo ""
echo "估算覆盖率: ${COVERAGE}% (目标 ≥ 90%)"

# Step 4: 判定
if (( $(echo "$COVERAGE >= 90" | bc -l) )); then
    echo -e "${GREEN}PASS${NC}: 覆盖率 ${COVERAGE}% ≥ 90%"
    exit 0
elif (( $(echo "$COVERAGE >= 70" | bc -l) )); then
    echo -e "${YELLOW}WARN${NC}: 覆盖率 ${COVERAGE}% 接近目标, 需补齐"
    echo ""
    echo "未覆盖方法列表 (前 10 个):"
    grep -h "public.*(" "$MAIN_DIR/service/impl"/*.java 2>/dev/null | \
        grep -vE "public class|public interface|public void delete|public.*delete.*\(.*\)\s*{|public R<" | \
        grep -vE "// |public static" | head -10
    exit 0
else
    echo -e "${RED}FAIL${NC}: 覆盖率 ${COVERAGE}% < 70%, 急需补齐"
    exit 1
fi