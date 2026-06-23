#!/bin/bash
# =====================================================
# Phase 14 微专业 · Autopilot 总编排入口
# 版本: v1.0
# 签发: 2026-06-24
# 用法:
#   bash autopilot.sh next                 # 找下一个 PENDING 工单
#   bash autopilot.sh run auditor M1-03    # 派 auditor 角色
#   bash autopilot.sh run investigator M1-03
#   bash autopilot.sh run fixer M1-03
#   bash autopilot.sh batch M1             # 跑整个 M1 模块（9 工单）
#   bash autopilot.sh validate batch-1     # 4 维交叉验证某 batch
#   bash autopilot.sh status               # 显示进度
#   bash autopilot.sh init                 # 重新初始化（不删数据）
# =====================================================

set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
PROGRESS_FILE="$PROJECT_ROOT/.audit-cache/phase14/progress.json"
REPORTS_DIR="$SCRIPT_DIR/reports"
SPEC_DOC="$PROJECT_ROOT/docs/开发规划/phase14-audit-fix-spec.md"

mkdir -p "$REPORTS_DIR"

# 颜色（如果终端支持）
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'

# --------- 工具函数 ---------
log() { echo -e "${BLUE}[autopilot]${NC} $*" >&2; }
ok() { echo -e "${GREEN}[✓]${NC} $*" >&2; }
warn() { echo -e "${YELLOW}[!]${NC} $*" >&2; }
err() { echo -e "${RED}[✗]${NC} $*" >&2; }

die() { err "$@"; exit 1; }

require_file() { [ -f "$1" ] || die "文件不存在: $1"; }

# --------- 子命令 ---------

cmd_status() {
  require_file "$PROGRESS_FILE"
  local total passed failed in_progress pending
  total=$(jq -r '.stats.total' "$PROGRESS_FILE")
  passed=$(jq -r '.stats.passed' "$PROGRESS_FILE")
  failed=$(jq -r '.stats.failed' "$PROGRESS_FILE")
  in_progress=$(jq -r '.stats.in_progress' "$PROGRESS_FILE")
  pending=$(jq -r '.stats.pending' "$PROGRESS_FILE")
  echo "总工单: $total | PASS: $passed | FAIL: $failed | IN_PROGRESS: $in_progress | PENDING: $pending"
  echo ""
  echo "最近 PASS 工单（最近 5 个）:"
  jq -r '.tickets | to_entries | map(select(.value.status == "PASS")) | .[-5:] | .[] | "  ✓ \(.key): \(.value.title)"' "$PROGRESS_FILE"
  echo ""
  echo "下个待跑工单（首个 PENDING）:"
  jq -r '.tickets | to_entries | map(select(.value.status == "PENDING"))[0] | "  → \(.key): \(.value.title) (\(.value.module))"' "$PROGRESS_FILE"
}

cmd_next() {
  require_file "$PROGRESS_FILE"
  local next
  next=$(jq -r '.tickets | to_entries | map(select(.value.status == "PENDING"))[0].key // empty' "$PROGRESS_FILE")
  if [ -z "$next" ]; then
    log "🎉 全部工单已完成！"
    return 0
  fi
  echo "$next"
}

cmd_run() {
  local role="$1"
  local ticket_id="$2"
  [ -z "$role" ] && die "用法: autopilot.sh run <role> <ticket_id>，role ∈ auditor/investigator/fixer"
  [ -z "$ticket_id" ] && die "用法: autopilot.sh run <role> <ticket_id>"

  # 生成任务书（用 Python render-task.py）
  local task_json
  task_json=$(python3 "$SCRIPT_DIR/lib/render-task.py" "$role" "$ticket_id")

  # 输出到 stdout
  echo "$task_json"

  # 保存任务书快照到 reports 目录
  local task_file="$REPORTS_DIR/${ticket_id}_${role}_task_r$(date +%s).json"
  echo "$task_json" > "$task_file"
  log "任务书已生成: $task_file"
}

cmd_batch() {
  local module="$1"
  [ -z "$module" ] && die "用法: autopilot.sh batch M1|M2|M3|M4"
  log "准备批量跑模块 $module ..."
  echo "批量跑模式："
  echo "  1. 主 Agent 派 5 个 auditor 并发（max ${BATCH_AUDITOR_PARALLEL:-5}）"
  echo "  2. 等所有报告 → 派 5 个 investigator 并发"
  echo "  3. 等所有报告 → 派 5 个 fixer 并发"
  echo "  4. 4 维交叉验证（4 reviewer 并发）"
  echo ""
  echo "⚠️  请主 Agent 主动驱动此流程，autopilot.sh 不直接执行 LLM 调用。"
  echo ""
  echo "下一步：在主 Agent 中调用"
  echo "  bash $SCRIPT_DIR/autopilot.sh status"
  echo "  bash $SCRIPT_DIR/autopilot.sh next"
  echo "  bash $SCRIPT_DIR/autopilot.sh run auditor <ticket_id>"
}

cmd_validate() {
  local batch_id="$1"
  [ -z "$batch_id" ] && die "用法: autopilot.sh validate <batch-id>，如 batch-1"
  log "启动 4 维交叉验证: $batch_id"
  echo "R1-R4 reviewer 子 Agent 任务书："
  echo "  R1: bash $SCRIPT_DIR/lib/cross-validate.sh r1 $batch_id"
  echo "  R2: bash $SCRIPT_DIR/lib/cross-validate.sh r2 $batch_id"
  echo "  R3: bash $SCRIPT_DIR/lib/cross-validate.sh r3 $batch_id"
  echo "  R4: bash $SCRIPT_DIR/lib/cross-validate.sh r4 $batch_id"
}

# --------- 入口 ---------
case "${1:-status}" in
  status)    cmd_status ;;
  next)      cmd_next ;;
  run)       cmd_run "$2" "$3" ;;
  batch)     cmd_batch "$2" ;;
  validate)  cmd_validate "$2" ;;
  init)      log "初始化检查"; cmd_status ;;
  *)         die "未知命令: $1。用法: status|next|run|batch|validate|init" ;;
esac
