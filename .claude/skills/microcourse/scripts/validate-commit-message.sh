#!/bin/bash
# ═══════════════════════════════════════════════════════════════════════════════
# validate-commit-message.sh
# 总工程师放行纪律 · 纪律 7 的自动化检查
# 在 commit-msg git hook 中调用，也可 CI 阶段调用
# ═══════════════════════════════════════════════════════════════════════════════
# 用法：
#   bash validate-commit-message.sh <commit-msg-file>
#   bash validate-commit-message.sh --check-last   # 检查最近一次 commit
# ═══════════════════════════════════════════════════════════════════════════════

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# ─── 输入 ─────────────────────────────────────────────────────────────────────
if [ "$1" = "--check-last" ]; then
    # 检查最近一次 commit 的 message（CI / 事后检查用）
    MSG=$(git log -1 --format="%B")
    MSG_TYPE="recent commit"
elif [ -n "$1" ]; then
    MSG=$(cat "$1")
    MSG_TYPE="commit message"
else
    echo "Usage: $0 <commit-msg-file> | $0 --check-last"
    exit 1
fi

echo ""
echo "────────────────────────────────────────────"
echo "  validate-commit-message.sh"
echo "  AGENTS.md §总工程师放行纪律 · 纪律 7"
echo "────────────────────────────────────────────"
echo ""

FIRST_LINE=$(echo "$MSG" | head -1)
BODY=$(echo "$MSG" | tail -n +2)

PASS=0
FAIL=0
FAILS=()

# ─── 检查 1：fix 类型 commit 必须有 【根因】 ──────────────────────────────────
if echo "$FIRST_LINE" | grep -qE "^fix\("; then
    if echo "$BODY" | grep -qE "【根因】"; then
        PASS=$((PASS+1))
        echo -e "  ${GREEN}✓${NC} fix commit 含【根因】段"
    else
        FAIL=$((FAIL+1))
        FAILS+=("fix commit 缺少【根因】段。纪律 7 要求 fix 必须填写根因分析")
    fi

    # 检查 2：必须有【验证】段
    if echo "$BODY" | grep -qE "【验证】"; then
        PASS=$((PASS+1))
        echo -e "  ${GREEN}✓${NC} fix commit 含【验证】段"
    else
        FAIL=$((FAIL+1))
        FAILS+=("fix commit 缺少【验证】段。纪律 7 要求 fix 必须填写验证命令及输出")
    fi

    # 检查 3：必须有【防止再发】段
    if echo "$BODY" | grep -qE "【防止再发】"; then
        PASS=$((PASS+1))
        echo -e "  ${GREEN}✓${NC} fix commit 含【防止再发】段"
    else
        FAIL=$((FAIL+1))
        FAILS+=("fix commit 缺少【防止再发】段。纪律 7 要求列出防止再发的措施")
    fi
else
    # 非 fix 类型不需要根因模板
    PASS=$((PASS+3))
    echo -e "  ${YELLOW}∼${NC} 非 fix commit，跳过【根因】模板检查"
fi

# ─── 检查 4：全 commit message 禁用违规表述 ──────────────────────────────────
DISALLOWED_PATTERNS=(
    "应该没问题"
    "大概没问题"
    "看起来没问题"
    "我相信"
    "应该是正确的"
    "应该是对的"
    "不太可能"
)

for pattern in "${DISALLOWED_PATTERNS[@]}"; do
    if echo "$BODY" | grep -q "$pattern"; then
        FAIL=$((FAIL+1))
        FAILS+=("commit body 含违规表述「$pattern」（纪律 1：禁止无证据断言）")
    fi
done
if [ "$FAIL" -eq 0 ]; then
    PASS=$((PASS+1))
fi

# ─── 汇总 ─────────────────────────────────────────────────────────────────────
echo ""
echo "────────────────────────────────────────────"
if [ "$FAIL" -gt 0 ]; then
    echo -e "  ${RED}❌ 验证失败（$PASS 通过 / $FAIL 失败）${NC}"
    for f in "${FAILS[@]}"; do
        echo -e "  ${RED}✗${NC} $f"
    done
    echo "────────────────────────────────────────────"
    echo ""
    echo -e "${RED}commit 被驳回。请按 AGENTS.md §纪律 7 模板补充根因分析。${NC}"
    exit 1
else
    echo -e "  ${GREEN}✅ 全部通过（$PASS 通过 / 0 失败）${NC}"
    echo "────────────────────────────────────────────"
    exit 0
fi
