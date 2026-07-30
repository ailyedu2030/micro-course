#!/bin/bash
# =============================================================================
# 生产部署门禁 — 防止 AI 绕过本地验证直接操作生产
# =============================================================================
# 原理: local-dev-deploy.sh 全部 PASS 后自动开门(写入时间戳)。
#      任何生产操作前必须 check 门是否开着。门关着 → 阻断。
#
# 用法:
#   bash scripts/deploy-gate.sh check   # 检查门是否开 (exit 0/1)
#   bash scripts/deploy-gate.sh open    # 开门 (local-dev-deploy.sh 自动调用)
#   bash scripts/deploy-gate.sh close   # 关门 (可选)
#   bash scripts/deploy-gate.sh status  # 查看门状态
# =============================================================================
set -euo pipefail

GATE_FILE=".production-gate"
PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
GATE_PATH="$PROJECT_ROOT/$GATE_FILE"
GATE_TTL=240  # 开门后 4 小时内有效 (分钟)

case "${1:-check}" in
  open)
    date +%s > "$GATE_PATH"
    echo "✅ 生产门禁已打开 ($(date))"
    echo "   有效期: ${GATE_TTL} 分钟"
    ;;
  close)
    rm -f "$GATE_PATH"
    echo "🔒 生产门禁已关闭"
    ;;
  status)
    if [ ! -f "$GATE_PATH" ]; then
      echo "🔒 门禁状态: 关闭"
      echo ""
      echo "   请先运行: bash scripts/local-dev-deploy.sh"
      echo "   全部 PASS 后门禁自动打开，方可操作生产"
      exit 1
    fi
    open_ts=$(cat "$GATE_PATH")
    now=$(date +%s)
    elapsed=$(( (now - open_ts) / 60 ))
    remaining=$(( GATE_TTL - elapsed ))
    if [ $remaining -gt 0 ]; then
      echo "✅ 门禁状态: 开启 (已开 ${elapsed}m, 剩余 ${remaining}m)"
      echo "   开门时间: $(date -r "$open_ts" '+%Y-%m-%d %H:%M:%S')"
    else
      echo "❌ 门禁状态: 已过期 (已开 ${elapsed}m, 超时 $(( elapsed - GATE_TTL ))m)"
      rm -f "$GATE_PATH"
      exit 1
    fi
    # R5: 部署前验证 CHANGE_ME 占位符
    echo ""
    echo "   ── 部署密钥占位符检查 (R5) ──"
    if bash scripts/verify-secrets.sh --strict > /tmp/verify-secrets.out 2>&1; then
      echo "   ✅ 部署密钥占位符检查通过"
    else
      echo "   ❌ 部署密钥占位符检查失败 — 阻断部署"
      cat /tmp/verify-secrets.out | tail -20
      rm -f /tmp/verify-secrets.out
      echo "   ────────────────────────────"
      echo "   请修复后重新开门: bash scripts/local-dev-deploy.sh"
      exit 1
    fi
    rm -f /tmp/verify-secrets.out
    echo "   ────────────────────────────"
    ;;
  check)
    if [ ! -f "$GATE_PATH" ]; then
      echo "❌ [门禁] 生产部署门禁已关闭!"
      echo ""
      echo "   你必须先在本地隔离环境验证:"
      echo "   ─────────────────────────────────────────"
      echo "   bash scripts/local-dev-deploy.sh"
      echo "   ─────────────────────────────────────────"
      echo "   全部 PASS 后门禁自动打开,才能操作生产。"
      echo ""
      echo "   不要跳过这个步骤。直接操作生产 = P0 事故。"
      exit 1
    fi
    open_ts=$(cat "$GATE_PATH")
    now=$(date +%s)
    elapsed=$(( (now - open_ts) / 60 ))
    if [ $elapsed -gt $GATE_TTL ]; then
      echo "❌ [门禁] 门禁已过期 (已开 ${elapsed}m, 超时 $(( elapsed - GATE_TTL ))m)"
      rm -f "$GATE_PATH"
      echo "   请重新运行 bash scripts/local-dev-deploy.sh"
      exit 1
    fi
    echo "✅ [门禁] 门禁有效 (已开 ${elapsed}m, 剩余 $(( GATE_TTL - elapsed ))m)"
    # R5: 部署前验证 CHANGE_ME 占位符
    echo ""
    echo "   ── 部署密钥占位符检查 (R5) ──"
    if bash scripts/verify-secrets.sh --strict > /tmp/verify-secrets.out 2>&1; then
      echo "   ✅ 部署密钥占位符检查通过"
    else
      echo "   ❌ 部署密钥占位符检查失败 — 阻断部署"
      cat /tmp/verify-secrets.out | tail -20
      rm -f /tmp/verify-secrets.out
      echo "   ────────────────────────────"
      echo "   请修复后重新开门: bash scripts/local-dev-deploy.sh"
      exit 1
    fi
    rm -f /tmp/verify-secrets.out
    echo "   ────────────────────────────"
    exit 0
    ;;
  *)
    echo "用法: $0 {check|open|close|status}"
    exit 1
    ;;
esac