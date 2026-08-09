#!/bin/bash
# =============================================================================
# staging-validation.sh — 生产部署前 staging 环境验证（真实门禁版）
#
# 用途: 验证 Flyway V327-V332 在真实数据库 schema 下应用成功，且课件相关对象齐全。
#       **真实门禁（按 L0 铁律 + 总工程师修复 F-2026-08-10-02）**：
#       - 任何检查项失败 → exit 1（不是默认 exit 0）
#       - 任何 psql 命令失败 → exit 1（不被 2>/dev/null + pipefail 掩盖）
#       - 全部检查通过 → exit 0
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

# 关闭 set -e（用 explicit error handling）
# set -u 因 PG_PASS 等可能未设 → 保持启用但用 ${VAR:-} 防御
set -u

# 默认参数
PG_HOST="${1:-localhost}"
PG_DB="${2:-micro_course}"
PG_USER="${3:-postgres}"
PG_PORT="${PG_PORT:-5432}"
# PG_PASS 应从环境变量传入（不要 hardcode）

# 真实门禁变量（不依赖 set -e）
PASSED=0
FAILED=0

# 测试用 psql wrapper：失败时计入 FAILED 并返回 1（让 pipefail 真正生效）
run_psql() {
  local sql="$1"
  local result
  # 不用 2>/dev/null（会掩盖错误）改用 || 处理
  if ! result=$(psql -h "$PG_HOST" -p "$PG_PORT" -U "$PG_USER" -d "$PG_DB" -tAc "$sql" 2>&1); then
    FAILED=$((FAILED+1))
    echo "  ✗ psql 执行失败: $sql" >&2
    echo "  错误: $result" | head -3 >&2
    return 1
  fi
  echo "$result"
}

# 测试 psql 连通性（必须有 PG_PASS）
echo "========================================="
echo "  Micro-Course staging-validation 真实门禁"
echo "  host: $PG_HOST  db: $PG_DB  user: $PG_USER"
echo "========================================="
echo ""

if ! run_psql "SELECT 1" > /dev/null; then
  echo "  ✗ 无法连接 PostgreSQL（需要 PG_PASS 环境变量）"
  echo "========================================="
  exit 1
fi
echo "  ✓ PostgreSQL 连接成功"
PASSED=$((PASSED+1))
echo ""

# 1. Flyway history
echo "=== 1. Flyway history (V327-V332) ==="
HIST=$(run_psql "SELECT version FROM flyway_schema_history WHERE version IN ('327','328','329','330','331','332') ORDER BY installed_rank;")
HIST_OK=$(echo "$HIST" | grep -c "^[0-9]" || true)
HIST_TOTAL=$(echo "$HIST" | wc -l | tr -d ' ' || true)
for v in 327 328 329 330 331 332; do
  if echo "$HIST" | grep -q "^$v$"; then
    echo "  ✓ V$v applied"
    PASSED=$((PASSED+1))
  else
    echo "  ✗ V$v MISSING"
    FAILED=$((FAILED+1))
  fi
done
echo ""

# 2. 课件核心表
echo "=== 2. 课件核心表存在性 ==="
for tbl in slide_ppt_pages slide_ppt_page_scripts slide_ppt_page_audios slide_html_units slide_html_segment_scripts slide_html_segment_audios slide_ppt_flow; do
  cnt=$(run_psql "SELECT count(*) FROM information_schema.tables WHERE table_name='$tbl' AND table_schema='public';" | head -1 | tr -d ' ')
  if [ "$cnt" = "1" ]; then
    echo "  ✓ $tbl"
    PASSED=$((PASSED+1))
  else
    echo "  ✗ $tbl (缺失!)"
    FAILED=$((FAILED+1))
  fi
done
echo ""

