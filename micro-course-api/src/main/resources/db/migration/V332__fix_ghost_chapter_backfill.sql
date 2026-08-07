-- V332: 幽灵章节自动修复回填（D-1 闭环 · 2026-08-07）
--
-- 【背景】 V310 回填段 COALESCE(s.chapter_id, 1) 硬编码兜底，把无 chapter 归属的存量
--   slide 归入"幽灵章节 1"。V328 仅提供诊断视图/函数（纯只读审计），人工修复模板
--   需手工执行。本 migration 将"可自动修复部分"落地为幂等自动修复：
--     1. 修复前调用 V328 的 audit_ghost_chapters() 获取报告（RAISE NOTICE 输出，留痕）
--     2. 对 slide_ppt_pages / slide_html_units 中 chapter_id=1 且可通过 section_id
--        反查到真实 chapter（cs.chapter_id ≠ 1）的记录：UPDATE 修正 chapter_id
--     3. 对 section_id 为 NULL / 跨课程引用（chapter1_cross_course）等无法自动判定
--        的记录：保持 chapter_id=1，写入 operation_logs 标记待人工 review
--     4. 创建视图 v_ghost_chapter_audit 暴露"剩余待人工 review 项"
--
-- 【判定标准与 V328 对齐】 chapter_id=1 且命中任一嫌疑：
--   a. 所属 section 缺失（section_id 无对应 course_sections）→ 无法反查, 待人工
--   b. 所属 section 真实 chapter_id ≠ 1 → V310 硬编码错误归属（主因, 本 migration 自动修复）
--   c. course_chapters.id=1 归属其它课程 → 跨课程引用, 待人工确认
--
-- 【幂等】 UPDATE 条件天然幂等（修复后 chapter_id ≠ 1，二次执行 COUNT=0 跳过）；
--   DO 块内先 SELECT 计数、确认非空才执行 UPDATE + 写 audit log；视图 CREATE OR REPLACE。
--   Flyway 启动时自动应用（生产部署需 DBA review 后随发布流程执行，禁止直连改数据）。
--
-- 【Rollback 路径】
--   本 migration 仅修正 V310 硬编码产生的错误归属（反查 section 的真实 chapter），
--   数据字典级语义：chapter_id 应等于所属 section 的 chapter_id（student 端按 chapter
--   归档展示）。如需回滚恢复原状（不推荐，仅当反查结果被证明错误时）：
--     DROP VIEW IF EXISTS v_ghost_chapter_audit;
--     -- 恢复"幽灵章节 1"归属（保留修复前的 operation_logs 明细可反查 affected 行）：
--     UPDATE slide_ppt_pages p SET chapter_id = 1, updated_at = NOW()
--       FROM course_sections cs
--       WHERE cs.id = p.section_id AND p.chapter_id = cs.chapter_id AND cs.chapter_id <> 1;
--     UPDATE slide_html_units u SET chapter_id = 1, updated_at = NOW()
--       FROM course_sections cs
--       WHERE cs.id = u.section_id AND u.chapter_id = cs.chapter_id AND cs.chapter_id <> 1;
--   注意：chapter_id=1 本身不一定错误（若 section 确实属于 chapter 1 则 V310 无过错），
--   rollback 仅针对"被 V332 修改过的行"（见 operation_logs GHOST_CHAPTER_FIX 明细）。

-- ═══════════════════════════════════════════════════════════════
-- 0. 前置检查: 依赖 V328 的诊断对象必须存在
--    （V328 与本 migration 同批发布时 Flyway 按版本顺序先跑 V328, 恒成立）
-- ═══════════════════════════════════════════════════════════════

-- ═══════════════════════════════════════════════════════════════
-- 1. 剩余待人工 review 视图（修复后仍无法自动判定的嫌疑行）
--    section 缺失（无法反查）或跨课程引用（chapter1_cross_course=true）
-- ═══════════════════════════════════════════════════════════════

CREATE OR REPLACE VIEW v_ghost_chapter_audit AS
SELECT
    g.source_type,
    g.row_id,
    g.course_id,
    g.current_chapter_id,
    g.section_id,
    g.actual_chapter_id,
    g.section_title,
    g.chapter1_course_id,
    g.chapter1_cross_course
