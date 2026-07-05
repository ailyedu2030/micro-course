-- V144: 删除 course_chapters 表的 uk_cc_course_sort 唯一约束
--
-- 背景：UNIQUE(course_id, sort_order) 约束导致以下问题：
-- 1. 重新排序时需要复杂的移位/交换逻辑，易出BUG
-- 2. 软删除行仍占用sort_order值，新行无法使用
-- 3. PostgreSQL逐行检查UNIQUE约束，UPDATE中间值即报错
--
-- 替代方案：应用程序层不做唯一性强制，查询时 ORDER BY sort_order ASC, id ASC
-- 即可获得稳定排序。相同sort_order的章节按id顺序排列，不影响用户体验。
--
-- 参考：docs/数据字典.md

ALTER TABLE course_chapters DROP CONSTRAINT IF EXISTS uk_cc_course_sort;