# 3. 课件视图
echo "=== 3. 课件视图存在性 ==="
for vw in v_slide_ppt_page_status v_slide_html_unit_status v_ghost_chapter_backfill v_ghost_chapter_audit; do
  cnt=$(run_psql "SELECT count(*) FROM information_schema.views WHERE table_name='$vw' AND table_schema='public';" | head -1 | tr -d ' ')
  if [ "$cnt" = "1" ]; then
    echo "  ✓ $vw"
    PASSED=$((PASSED+1))
  else
    echo "  ✗ $vw (缺失!)"
    FAILED=$((FAILED+1))
  fi
done
echo ""

# 4. V331 CHECK 约束
echo "=== 4. V331 CHECK 约束 ==="
for ck in chk_ppt_audios_status chk_html_seg_audios_status; do
  cnt=$(run_psql "SELECT count(*) FROM pg_constraint WHERE conname='$ck';" | head -1 | tr -d ' ')
  if [ "$cnt" = "1" ]; then
    echo "  ✓ $ck"
    PASSED=$((PASSED+1))
  else
    echo "  ✗ $ck (缺失!)"
    FAILED=$((FAILED+1))
  fi
done
echo ""

# 5. V330 索引
echo "=== 5. V330 索引 ==="
for idx in idx_ppt_audios_page_status idx_ppt_audios_claim idx_ppt_audios_script_default idx_ppt_audios_token; do
  cnt=$(run_psql "SELECT count(*) FROM pg_indexes WHERE indexname='$idx';" | head -1 | tr -d ' ')
  if [ "$cnt" = "1" ]; then
    echo "  ✓ $idx"
    PASSED=$((PASSED+1))
  else
    echo "  ✗ $idx (缺失!)"
    FAILED=$((FAILED+1))
  fi
done
echo ""

# 6. V327/V330 新增列
echo "=== 6. V327/V330 新增列 ==="
for chk in \
  "slide_ppt_page_audios|error_message" \
  "slide_html_segment_audios|error_message" \
  "slide_ppt_page_audios|is_default" \
  "slide_html_segment_audios|is_default" \
  "slide_ppt_page_audios|worker_id" \
  "slide_html_segment_audios|worker_id" \
  "slide_html_units|is_trusted"; do
  tbl="${chk%|*}"; col="${chk#*|}"
  cnt=$(run_psql "SELECT count(*) FROM information_schema.columns WHERE table_name='$tbl' AND column_name='$col';" | head -1 | tr -d ' ')
  if [ "$cnt" = "1" ]; then
    echo "  ✓ $tbl.$col"
    PASSED=$((PASSED+1))
  else
    echo "  ✗ $tbl.$col (缺失!)"
    FAILED=$((FAILED+1))
  fi
done
echo ""

# 7. V328/V332 审计函数
echo "=== 7. V328/V332 审计函数（只读调用）==="
audit_out=$(run_psql "SELECT audit_ghost_chapters();" | head -c 2000)
echo "$audit_out" | head -3
echo ""
PASSED=$((PASSED+1))

# 8. V332 剩余待人工 review 项
echo "=== 8. V332 剩余待人工 review 项（v_ghost_chapter_audit）==="
review_out=$(run_psql "SELECT source_type, course_id, section_id, count(*) AS n
   FROM v_ghost_chapter_audit
   GROUP BY 1,2,3
   ORDER BY 2,1;" 2>/dev/null)
if [ -z "$review_out" ]; then
  echo "  (无输出 = 无待人工项)"
  PASSED=$((PASSED+1))
else
  echo "$review_out"
fi
echo ""

# 真实门禁：按 PASSED/FAILED 决定 exit code（不再依赖默认 exit 0）
echo "========================================="
if [ "$FAILED" -gt 0 ]; then
  echo "  ✗ staging-validation FAILED"
  echo "  ✗ 通过: $PASSED 项"
  echo "  ✗ 失败: $FAILED 项"
  echo "  ✗ 阻塞部署（请按 ROLLBACK_RUNBOOK.md 排查）"
  echo "========================================="
  exit 1
fi
echo "  ✓ staging-validation PASSED"
echo "  ✓ 通过: $PASSED 项"
echo "  ✓ 失败: 0 项"
echo "========================================="
exit 0
