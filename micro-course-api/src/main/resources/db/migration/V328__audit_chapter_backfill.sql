-- V328: 幽灵章节回填审计 (D-1 · 2026-08-07)
--
-- 【背景】 V310 (V310__backfill_slide_pages_to_new_arch.sql) PPT/HTML 回填段使用
--   COALESCE(s.chapter_id, 1) 硬编码 1 兜底 —— 无 chapter 归属的存量 slide
--   被归入"幽灵章节 1"(该行实际所属 section 的 chapter_id 不是 1, 或
--   course_chapters.id=1 归属其它课程, 或 section 缺失无法反查)。
--   生产环境存在数据完整性问题: 学生端可能看到错误的章节归属。
--
-- 【本 migration 定位】 纯审计, 不修改任何数据 (L0 兜底: 数据完整性 = 体验保障)。
--   - 视图   v_ghost_chapter_backfill: 列出全部"幽灵章节"嫌疑行 (PPT + HTML 明细)
--   - 函数   audit_ghost_chapters(): 返回 JSON 报告
--            (总数 + 按 course 分布 + 明细样例 ≤200 行)
--   - 调用   SELECT audit_ghost_chapters();   (或 psql 里 \x 后调用)
--
-- 【人工修复路径 (V329+, 不在本 migration 内)】 修复前必须人工 review 审计结果:
--   1) 对每条嫌疑行确认正确 chapter_id (以 section 反查为准):
--      UPDATE slide_ppt_pages p
--      SET chapter_id = cs.chapter_id, updated_at = NOW()
--      FROM course_sections cs
--      WHERE cs.id = p.section_id
--        AND p.chapter_id = 1
--        AND cs.chapter_id IS DISTINCT FROM 1;
--      -- slide_html_units 同理:
--      UPDATE slide_html_units u
--      SET chapter_id = cs.chapter_id, updated_at = NOW()
--      FROM course_sections cs
--      WHERE cs.id = u.section_id
--        AND u.chapter_id = 1
--        AND cs.chapter_id IS DISTINCT FROM 1;
--   2) section 缺失的嫌疑行 (section_id 无对应 course_sections) 需先修 section
--      归属, 再按新 section 反查 chapter_id。
--   3) 全部修复后重跑 audit_ghost_chapters() 应为 total_ghost_rows=0。
--   4) 修复走正式发布流程 (V329+ migration + 生产门禁), 禁止直连生产 DB 改数据。
--
-- 【回滚】 DROP VIEW IF EXISTS v_ghost_chapter_backfill;
--          DROP FUNCTION IF EXISTS audit_ghost_chapters();
-- 【幂等】 CREATE OR REPLACE VIEW / FUNCTION, 重跑安全。

-- ═══════════════════════════════════════════════════════════════
-- 1. 幽灵章节明细视图
--    判定标准 (chapter_id=1 且命中以下任一)：
--      a. 所属 section 缺失 (cs.id IS NULL)        → 无法反查, 待人工
--      b. 所属 section 的真实 chapter_id ≠ 1       → V310 硬编码错误归属 (主因)
--      c. course_chapters.id=1 归属其它课程        → 跨课程引用 (数据字典级 FK 错位)
-- ═══════════════════════════════════════════════════════════════

CREATE OR REPLACE VIEW v_ghost_chapter_backfill AS
WITH chapter1 AS (
    SELECT id, course_id, title FROM course_chapters WHERE id = 1
)
SELECT
    'PPT'::text AS source_type,
    p.id AS row_id,
    p.course_id,
    p.chapter_id AS current_chapter_id,
    p.section_id,
    cs.chapter_id AS actual_chapter_id,
    cs.title AS section_title,
    c1.course_id AS chapter1_course_id,
    (c1.course_id IS NOT NULL AND c1.course_id <> p.course_id) AS chapter1_cross_course
FROM slide_ppt_pages p
LEFT JOIN course_sections cs ON cs.id = p.section_id
LEFT JOIN chapter1 c1 ON TRUE
WHERE p.chapter_id = 1
  AND (
      cs.id IS NULL
      OR cs.chapter_id IS DISTINCT FROM 1
      OR (c1.course_id IS NOT NULL AND c1.course_id <> p.course_id)
  )
UNION ALL
SELECT
    'HTML'::text AS source_type,
    u.id AS row_id,
    u.course_id,
    u.chapter_id AS current_chapter_id,
    u.section_id,
    cs.chapter_id AS actual_chapter_id,
    cs.title AS section_title,
    c1.course_id AS chapter1_course_id,
    (c1.course_id IS NOT NULL AND c1.course_id <> u.course_id) AS chapter1_cross_course
FROM slide_html_units u
LEFT JOIN course_sections cs ON cs.id = u.section_id
LEFT JOIN chapter1 c1 ON TRUE
WHERE u.chapter_id = 1
  AND (
      cs.id IS NULL
      OR cs.chapter_id IS DISTINCT FROM 1
      OR (c1.course_id IS NOT NULL AND c1.course_id <> u.course_id)
  );

COMMENT ON VIEW v_ghost_chapter_backfill IS
    'V310 COALESCE(chapter_id,1) 硬编码产生的幽灵章节嫌疑行 (V328 纯审计, 不改数据)';

-- ═══════════════════════════════════════════════════════════════
-- 2. 审计函数: 返回 JSON 报告 (总数 + 按 course 分布 + 明细样例)
--    调用: SELECT audit_ghost_chapters();
-- ═══════════════════════════════════════════════════════════════

CREATE OR REPLACE FUNCTION audit_ghost_chapters()
RETURNS JSONB
LANGUAGE plpgsql
AS $$
DECLARE
    v_total INT;
    v_by_course JSONB;
    v_rows JSONB;
BEGIN
    SELECT COUNT(*)::int INTO v_total FROM v_ghost_chapter_backfill;

    SELECT COALESCE(jsonb_agg(x ORDER BY x.course_id), '[]'::jsonb) INTO v_by_course
    FROM (
        SELECT course_id, source_type, COUNT(*) AS cnt
        FROM v_ghost_chapter_backfill
        GROUP BY course_id, source_type
    ) x;

    SELECT COALESCE(jsonb_agg(x ORDER BY x.course_id, x.row_id), '[]'::jsonb) INTO v_rows
    FROM (
        SELECT * FROM v_ghost_chapter_backfill
        ORDER BY course_id, row_id
        LIMIT 200
    ) x;

    RETURN jsonb_build_object(
        'audited_at', NOW(),
        'audit_version', 'V328',
        'total_ghost_rows', v_total,
        'by_course', v_by_course,
        'sample_rows', v_rows,
        'note', '纯审计输出, 不修改数据。修复由人工 review 后执行 V329+ 后置 UPDATE, ' ||
                '详见 docs/incidents/2026-08-07-V310-chapter-backfill-ghost-chapters.md'
    );
END;
$$;

COMMENT ON FUNCTION audit_ghost_chapters() IS
    'V328 幽灵章节审计: 返回 JSONB 报告 (总数/按 course 分布/明细样例), 纯只读';
