#!/bin/bash
# ===================================================================
# sed-mutation-test.sh — 变异测试工具
#
# 通过对源文件做变异（如反转条件、删除保护语句），然后运行测试，
# 验证现有测试是否能捕获变异。若测试未能捕获（变异通过），说明
# 测试覆盖不足。
#
# 用法: bash tools/sed-mutation-test.sh <target_java_file> [test_method]
# 示例: bash tools/sed-mutation-test.sh service/impl/EnrollmentServiceImpl.java
# ===================================================================

set -euo pipefail

TARGET_FILE="${1:?用法: bash tools/sed-mutation-test.sh <target_java_file> [test_method]}"
TEST_METHOD="${2:-}"
PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

echo "============================================"
echo "  Sed Mutation Test"
echo "  文件: $TARGET_FILE"
echo "  项目: $PROJECT_DIR"
echo "============================================"

# 变异操作符定义
MUTATIONS=(
    # name, search_pattern, replacement, description
    "negate_if_null:if \(.*== null\):if \(.*!= null\):反转 null 检查"
    "negate_if_not_null:if \(.*!= null\):if \(.*== null\):反转非 null 检查"
    "remove_throw:throw new BusinessException:// MUTATED: throw removed:删除业务异常抛出"
    "remove_return_false:return false:return true:反转 false 返回"
    "remove_return_true:return true:return false:反转 true 返回"
    "flip_gt:> :< :反转大于号"
    "flip_gte:>=:<=:反转大于等于"
    "remove_null_check:== null) {:// MUTATED: null check removed:删除 null 检查"
)

TOTAL=0
KILLED=0
SURVIVED=0

for mutation in "${MUTATIONS[@]}"; do
    NAME="${mutation%%:*}"
    REMAINDER="${mutation#*:}"
    SEARCH="${REMAINDER%%:*}"
    REMAINDER2="${REMAINDER#*:}"
    REPLACE="${REMAINDER2%%:*}"
    DESC="${REMAINDER2#*:}"

    # 检查目标文件中是否存在搜索模式
    if ! grep -q "$SEARCH" "$TARGET_FILE" 2>/dev/null; then
        continue
    fi

    TOTAL=$((TOTAL + 1))
    echo ""
    echo "--- [$TOTAL] $NAME: $DESC ---"

    # 备份原始文件
    cp "$TARGET_FILE" "${TARGET_FILE}.bak"

    # 应用变异
    sed -i '' "s/$SEARCH/$REPLACE/" "$TARGET_FILE" 2>/dev/null || {
        echo "  ⚠️  sed 替换失败，跳过"
        cp "${TARGET_FILE}.bak" "$TARGET_FILE"
        rm -f "${TARGET_FILE}.bak"
        continue
    }

    # 编译并运行测试
    if [ -n "$TEST_METHOD" ]; then
        if mvn test -q -Dtest="$TEST_METHOD" -f "$PROJECT_DIR/micro-course-api/pom.xml" 2>/dev/null; then
            echo "  ❌ SURVIVED: 变异未被测试捕获"
            SURVIVED=$((SURVIVED + 1))
        else
            echo "  ✅ KILLED: 测试捕获了变异"
            KILLED=$((KILLED + 1))
        fi
    else
        # 仅编译验证
        if mvn compile -q -f "$PROJECT_DIR/micro-course-api/pom.xml" 2>/dev/null; then
            echo "  ⚡ 编译通过（未运行测试）"
        else
            echo "  ❌ 编译失败（变异导致语法错误）"
        fi
    fi

    # 恢复原始文件
    cp "${TARGET_FILE}.bak" "$TARGET_FILE"
    rm -f "${TARGET_FILE}.bak"
done

echo ""
echo "============================================"
echo "  结果: $KILLED/$TOTAL killed"
if [ "$TOTAL" -gt 0 ]; then
    RATE=$((KILLED * 100 / TOTAL))
    echo "  杀伤率: ${RATE}%"
    if [ "$RATE" -ge 80 ]; then
        echo "  ✅ 测试覆盖充足 (≥80%)"
    else
        echo "  ⚠️  测试覆盖不足，建议补充测试"
    fi
fi
echo "============================================"