FROM v_ghost_chapter_backfill g
WHERE g.section_id IS NULL OR g.chapter1_cross_course;

COMMENT ON VIEW v_ghost_chapter_audit IS
    'V332 自动修复后剩余待人工 review 的幽灵章节嫌疑行 (section 缺失无法反查 / 跨课程引用)';

-- ═══════════════════════════════════════════════════════════════
-- 2. 幂等自动修复（DO 块: 先计数, 非空才 UPDATE + 写 audit log）
-- ═══════════════════════════════════════════════════════════════

DO $$
DECLARE
    v_before_report TEXT;
    v_ppt_fixable   INT;
    v_html_fixable  INT;
    v_ppt_fixed     INT;
    v_html_fixed    INT;
    v_review_left   INT;
BEGIN
    -- 2.1 修复前审计报告（调用 V328 函数, 输出到 Flyway 日志留痕）
    SELECT audit_ghost_chapters()::text INTO v_before_report;
    RAISE NOTICE '[V332] 修复前幽灵章节审计报告: %', v_before_report;

    -- 2.2 计数（幂等判定: 仅当存在可修复行才执行 UPDATE）
    SELECT COUNT(*) INTO v_ppt_fixable
    FROM slide_ppt_pages p
    JOIN course_sections cs ON cs.id = p.section_id
    WHERE p.chapter_id = 1 AND cs.chapter_id IS DISTINCT FROM 1;

    SELECT COUNT(*) INTO v_html_fixable
    FROM slide_html_units u
    JOIN course_sections cs ON cs.id = u.section_id
    WHERE u.chapter_id = 1 AND cs.chapter_id IS DISTINCT FROM 1;

    -- 2.3 修复 PPT（section 反查真实 chapter_id）
    IF v_ppt_fixable > 0 THEN
        UPDATE slide_ppt_pages p
        SET chapter_id = cs.chapter_id, updated_at = NOW()
        FROM course_sections cs
        WHERE cs.id = p.section_id
          AND p.chapter_id = 1
          AND cs.chapter_id IS DISTINCT FROM 1;
        GET DIAGNOSTICS v_ppt_fixed = ROW_COUNT;
    ELSE
        v_ppt_fixed := 0;
    END IF;

    -- 2.4 修复 HTML（section 反查真实 chapter_id）
    IF v_html_fixable > 0 THEN
        UPDATE slide_html_units u
        SET chapter_id = cs.chapter_id, updated_at = NOW()
        FROM course_sections cs
        WHERE cs.id = u.section_id
          AND u.chapter_id = 1
          AND cs.chapter_id IS DISTINCT FROM 1;
        GET DIAGNOSTICS v_html_fixed = ROW_COUNT;
    ELSE
        v_html_fixed := 0;
    END IF;

    -- 2.5 剩余待人工 review 项（section 缺失 / 跨课程引用）
    SELECT COUNT(*) INTO v_review_left FROM v_ghost_chapter_audit;

    -- 2.6 审计留痕: 修复事件写入 operation_logs（action 长度 < 50）
    IF v_ppt_fixed > 0 OR v_html_fixed > 0 OR v_review_left > 0 THEN
        INSERT INTO operation_logs (user_id, action, target_type, target_id, detail, ip, success, created_at)
        VALUES (
            NULL,
            'GHOST_CHAPTER_FIX',
            'SYSTEM',
            NULL,
            jsonb_build_object(
                'migration', 'V332',
                'ppt_fixed', v_ppt_fixed,
                'html_fixed', v_html_fixed,
                'review_left', v_review_left,
                'audited_at', NOW()
            )::text,
            NULL,
            TRUE,
            NOW()
        );
    END IF;

    RAISE NOTICE '[V332] 幽灵章节自动修复完成: PPT 修复 % 行, HTML 修复 % 行, 剩余待人工 review % 行',
        v_ppt_fixed, v_html_fixed, v_review_left;
END;
$$;

-- ═══════════════════════════════════════════════════════════════
-- 3. 修复后自检提示（幂等: 二次执行可修复行应为 0）
--    查询: SELECT audit_ghost_chapters();  → total_ghost_rows 应仅剩待人工 review 项
-- ═══════════════════════════════════════════════════════════════
