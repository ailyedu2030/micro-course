-- P1C-021 / P1C-024 修复: 学习进度表增加线下签到标记字段
-- 用于 tracking 学生是否参加了线下活动（签到后自动标记）

ALTER TABLE learning_progress ADD COLUMN IF NOT EXISTS offline_attended BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN learning_progress.offline_attended IS '线下活动是否签到（P1C-021 新增）';
