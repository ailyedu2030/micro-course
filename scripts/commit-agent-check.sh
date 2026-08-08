#!/bin/bash
# Commit Agent 工作区状态判定脚本
# 解决历史误判问题：不要看 git reflog（历史不代表当前状态）
# 用法: bash scripts/commit-agent-check.sh [expected_branch]

set -e

EXPECTED_BRANCH=${1:-}

echo "═══════════════════════════════════════════════════════════════"
echo "Commit Agent 工作区状态判定"
echo "═══════════════════════════════════════════════════════════════"
echo ""

# 第 1 步: git status --short
echo "--- 第 1 步: 工作区文件状态 ---"
status_short=$(git status --short)
file_count=0
if [ -n "$status_short" ]; then
    file_count=$(printf '%s\n' "$status_short" | wc -l | tr -d ' ')
fi
echo "$status_short"
echo ""
echo "工作区文件数（含 M/??/D）: $file_count"
echo ""

# 第 2 步: git diff --stat
echo "--- 第 2 步: 修改统计 ---"
diff_stat=$(git diff --stat)
echo "$diff_stat"
echo ""

# 第 3 步: 未跟踪文件
echo "--- 第 3 步: 未跟踪文件 ---"
untracked=$(git ls-files --others --exclude-standard)
echo "$untracked"
echo ""

# 第 4 步: 当前分支
current_branch=$(git branch --show-current)
echo "--- 当前分支: $current_branch ---"
if [ -n "$EXPECTED_BRANCH" ] && [ "$current_branch" != "$EXPECTED_BRANCH" ]; then
    echo "⚠️ 警告: 当前分支 $current_branch 与期望 $EXPECTED_BRANCH 不符"
fi
echo ""

# 最终判定
echo "═══════════════════════════════════════════════════════════════"
echo "最终判定"
echo "═══════════════════════════════════════════════════════════════"
if [ "$file_count" -gt 0 ] 2>/dev/null; then
    echo "✅ 工作区有 $file_count 个文件待处理 → 继续 commit"
    exit 0
else
    diff_count=$(git diff --stat | wc -l | tr -d ' ')
    if [ "$diff_count" -gt 0 ] 2>/dev/null; then
        echo "⚠️ git diff --stat 有输出但 status 0 行（极少见，可能 staged 文件）"
        echo "继续 commit"
        exit 0
    else
        echo "❌ 工作区真的空 → 停止 commit"
        exit 1
    fi
fi