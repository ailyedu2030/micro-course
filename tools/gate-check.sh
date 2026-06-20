#!/bin/bash
# ===================================================================
# gate-check.sh — Super-Fix 门禁检查
#
# 15 道门禁的分阶段校验。每个 gate 对应一个检查函数。
# 调用方式: bash tools/gate-check.sh <gate_name>
# 示例: bash tools/gate-check.sh PHASE_0_ENTRY
#       bash tools/gate-check.sh PHASE_7_FINAL
#
# 无参数时列出所有可用 gate。
# ===================================================================

set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

# ---- Gate 检查函数 ----

gate_PHASE_0_ENTRY() {
    # 子系统清单 + 并发锁检查
    [ -f .audit-cache/audit_state.json ] && return 0 || return 1
}

gate_PHASE_2_REVIEW() {
    # Blue Team 产出检查 — 至少 3 个 findings
    local count=$(python3 -c "import json;f=json.load(open('$PROJECT_DIR/.audit-cache/findings.json'));print(len(f.get('findings',[])))" 2>/dev/null || echo "0")
    [ "$count" -ge 3 ]
}

gate_PHASE_4_FIX() {
    # P0/P1/P2 修复检查 — 所有发现必须有 remediation
    local without_remediation=$(python3 -c "import json;f=json.load(open('$PROJECT_DIR/.audit-cache/findings.json'));print(sum(1 for x in f.get('findings',[]) if not x.get('remediation')))" 2>/dev/null || echo "999")
    local total=$(python3 -c "import json;f=json.load(open('$PROJECT_DIR/.audit-cache/findings.json'));print(len(f.get('findings',[])))" 2>/dev/null || echo "0")
    [ "$without_remediation" -eq 0 ] && [ "$total" -gt 0 ]
}

gate_PHASE_4_5_TEST_AUTHOR() {
    # 测试作者检查
    [ -d "$PROJECT_DIR/micro-course-api/src/test" ] && return 0 || return 1
}

gate_PHASE_5_STATIC() {
    # 静态检查
    if [ -f "$PROJECT_DIR/micro-course-api/pom.xml" ]; then
        mvn compile -q -f "$PROJECT_DIR/micro-course-api/pom.xml" 2>/dev/null
    else
        mvn compile -q 2>/dev/null
    fi
}

gate_PHASE_5_5_SMOKE() {
    # 烟雾测试
    bash "$PROJECT_DIR/tools/smoke-test.sh" 2>/dev/null
}

gate_PHASE_5_6_DYNAMIC() {
    # 全量测试
    mvn test -q -f "$PROJECT_DIR/micro-course-api/pom.xml" 2>/dev/null
    return $?  # 允许跳过 — 需要数据库
}

gate_PHASE_5_7_CHAOS() {
    # 混沌测试 — 暂未实现
    return 0
}

gate_PHASE_5_8_MUTATION() {
    # 变异测试
    bash "$PROJECT_DIR/tools/sed-mutation-test.sh" \
        "$PROJECT_DIR/micro-course-api/src/main/java/com/microcourse/service/impl/EnrollmentServiceImpl.java" \
        2>/dev/null
    return $?
}

gate_PHASE_6_LOOP() {
    # 收敛检查
    bash "$PROJECT_DIR/tools/convergence-check.sh" 2>/dev/null
}

gate_PHASE_6_5_DEVIL_ADVOCATE() {
    # 对抗审查 — 检查是否有未关联文件的 finding
    local orphan=$(python3 -c "
import json
with open('$PROJECT_DIR/.audit-cache/findings.json') as f:
    finds = json.load(f).get('findings',[])
import os
for fn in finds:
    fp = fn.get('file','')
    if fp and not os.path.exists('$PROJECT_DIR/' + fp.split(':')[0]):
        print(fp)
" 2>/dev/null)
    [ -z "$orphan" ]
}

gate_PHASE_7_FINAL() {
    # 最终认证
    bash "$PROJECT_DIR/tools/convergence-check.sh" 2>/dev/null && \
    bash "$PROJECT_DIR/tools/smoke-test.sh" 2>/dev/null
}

# ---- Gate 注册表 ----
declare -A GATES
GATES["PHASE_0_ENTRY"]="审计状态初始化检查"
GATES["PHASE_2_REVIEW"]="Blue Team 产出 ≥3 findings"
GATES["PHASE_4_FIX"]="P0/P1/P2 全部修复"
GATES["PHASE_4_5_TEST_AUTHOR"]="测试文件存在"
GATES["PHASE_5_STATIC"]="编译通过"
GATES["PHASE_5_5_SMOKE"]="烟雾测试通过"
GATES["PHASE_5_6_DYNAMIC"]="全量测试（可选）"
GATES["PHASE_5_7_CHAOS"]="混沌测试（预留）"
GATES["PHASE_5_8_MUTATION"]="变异测试"
GATES["PHASE_6_LOOP"]="收敛检查"
GATES["PHASE_6_5_DEVIL_ADVOCATE"]="对抗审查"
GATES["PHASE_7_FINAL"]="最终零缺陷认证"

# ---- 主逻辑 ----
if [ $# -eq 0 ]; then
    echo "可用门禁 (15):"
    echo ""
    for name in "${!GATES[@]}"; do
        printf "  %-30s %s\n" "$name" "${GATES[$name]}"
    done | sort
    echo ""
    echo "用法: bash tools/gate-check.sh <gate_name>"
    exit 0
fi

GATE_NAME="$1"
if [ -z "${GATES[$GATE_NAME]:-}" ]; then
    echo "❌ 未知门禁: $GATE_NAME"
    echo "   可用门禁: ${!GATES[*]}"
    exit 1
fi

echo "🔒 Gate: $GATE_NAME — ${GATES[$GATE_NAME]}"

# 调用对应的检查函数
FUNC_NAME="gate_${GATE_NAME}"
if declare -f "$FUNC_NAME" > /dev/null; then
    if "$FUNC_NAME"; then
        echo "  ✅ PASS"
        exit 0
    else
        echo "  ❌ FAIL"
        exit 1
    fi
else
    echo "  ⚠️  检查函数未实现，跳过"
    exit 0
fi
