-- V191: slide_pages 新增 TTS 元数字段
-- 支持分段音频数量、使用的 voice/model、生成时间

ALTER TABLE slide_pages
  ADD COLUMN IF NOT EXISTS segment_count INTEGER,
  ADD COLUMN IF NOT EXISTS voice VARCHAR(64),
  ADD COLUMN IF NOT EXISTS tts_model VARCHAR(64),
  ADD COLUMN IF NOT EXISTS generated_at TIMESTAMP;

COMMENT ON COLUMN slide_pages.segment_count IS '该小节音频分段数量（如15段）';
COMMENT ON COLUMN slide_pages.voice IS 'TTS 使用的音色 ID';
COMMENT ON COLUMN slide_pages.tts_model IS 'TTS 模型（如 speech-2.8-hd）';
COMMENT ON COLUMN slide_pages.generated_at IS '音频生成时间';
