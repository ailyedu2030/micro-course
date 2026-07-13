-- V183: 迁移 chapters → sections (1:1)
-- 每个 chapter（含 chapter_type）生成一条对应 section
-- 章节原有的 type、排序、时长等保留到 section 层

INSERT INTO course_sections
    (chapter_id, course_id, title, section_type, sort_order, duration,
     visible, version, created_at, updated_at)
SELECT
    cc.id, cc.course_id, cc.title,
    COALESCE(cc.chapter_type, 'VIDEO'),
    cc.sort_order, COALESCE(cc.duration, 0), TRUE,
    1, cc.created_at, cc.updated_at
FROM course_chapters cc
WHERE cc.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM course_sections cs
      WHERE cs.chapter_id = cc.id
        AND cs.title = cc.title
  );
