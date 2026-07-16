-- P1Stage1IntegrationTest 测试后清理
-- 删除 P1 测试创建的 course/chapter/section
DELETE FROM slide_pages WHERE course_id IN (SELECT id FROM courses WHERE hid LIKE 'p1-%');
DELETE FROM course_sections WHERE course_id IN (SELECT id FROM courses WHERE hid LIKE 'p1-%');
DELETE FROM course_chapters WHERE course_id IN (SELECT id FROM courses WHERE hid LIKE 'p1-%');
DELETE FROM course_slides WHERE course_id IN (SELECT id FROM courses WHERE hid LIKE 'p1-%');
DELETE FROM courses WHERE hid LIKE 'p1-%';
