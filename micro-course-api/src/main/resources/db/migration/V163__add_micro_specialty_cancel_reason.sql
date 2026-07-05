-- P0-009: 微专业取消/关闭时记录原因
ALTER TABLE micro_specialties ADD COLUMN IF NOT EXISTS cancel_reason VARCHAR(500);
COMMENT ON COLUMN micro_specialties.cancel_reason IS '取消/关闭原因';
