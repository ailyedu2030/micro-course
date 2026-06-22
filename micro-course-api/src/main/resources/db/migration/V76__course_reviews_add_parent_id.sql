-- V76__course_reviews_add_parent_id.sql · 评价回复支持
-- E4: 为课程评价增加 parent_id 字段，支持评价回复功能
-- P1(R4 验证补齐): 增加自引用外键 ON DELETE CASCADE，与项目惯例一致
-- 依据: docs/数据字典.md v0.5
-- 日期: 2026-06-22
ALTER TABLE course_reviews ADD COLUMN IF NOT EXISTS parent_id BIGINT;
COMMENT ON COLUMN course_reviews.parent_id IS '父评价ID（支持评价回复，NULL 表示顶级评价）';
CREATE INDEX IF NOT EXISTS idx_course_reviews_parent_id ON course_reviews(parent_id);

-- P1(R4 验证补齐): 自引用外键，删除父评价时级联删除回复
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_course_reviews_parent'
          AND table_name = 'course_reviews'
    ) THEN
        ALTER TABLE course_reviews
            ADD CONSTRAINT fk_course_reviews_parent
            FOREIGN KEY (parent_id) REFERENCES course_reviews(id) ON DELETE CASCADE;
    END IF;
END $$;
