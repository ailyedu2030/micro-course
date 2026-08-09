#!/bin/bash
# =============================================================================
# staging-validation.sh — 生产部署前 staging 环境验证（F-2026-08-10-01 加固）
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
#   5. V327/V330 新增列
#   6. V328/V332 审计函数（audit_ghost_chapters()）
#   7. V332 剩余待人工 review 项（v_ghost_chapter_audit）
#
# 只读安全: 全部查询均为 SELECT，不修改任何数据。
# 真实门禁: 任一检查失败 → 汇总输出 + 非零退出码（部署脚本据此阻断）。
# =============================================================================

set -euo pipefail

PG_HOST=${1:-localhost}
PG_DB=${2:-micro_course}
PG_USER=${3:-postgres}
PG_PORT=${PG_PORT:-5432}
export PGPASSWORD="${PG_PASS:-postgres}"

PASS=0
FAIL=0

ok()   { echo "  ✓ $1"; PASS=$((PASS+1)); }
ng()   { echo "  ✗ $1"; FAIL=$((FAIL+1)); }

psql_q() { psql -h "$PG_HOST" -p "$PG_PORT" -U "$PG_USER" -d "$PG_DB" -tA -c "$1" 2>/dev/null || echo ""; }

echo "=============================================================="
echo "Staging Validation · ${PG_HOST}:${PG_PORT}/${PG_DB} (user=${PG_USER})"
echo "目标: Flyway V327-V332 + 课件 schema 兼容性"
echo "=============================================================="

echo
echo "=== 1. Flyway history (V327-V332) ==="
for v in 327 328 329 330 331 332; do
  row=$(psql_q "SELECT version || '|' || success FROM flyway_schema_history WHERE version::numeric = $v LIMIT 1;")
  if [ -n "$row" ]; then
    ver="${row%|*}"; suc="${row##*|}"
    if [ "$suc" = "t" ] || [ "$suc" = "true" ]; then
      ok "V$ver applied (success=true)"
    else
      ng "V$ver applied but success=false"
    fi
  else
    ng "V$v 未应用"
  fi
done

echo
echo "=== 2. 课件核心表存在性 ==="
for table in slide_ppt_pages slide_ppt_page_scripts slide_ppt_page_audios \
             slide_html_units slide_html_segment_scripts slide_html_segment_audios \
             slide_ppt_flow; do
  cnt=$(psql_q "SELECT count(*) FROM information_schema.tables WHERE table_name='$table' AND table_schema='public';")
  if [ "$cnt" = "1" ]; then ok "$table"; else ng "$table (缺失!)"; fi
done

echo
echo "=== 3. 课件视图存在性 ==="
for view in v_slide_ppt_page_status v_slide_html_unit_status v_ghost_chapter_backfill v_ghost_chapter_audit; do
  cnt=$(psql_q "SELECT count(*) FROM pg_views WHERE viewname='$view' AND schemaname='public';")
  if [ "$cnt" = "1" ]; then ok "$view"; else ng "$view (缺失!)"; fi
done

echo
echo "=== 4. V331 CHECK 约束 ==="
for con in chk_ppt_audios_status chk_html_seg_audios_status; do
  cnt=$(psql_q "SELECT count(*) FROM pg_constraint WHERE conname='$con';")
  if [ "$cnt" = "1" ]; then ok "$con"; else ng "$con (缺失!)"; fi
done

echo
echo "=== 5. V330 索引 ==="
for idx in idx_ppt_audios_claim idx_html_seg_audios_claim \
           idx_ppt_audios_script_default idx_html_seg_audios_script_default \
           idx_ppt_page_audios_status_processing idx_html_segment_audios_status_processing; do
  cnt=$(psql_q "SELECT count(*) FROM pg_indexes WHERE indexname='$idx';")
  if [ "$cnt" = "1" ]; then ok "$idx"; else ng "$idx (缺失!)"; fi
done

echo
echo "=== 6. V327/V330 新增列 ==="
for chk in \
  "slide_ppt_page_audios|error_message" "slide_html_segment_audios|error_message" \
  "slide_ppt_page_audios|is_default"   "slide_html_segment_audios|is_default" \
  "slide_ppt_page_audios|worker_id"    "slide_html_segment_audios|worker_id" \
  "slide_html_units|is_trusted"; do
  tbl="${chk%|*}"; col="${chk#*|}"
  cnt=$(psql_q "SELECT count(*) FROM information_schema.columns WHERE table_name='$tbl' AND column_name='$col';")
  if [ "$cnt" = "1" ]; then ok "$tbl.$col"; else ng "$tbl.$col (缺失!)"; fi
done

echo
echo "=== 7. V328/V332 审计函数（只读调用）==="
out=$(psql_q "SELECT audit_ghost_chapters();" | head -c 2000)
if [ -n "$out" ]; then
  ok "audit_ghost_chapters() 可执行"
  echo "$out" | head -c 800
  echo
else
  ng "audit_ghost_chapters() 不可执行"
fi

echo
echo "=== 8. V332 剩余待人工 review 项（v_ghost_chapter_audit）==="
rows=$(psql_q "SELECT count(*) FROM v_ghost_chapter_audit;")
if [ -n "$rows" ] && [ "$rows" = "0" ]; then
  ok "v_ghost_chapter_audit 无待人工项"
else
  echo "  ⚠️  v_ghost_chapter_audit 存在 $rows 行（需人工 review，不阻断）"
fi

echo
echo "=============================================================="
echo "  结果: ✅ $PASS 通过 / ❌ $FAIL 失败"
echo "=============================================================="
if [ "$FAIL" -gt 0 ]; then
  echo "❌ Staging Validation 未通过 —— 部署流程应阻断（deploy-5-percent.sh 阶段 0）"
  exit 1
fi
echo "✅ Staging Validation 全部通过，可进入部署流程"
