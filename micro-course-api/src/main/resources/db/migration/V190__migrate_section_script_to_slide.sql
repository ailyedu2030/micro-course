-- 迁移存量 course_sections.script_content → slide_page.narration_script
-- 只覆盖 slide_page.narration_script IS NULL 的记录，不覆盖已有值
UPDATE slide_pages sp
SET narration_script = cs.script_content
FROM course_sections cs
WHERE cs.id = sp.section_id
  AND cs.script_content IS NOT NULL
  AND sp.narration_script IS NULL;
