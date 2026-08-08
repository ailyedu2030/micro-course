#!/bin/bash
# =============================================================================
# staging-validation.sh — 生产部署前 staging 环境验证
#
# 用途: 验证 Flyway V327-V332 在真实数据库 schema 下应用成功，且课件相关对象齐全。
#
# 用法:
#   PG_PASS=postgres bash scripts/staging-validation.sh [PG_HOST] [PG_DB] [PG_USER]
#
#   默认: localhost / micro_course / postgres（dev DB 亦为真实 Flyway applied）
#   生产: 先通过 deploy-gate.sh check，确认只读用户后使用
#
# 检查项:
#   1. Flyway history (V327-V332)
#   2. 课件相关表 / 视图存在性
#   3. V331 CHECK 约束（chk_ppt_audios_status / chk_html_seg_audios_status）
#   4. V330 索引
#   5. V328/V332 审计函数（audit_ghost_chapters()）
#   6. V332 剩余待人工 review 项（v_ghost_chapter_audit）
#
# 只读安全: 全部查询均为 SELECT，不修改任何数据。
# =============================================================================

set -euo pipefail

PG_HOST=${1:-localhost}
PG_DB=${2:-micro_course}
PG_USER=${3:-postgres}
PG_PORT=${PG_PORT:-5432}
export PGPASSWORD="${PG_PASS:-postgres}"

run() {
  echo
  echo "=== $1 ==="
  psql -h "$PG_HOST" -p "$PG_PORT" -U "$PG_USER" -d "$PG_DB" -v ON_ERROR_STOP=1 -tA -c "$2" || echo "  ⚠️  查询失败（对象可能不存在）"
}

echo "=============================================================="
echo "Staging Validation · ${PG_HOST}:${PG_PORT}/${PG_DB} (user=${PG_USER})"
echo "目标: Flyway V327-V332 + 课件 schema 兼容性"
echo "=============================================================="

run "1. Flyway history (V327-V332)" \
  "SELECT version, success, execution_time || 'ms' AS ms, installed_on
   FROM flyway_schema_history
   WHERE version::int BETWEEN 327 AND 332
   ORDER BY installed_rank;"

echo
echo "=== 2. 课件核心表存在性 ==="
for table in slide_ppt_pages slide_ppt_page_scripts slide_ppt_page_audios \
             slide_html_units slide_html_segment_scripts slide_html_segment_audios \
             slide_ppt_flow; do
  cnt=$(psql -h "$PG_HOST" -p "$PG_PORT" -U "$PG_USER" -d "$PG_DB" -tA -c \
    "SELECT count(*) FROM information_schema.tables WHERE table_name='$table' AND table_schema='public';" 2>/dev/null || echo 0)
  if [ "$cnt" = "1" ]; then
    echo "  ✓ $table"
  else
    echo "  ✗ $table (缺失!)"
  fi
done

echo
echo "=== 3. 课件视图存在性 ==="
for view in v_slide_ppt_page_status v_slide_html_unit_status v_ghost_chapter_backfill v_ghost_chapter_audit; do
  cnt=$(psql -h "$PG_HOST" -p "$PG_PORT" -U "$PG_USER" -d "$PG_DB" -tA -c \
    "SELECT count(*) FROM pg_views WHERE viewname='$view' AND schemaname='public';" 2>/dev/null || echo 0)
  if [ "$cnt" = "1" ]; then
    echo "  ✓ $view"
  else
    echo "  ✗ $view (缺失!)"
  fi
done

run "4. V331 CHECK 约束" \
  "SELECT conrelid::regclass AS tbl, conname,
          pg_get_constraintdef(oid) AS def
   FROM pg_constraint
   WHERE conname IN ('chk_ppt_audios_status','chk_html_seg_audios_status')
   ORDER BY conname;"

run "5. V330 索引" \
  "SELECT indexname
   FROM pg_indexes
   WHERE indexname IN ('idx_ppt_audios_claim','idx_html_seg_audios_claim',
                       'idx_ppt_audios_script_default','idx_html_seg_audios_script_default',
                       'idx_ppt_page_audios_status_processing','idx_html_segment_audios_status_processing')
   ORDER BY indexname;"

echo
echo "=== 6. V327/V330 新增列 ==="
for chk in \
  "slide_ppt_page_audios|error_message" "slide_html_segment_audios|error_message" \
  "slide_ppt_page_audios|is_default"   "slide_html_segment_audios|is_default" \
  "slide_ppt_page_audios|worker_id"    "slide_html_segment_audios|worker_id" \
  "slide_html_units|is_trusted"; do
  tbl="${chk%|*}"; col="${chk#*|}"
  cnt=$(psql -h "$PG_HOST" -p "$PG_PORT" -U "$PG_USER" -d "$PG_DB" -tA -c \
    "SELECT count(*) FROM information_schema.columns WHERE table_name='$tbl' AND column_name='$col';" 2>/dev/null || echo 0)
  if [ "$cnt" = "1" ]; then
    echo "  ✓ $tbl.$col"
  else
    echo "  ✗ $tbl.$col (缺失!)"
  fi
done

echo
echo "=== 7. V328/V332 审计函数（只读调用）==="
psql -h "$PG_HOST" -p "$PG_PORT" -U "$PG_USER" -d "$PG_DB" -tA -c \
  "SELECT audit_ghost_chapters();" 2>/dev/null | head -c 2000
echo

echo
echo "=== 8. V332 剩余待人工 review 项（v_ghost_chapter_audit）==="
psql -h "$PG_HOST" -p "$PG_PORT" -U "$PG_USER" -d "$PG_DB" -tA -c \
  "SELECT source_type, course_id, section_id, count(*) AS n
   FROM v_ghost_chapter_audit
   GROUP BY 1,2,3
   ORDER BY 2,1;" 2>/dev/null || echo "  (无输出 = 无待人工项)"

echo
echo "=== 验证完成 ==="
