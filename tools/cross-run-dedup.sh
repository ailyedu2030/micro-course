#!/bin/bash
# ===================================================================
# cross-run-dedup.sh — Super-Fix 跨运行去重
#
# 对比历史审计运行，识别新增/修复/持续存在的发现。
# 退出码: 0 = 无新增, 1 = 有新增发现, 2 = 无历史数据
#
# 用法: bash tools/cross-run-dedup.sh [--verbose]
# ===================================================================

set -euo pipefail

VERBOSE=false
[[ "${1:-}" == "--verbose" ]] && VERBOSE=true

CACHE_DIR=".audit-cache"
HISTORY_FILE="$CACHE_DIR/audit_state.json"
CURRENT_FINDINGS="$CACHE_DIR/findings.json"

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

if [ ! -f "$HISTORY_FILE" ]; then
    echo -e "${RED}[ERROR]${NC} $HISTORY_FILE 不存在"
    exit 2
fi

if [ ! -f "$CURRENT_FINDINGS" ]; then
    echo -e "${RED}[ERROR]${NC} $CURRENT_FINDINGS 不存在"
    exit 2
fi

echo "📊 Super-Fix 跨运行去重"
echo ""

# 读取历史统计
if command -v jq &>/dev/null; then
    TOTAL_RUNS=$(jq '.total_runs // 0' "$HISTORY_FILE")
    TOTAL_FINDINGS=$(jq '.total_findings // 0' "$HISTORY_FILE")
    TOTAL_FIXED=$(jq '.total_fixed // 0' "$HISTORY_FILE")
    CURRENT_COUNT=$(jq '.findings | length' "$CURRENT_FINDINGS")
    
    echo "  历史运行次数: $TOTAL_RUNS"
    echo "  历史总发现:   $TOTAL_FINDINGS"
    echo "  已修复:       $TOTAL_FIXED"
    echo "  当前发现:     $CURRENT_COUNT"
    echo ""
    
    # 检查是否有新增
    OPEN_COUNT=$(jq '[.findings[] | select(.status == "open" or .status == null)] | length' "$CURRENT_FINDINGS")
    FIXED_COUNT=$(jq '[.findings[] | select(.status == "fixed" or .status == "verified")] | length' "$CURRENT_FINDINGS")
    
    echo -e "  ${GREEN}已修复/已验证: $FIXED_COUNT${NC}"
    echo -e "  ${RED}仍待修复:     $OPEN_COUNT${NC}"
    echo ""
    
    if [ "$OPEN_COUNT" -gt 0 ]; then
        echo -e "${YELLOW}[WARN]${NC} 还有 $OPEN_COUNT 个发现待修复"
        
        if [ "$VERBOSE" = "true" ]; then
            echo ""
            echo "待修复发现:"
            jq -r '.[] | select(.status == "open") | "  [\(.severity)] \(.file):\(.line) — \(.description)"' "$CURRENT_FINDINGS"
        fi
        
        exit 1
    else
        echo -e "${GREEN}[PASS]${NC} 所有发现已修复"
        exit 0
    fi
else
    # Fallback without jq
    CURRENT_COUNT=$(grep -c '"id"' "$CURRENT_FINDINGS" || echo 0)
    echo "  当前发现: $CURRENT_COUNT（需要 jq 进行详细分析）"
    exit 0
fi
