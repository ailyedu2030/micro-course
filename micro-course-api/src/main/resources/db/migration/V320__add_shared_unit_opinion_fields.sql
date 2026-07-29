-- D-001: 为 proposal_shared_units 表添加意见与签字字段
-- 之前这些字段仅存储在 proposal_signatures 表（sign_level='SHARED_UNIT'），
-- 现冗余存储到 proposal_shared_units 以便直接读取，proposal_signatures 仍保持同步。

ALTER TABLE proposal_shared_units
    ADD COLUMN IF NOT EXISTS opinion_text        TEXT,
    ADD COLUMN IF NOT EXISTS signature_type      VARCHAR(20) DEFAULT 'TEXT',
    ADD COLUMN IF NOT EXISTS signature_text      VARCHAR(100),
    ADD COLUMN IF NOT EXISTS signature_image_url VARCHAR(500),
    ADD COLUMN IF NOT EXISTS seal_image_url      VARCHAR(500),
    ADD COLUMN IF NOT EXISTS sign_date           TIMESTAMP,
    ADD COLUMN IF NOT EXISTS remark              VARCHAR(200);

COMMENT ON COLUMN proposal_shared_units.opinion_text IS '单位意见';
COMMENT ON COLUMN proposal_shared_units.signature_type IS '签字类型: TEXT/IMAGE';
COMMENT ON COLUMN proposal_shared_units.signature_text IS '签字文字（签字类型为 TEXT 时）';
COMMENT ON COLUMN proposal_shared_units.signature_image_url IS '签字图片 URL（签字类型为 IMAGE 时）';
COMMENT ON COLUMN proposal_shared_units.seal_image_url IS '印章图片 URL';
COMMENT ON COLUMN proposal_shared_units.sign_date IS '签字日期';
COMMENT ON COLUMN proposal_shared_units.remark IS '备注';
