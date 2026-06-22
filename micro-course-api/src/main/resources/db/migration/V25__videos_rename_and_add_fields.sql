-- V25__videos_rename_and_add_fields.sql · 视频表字段修复
-- 依据: docs/数据字典.md v0.5 §3.1

-- 1. 重命名 file_name → original_name
ALTER TABLE videos RENAME COLUMN file_name TO original_name;

-- 2. 重命名 hls_url → m3u8_url
ALTER TABLE videos RENAME COLUMN hls_url TO m3u8_url;

-- 3. 添加缺失字段
ALTER TABLE videos ADD COLUMN IF NOT EXISTS play_sign VARCHAR(255);
ALTER TABLE videos ADD COLUMN IF NOT EXISTS sign_expired_at TIMESTAMP;
ALTER TABLE videos ADD COLUMN IF NOT EXISTS watermark_enabled BOOLEAN DEFAULT false;
ALTER TABLE videos ADD COLUMN IF NOT EXISTS max_play_rate INTEGER DEFAULT 0;
ALTER TABLE videos ADD COLUMN IF NOT EXISTS cover_url VARCHAR(500);
ALTER TABLE videos ADD COLUMN IF NOT EXISTS caption_url VARCHAR(500);
ALTER TABLE videos ADD COLUMN IF NOT EXISTS caption_language VARCHAR(10) DEFAULT 'zh';
ALTER TABLE videos ADD COLUMN IF NOT EXISTS audio_description_url VARCHAR(500);
ALTER TABLE videos ADD COLUMN IF NOT EXISTS allow_download BOOLEAN DEFAULT false;