-- V185: 迁移 course_slides.lesson_id → section_id
-- 1) 添加 section_id 列（替代 lesson_id）
-- 2) 从章节→section 映射填充 section_id
-- 3) 删除旧的 lesson_id 列

ALTER TABLE course_slides ADD COLUMN IF NOT EXISTS section_id BIGINT;

-- 通过 chapter_id 找到对应 section（章节→section 1:1 映射）
UPDATE course_slides cs
SET section_id = (
    SELECT cs2.id FROM course_sections cs2
    WHERE cs2.chapter_id = cs.chapter_id
      AND cs2.deleted_at IS NULL
    ORDER BY cs2.sort_order ASC
    LIMIT 1
)
WHERE cs.section_id IS NULL
  AND cs.chapter_id IS NOT NULL;

-- 如果 lesson_id 有值（遗留数据），关联到对应 section
UPDATE course_slides cs
SET section_id = (
    SELECT cs2.id FROM course_sections cs2
    JOIN lessons l ON l.chapter_id = cs2.chapter_id
    WHERE l.id = cs.lesson_id
      AND cs2.sort_order = 10000 + COALESCE(l.sort_order, 0)
      AND cs2.deleted_at IS NULL
    LIMIT 1
)
WHERE cs.section_id IS NULL
  AND cs.lesson_id IS NOT NULL;

-- 更新约束：删除旧的 lesson_id 索引，新建 section_id 索引
DROP INDEX IF EXISTS uk_slides_course_lesson;
DROP INDEX IF EXISTS idx_slides_lesson;
CREATE UNIQUE INDEX IF NOT EXISTS uk_slides_course_section ON course_slides(course_id, section_id);
CREATE INDEX IF NOT EXISTS idx_slides_section ON course_slides(section_id);

-- 迁移 slide_pages.lesson_id → section_id
ALTER TABLE slide_pages ADD COLUMN IF NOT EXISTS section_id BIGINT;

UPDATE slide_pages sp
SET section_id = (
    SELECT cs2.id FROM course_sections cs2
    JOIN lessons l ON l.chapter_id = cs2.chapter_id
    WHERE l.id = sp.lesson_id
      AND cs2.sort_order = 10000 + COALESCE(l.sort_order, 0)
      AND cs2.deleted_at IS NULL
    LIMIT 1
)
WHERE sp.section_id IS NULL
  AND sp.lesson_id IS NOT NULL;

DROP INDEX IF EXISTS uk_sp_course_lesson_page;
DROP INDEX IF EXISTS idx_sp_lesson;
CREATE UNIQUE INDEX IF NOT EXISTS uk_sp_course_section_page ON slide_pages(course_id, section_id, page_number);
CREATE INDEX IF NOT EXISTS idx_sp_section ON slide_pages(section_id);

-- 删除旧的 lesson_id 列
ALTER TABLE course_slides DROP COLUMN IF EXISTS lesson_id;
ALTER TABLE slide_pages  DROP COLUMN IF EXISTS lesson_id;
