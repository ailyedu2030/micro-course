-- V333: 简化方案 - HTML 课件 + PPT 课件 2 种独立管理
-- 策略：INTERACTIVE 课程按 section.courseware_type 拆分（HTML 或 PPT/BOTH）
-- 边界：混合课程（有 PPT 又有 HTML section）→ PPT_COURSEWARE 优先
-- 无 section 的 INTERACTIVE → 默认 HTML_COURSEWARE

-- 1. 放宽 CHECK 约束（先 DROP，数据迁移完成后重新 ADD —— 顺序不能颠倒：
--    若先 ADD 4 值约束，存量 INTERACTIVE 行会在 ADD 时违反新约束）
ALTER TABLE courses DROP CONSTRAINT IF EXISTS chk_courses_course_type;

-- 2. 数据迁移
UPDATE courses SET course_type = 
    CASE 
        WHEN EXISTS (
            SELECT 1 FROM course_sections cs 
            WHERE cs.course_id = courses.id 
            AND cs.courseware_type IN ('PPT', 'BOTH')
        ) THEN 'PPT_COURSEWARE'
        WHEN EXISTS (
            SELECT 1 FROM course_sections cs 
            WHERE cs.course_id = courses.id 
            AND cs.courseware_type = 'HTML'
        ) THEN 'HTML_COURSEWARE'
        WHEN course_type = 'VIDEO' THEN 'VIDEO'
        WHEN course_type = 'OFFLINE' THEN 'OFFLINE'
        WHEN course_type = 'INTERACTIVE' THEN 'HTML_COURSEWARE'
        ELSE course_type
    END
WHERE course_type IN ('VIDEO', 'INTERACTIVE', 'OFFLINE');

-- 3. 重新收紧为 4 值 CHECK 约束
ALTER TABLE courses ADD CONSTRAINT chk_courses_course_type 
    CHECK (course_type IN (
        'HTML_COURSEWARE', 'PPT_COURSEWARE',
        'VIDEO', 'OFFLINE'
    ));

-- 4. 审计
DO $$
DECLARE
    v_total INTEGER;
    v_html INTEGER;
    v_ppt INTEGER;
BEGIN
    SELECT COUNT(*) INTO v_total FROM courses;
    SELECT COUNT(*) INTO v_html FROM courses WHERE course_type = 'HTML_COURSEWARE';
    SELECT COUNT(*) INTO v_ppt FROM courses WHERE course_type = 'PPT_COURSEWARE';
    RAISE NOTICE 'V333 数据迁移: 总课程=%, HTML_COURSEWARE=%, PPT_COURSEWARE=%', v_total, v_html, v_ppt;
END $$;

COMMENT ON TABLE courses IS 'V333: course_type 4 值 (HTML_COURSEWARE / PPT_COURSEWARE / VIDEO / OFFLINE)';
