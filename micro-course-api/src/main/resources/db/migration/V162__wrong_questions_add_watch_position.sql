-- P1I-025: wrong_questions 表添加 watch_position 列
-- VO 中已定义 watchPosition 字段，补充 DB 列映射
ALTER TABLE wrong_questions ADD COLUMN watch_position INTEGER DEFAULT NULL;
COMMENT ON COLUMN wrong_questions.watch_position IS '视频观看位置（秒），关联错题时的视频进度位置';
