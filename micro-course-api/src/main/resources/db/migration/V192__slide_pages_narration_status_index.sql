-- V192: slide_pages narration_status 索引
-- 用于加速 generateAll() / markAllPagesError() 等批量状态查询

CREATE INDEX IF NOT EXISTS idx_slide_pages_narration_status ON slide_pages(narration_status);
