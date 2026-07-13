-- V184: 迁移 lessons → sections
-- lessons 附加到对应 chapter 的 section 后（sort_order 偏移确保不冲突）

INSERT INTO course_sections
    (chapter_id, course_id, title, section_type, sort_order, duration,
     visible, script_content, version, created_at, updated_at)
SELECT
    l.chapter_id, l.course_id, l.title,
    COALESCE(l.lesson_type, 'VIDEO'),
    10000 + COALESCE(l.sort_order, 0),
    COALESCE(l.duration, 0),
    COALESCE(l.visible, true),
    l.script_content,
    COALESCE(l.version, 1),
    l.created_at, l.updated_at
FROM lessons l
WHERE l.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM course_sections cs
      WHERE cs.chapter_id = l.chapter_id
        AND cs.title = l.title
        AND cs.sort_order = 10000 + COALESCE(l.sort_order, 0)
  );
