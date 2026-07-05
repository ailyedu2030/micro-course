-- P1C-037: 为 notification_preferences 添加 extra_preferences 列
-- 用于存储播放/隐私/辅助功能等扩展偏好设置（JSON 格式）
ALTER TABLE notification_preferences
    ADD COLUMN IF NOT EXISTS extra_preferences TEXT;
