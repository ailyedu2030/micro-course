-- J3-01: 为 exercises 表添加 is_exam 字段，区分考试与普通练习
ALTER TABLE exercises ADD COLUMN IF NOT EXISTS is_exam BOOLEAN DEFAULT FALSE;

COMMENT ON COLUMN exercises.is_exam IS '是否为考试（区别于普通练习）';
