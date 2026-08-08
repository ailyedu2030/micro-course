#!/bin/bash
# V310 ghost chapter 生产审计脚本（DBA 手工执行）
# 输出：幽灵章节行数、按 course 分布、sample rows
# 用途：DBA 在生产数据库执行，生成审计报告给总工程师 review
# 安全：只读审计（SELECT），不修改任何数据。数据修复必须走 V332 后的 migration + 生产门禁
#       （见 docs/incidents/2026-08-07-V310-chapter-backfill-ghost-chapters.md §5）
# 用法：bash scripts/audit-v310-ghost-chapter-prod.sh [PGHOST] [PGUSER] [PGDATABASE]

PGHOST=${1:-microcourse-prod-pg}
PGUSER=${2:-readonly_user}
PGDATABASE=${3:-micro_course}

echo "=== V310 Ghost Chapter 生产审计 ==="
echo "Host: $PGHOST"
echo "User: $PGUSER"
echo "DB: $PGDATABASE"
echo ""

echo "--- PPT Pages with chapter_id=1 ---"
psql -h "$PGHOST" -U "$PGUSER" -d "$PGDATABASE" -c "
SELECT 
  COUNT(*) AS ghost_count,
  COUNT(DISTINCT course_id) AS affected_courses
FROM slide_ppt_pages 
WHERE chapter_id = 1;
"

echo "--- HTML Units with chapter_id=1 ---"
psql -h "$PGHOST" -U "$PGUSER" -d "$PGDATABASE" -c "
SELECT 
  COUNT(*) AS ghost_count,
  COUNT(DISTINCT course_id) AS affected_courses
FROM slide_html_units 
WHERE chapter_id = 1;
"

echo "--- Cross-course references (chapter 1 belonging to other courses) ---"
psql -h "$PGHOST" -U "$PGUSER" -d "$PGDATABASE" -c "
SELECT 
  p.course_id AS ppt_course,
  p.chapter_id AS ppt_chapter,
  cc.course_id AS chapter_course,
  COUNT(*) AS ghost_count
FROM slide_ppt_pages p
JOIN course_chapters cc ON cc.id = p.chapter_id
WHERE p.chapter_id = 1 AND p.course_id != cc.course_id
GROUP BY p.course_id, p.chapter_id, cc.course_id;
"

echo "--- Sections missing chapter_id (orphan references) ---"
psql -h "$PGHOST" -U "$PGUSER" -d "$PGDATABASE" -c "
SELECT 
  p.id AS page_id,
  p.course_id,
  p.section_id,
  p.chapter_id
FROM slide_ppt_pages p
WHERE p.section_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM course_sections cs WHERE cs.id = p.section_id);
"

echo "=== 审计完成。请 review 报告后人工执行 V332 修复 ==="
