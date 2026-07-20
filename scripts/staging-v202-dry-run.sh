#!/usr/bin/env bash
# =====================================================================
# staging-v202-dry-run.sh
# V202 schema 迁移 staging 环境预演脚本
#
# 决策 D4 · 责任: AI · 完成节点: 2026-07-21
# 关联: DECISION-2026-07-20.md / PR #32 V202 migration
#
# 用法:
#   bash scripts/staging-v202-dry-run.sh [--execute] [--staging-host <host>]
#
#   --execute    实际执行迁移（默认仅 dry-run 输出影响评估）
#   --staging-host   staging 数据库主机地址（默认从环境变量读取）
# =====================================================================

set -euo pipefail

# ---- 配置 ----
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
V202_SQL="${PROJECT_ROOT}/micro-course-api/src/main/resources/db/migration/V202__chapter_teacher_teacher_id_nullable.sql"
BACKUP_DIR="${PROJECT_ROOT}/backups/staging"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

# staging 数据库配置（通过环境变量或默认值）
STAGING_HOST="${STAGING_HOST:-${STAGING_DB_HOST:-}}"
STAGING_PORT="${STAGING_PORT:-5432}"
STAGING_DB="${STAGING_DB:-microcourse}"
STAGING_USER="${STAGING_USER:-microcourse}"
STAGING_PASSWORD="${STAGING_PASSWORD:-}"

# ---- 颜色 ----
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }

# ---- 参数解析 ----
DRY_RUN=true
while [[ $# -gt 0 ]]; do
    case "$1" in
        --execute) DRY_RUN=false; shift ;;
        --staging-host) STAGING_HOST="$2"; shift 2 ;;
        *) log_error "未知参数: $1"; exit 1 ;;
    esac
done

# ---- 前置检查 ----
echo "============================================"
echo "  V202 Staging Dry-Run"
echo "  $(date '+%Y-%m-%d %H:%M:%S')"
echo "============================================"

# 1. 确认不是生产环境
if [[ "$STAGING_HOST" =~ (100\.74\.122\.13|microcourse\.ailyedu\.cn) ]]; then
    log_error "🚨 检测到生产环境标识！此脚本仅用于 staging。"
    log_error "   目标: $STAGING_HOST"
    log_error "   生产环境禁止执行 V202 迁移 dry-run。"
    log_error "   请确认 staging 环境地址后重试。"
    exit 1
fi

# 2. 确认 V202 SQL 文件存在
if [[ ! -f "$V202_SQL" ]]; then
    log_error "V202 SQL 文件不存在: $V202_SQL"
    log_error "请确认 PR #32 分支已 checkout，或 SQL 文件路径正确。"
    exit 1
fi

# 3. 确认数据库连接
if [[ -z "$STAGING_HOST" ]]; then
    log_error "未指定 staging 数据库主机。"
    log_error "请设置 STAGING_DB_HOST 环境变量或使用 --staging-host 参数。"
    exit 1
fi

log_info "目标数据库: $STAGING_HOST:$STAGING_PORT/$STAGING_DB"
log_info "模式: $([ "$DRY_RUN" = true ] && echo 'DRY-RUN（仅评估，不执行）' || echo '执行模式')"

# 构建 psql 连接参数
PSQL_ARGS="-h $STAGING_HOST -p $STAGING_PORT -d $STAGING_DB -U $STAGING_USER"
if [[ -n "$STAGING_PASSWORD" ]]; then
    export PGPASSWORD="$STAGING_PASSWORD"
fi

# ---- Dry-Run: 评估脏数据 ----
echo ""
echo "--- 步骤 1: 脏数据评估 ---"

