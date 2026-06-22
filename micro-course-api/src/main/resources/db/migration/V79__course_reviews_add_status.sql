-- V79: course_reviews 添加 status 字段，支持审核通过/驳回
-- 0=待审核, 1=通过, 2=驳回; 存量数据默认为 1（通过）

ALTER TABLE course_reviews
    ADD COLUMN IF NOT EXISTS status INTEGER NOT NULL DEFAULT 1;

-- 已有数据全部设为通过（兼容原有行为）
UPDATE course_reviews SET status = 1 WHERE status IS NULL;

CREATE INDEX IF NOT EXISTS idx_course_reviews_status ON course_reviews(status);
