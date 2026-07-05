-- V145: 补充 course_chapters 的普通索引(V144删除UNIQUE后索引可能丢失)
-- V144 删除了 uk_cc_course_sort 唯一约束，但原约束创建的索引也一并被删除。
-- 此处补充一个普通索引，确保按 course_id + sort_order 查询时的性能。
CREATE INDEX IF NOT EXISTS idx_cc_course_sort ON course_chapters(course_id, sort_order);
