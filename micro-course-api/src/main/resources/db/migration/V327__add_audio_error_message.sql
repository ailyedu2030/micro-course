-- V327: v2 音频表增加 error_message 失败原因列（R-15 / 数据字典 P1-I-3/4）
--
-- 背景: TtsWorkerService 标记 FAILED 时仅写 status，前端 AudioPanel 只能显示"失败"二字，
--       用户无法得知失败原因（余额不足 / 限流 / 超时）。此列记录最近一次失败原因。
-- 与 legacy course_slides.error_message（V182 设计）对齐。
-- Rollback 路径: ALTER TABLE slide_ppt_page_audios DROP COLUMN error_message;
--                ALTER TABLE slide_html_segment_audios DROP COLUMN error_message;

ALTER TABLE slide_ppt_page_audios
    ADD COLUMN IF NOT EXISTS error_message TEXT;

ALTER TABLE slide_html_segment_audios
    ADD COLUMN IF NOT EXISTS error_message TEXT;

COMMENT ON COLUMN slide_ppt_page_audios.error_message IS '最近一次生成失败原因（余额不足/限流/超时等），R-15';
COMMENT ON COLUMN slide_html_segment_audios.error_message IS '最近一次生成失败原因（余额不足/限流/超时等），R-15';