# 评估受影响的 PENDING + TBD 记录数
DIRTY_COUNT=$(psql $PSQL_ARGS -t -A -c "
SELECT COUNT(*)
FROM chapter_teacher_assignments cta
WHERE source = 'TBD'
  AND accept_status = 'PENDING'
  AND teacher_id IS NOT NULL
  AND EXISTS (
      SELECT 1 FROM micro_specialty_proposals p
      WHERE p.id = cta.proposal_id
        AND p.proposer_id IS DISTINCT FROM cta.teacher_id
  );
" 2>&1)

if [[ "$DIRTY_COUNT" =~ ^[0-9]+$ ]]; then
    log_info "脏数据行数: $DIRTY_COUNT"
else
    log_error "查询失败: $DIRTY_COUNT"
    exit 1
fi

# 评估 chapter_teacher_assignments 总行数
TOTAL_COUNT=$(psql $PSQL_ARGS -t -A -c "
SELECT COUNT(*) FROM chapter_teacher_assignments;
" 2>&1)

log_info "chapter_teacher_assignments 总行数: $TOTAL_COUNT"

# 决策阈值
if [[ "$DIRTY_COUNT" -eq 0 ]]; then
    log_info "✅ 无脏数据，迁移安全。"
elif [[ "$DIRTY_COUNT" -le 100 ]]; then
    log_warn "⚠️  脏数据 $DIRTY_COUNT 行 ≤ 100，可直接执行迁移。"
else
    log_error "🚨 脏数据 $DIRTY_COUNT 行 > 100，需要 owner 决策。"
    log_error "   请检查数据后决定:"
    log_error "   1. 确认数据确实为脏数据 → 执行迁移"
    log_error "   2. 数据可能有效 → 先排查再决定"
    if [[ "$DRY_RUN" = true ]]; then
        log_error "   Dry-run 模式，不会执行迁移。"
        exit 0
    fi
    exit 1
fi

# ---- 显示脏数据明细（≤ 20 行时） ----
if [[ "$DIRTY_COUNT" -le 20 ]] && [[ "$DIRTY_COUNT" -gt 0 ]]; then
    echo ""
    echo "--- 脏数据明细（仅显示 ≤ 20 行） ---"
    psql $PSQL_ARGS -c "
SELECT cta.id, cta.proposal_id, cta.chapter_id, cta.teacher_id,
       p.proposer_id, cta.source, cta.accept_status
FROM chapter_teacher_assignments cta
JOIN micro_specialty_proposals p ON p.id = cta.proposal_id
WHERE source = 'TBD'
  AND accept_status = 'PENDING'
  AND teacher_id IS NOT NULL
  AND p.proposer_id IS DISTINCT FROM cta.teacher_id
ORDER BY cta.id
LIMIT 20;
" 2>&1
fi

# ---- 执行迁移 ----
if [[ "$DRY_RUN" = false ]]; then
    echo ""
    echo "--- 步骤 2: 备份 staging 数据库 ---"
    mkdir -p "$BACKUP_DIR"
    BACKUP_FILE="${BACKUP_DIR}/staging_pre_v202_${TIMESTAMP}.sql"
    log_info "备份到: $BACKUP_FILE"
    pg_dump $PSQL_ARGS -F p -f "$BACKUP_FILE"
    log_info "✅ 备份完成 ($(wc -c < "$BACKUP_FILE") bytes)"

    echo ""
    echo "--- 步骤 3: 备份 chapter_teacher_assignments 表 ---"
    TABLE_BACKUP="${BACKUP_DIR}/chapter_teacher_assignments_pre_v202_${TIMESTAMP}.sql"
    pg_dump $PSQL_ARGS -t chapter_teacher_assignments -F p -f "$TABLE_BACKUP"
    log_info "✅ 表备份完成"

    echo ""
    echo "--- 步骤 4: 执行 V202 迁移 ---"
    log_warn "⚠️  即将执行 V202 schema 变更，请确认..."
    read -p "输入 'yes' 继续: " CONFIRM
    if [[ "$CONFIRM" != "yes" ]]; then
        log_warn "用户取消执行。"
        exit 0
    fi

    psql $PSQL_ARGS -f "$V202_SQL" 2>&1
    log_info "✅ V202 迁移执行完成"

    echo ""
    echo "--- 步骤 5: 验证迁移结果 ---"

    # 验证 constraint 已替换
    CHK_COUNT=$(psql $PSQL_ARGS -t -A -c "
SELECT COUNT(*) FROM information_schema.check_constraints
WHERE constraint_name = 'chk_cta_source_consistency'
  AND table_name = 'chapter_teacher_assignments';
" 2>&1)

    # 验证 teacher_id nullable
    NULLABLE_CHECK=$(psql $PSQL_ARGS -t -A -c "
SELECT is_nullable FROM information_schema.columns
WHERE table_name = 'chapter_teacher_assignments'
  AND column_name = 'teacher_id';
" 2>&1)

    # 验证脏数据已清除
    REMAINING_DIRTY=$(psql $PSQL_ARGS -t -A -c "
SELECT COUNT(*)
FROM chapter_teacher_assignments cta
WHERE source = 'TBD'
  AND accept_status = 'PENDING'
  AND teacher_id IS NOT NULL
  AND EXISTS (
      SELECT 1 FROM micro_specialty_proposals p
      WHERE p.id = cta.proposal_id
        AND p.proposer_id IS DISTINCT FROM cta.teacher_id
  );
" 2>&1)

    log_info "chk_cta_source_consistency 存在: $CHK_COUNT (期望 1)"
    log_info "teacher_id IS NULLABLE: $NULLABLE_CHECK (期望 YES)"
    log_info "剩余脏数据: $REMAINING_DIRTY (期望 0)"

    if [[ "$CHK_COUNT" = "1" ]] && [[ "$NULLABLE_CHECK" = "YES" ]] && [[ "$REMAINING_DIRTY" = "0" ]]; then
        log_info "✅ 所有验证通过！"
    else
        log_error "❌ 验证失败，请检查迁移结果。"
        log_error "   回滚命令: psql $PSQL_ARGS -f $TABLE_BACKUP"
        exit 1
    fi

    echo ""
    echo "--- 回滚指南 ---"
    echo "如需回滚 V202 迁移:"
    echo "  psql $PSQL_ARGS -f $TABLE_BACKUP"
    echo "  psql $PSQL_ARGS -f $BACKUP_FILE    # 完整回滚"
fi

echo ""
echo "============================================"
log_info "V202 Staging Dry-Run 完成"
echo "============================================"
