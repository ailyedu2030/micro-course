-- V13__video_upload_fields.sql · 视频上传 + 转码字段
-- 依据：Phase 8 开发规范
-- 日期：2026-06-12
-- 状态机：UPLOADING(0), TRANSCODING(1), COMPLETED(2), FAILED(3)

ALTER TABLE videos ADD COLUMN IF NOT EXISTS original_path VARCHAR(512);

-- 注释（PG 支持 IF NOT EXISTS 跳过重复）
COMMENT ON COLUMN videos.original_path IS '原始视频文件存储路径';