-- V301: PPT 讲述稿表 (1:N 历史版本, is_active 标记最新)
--
-- 设计动机: 客户改讲述稿不应丢失历史 (回滚 + A/B 对比)
-- Rollback 路径: DROP TABLE slide_ppt_page_scripts CASCADE;

CREATE TABLE slide_ppt_page_scripts (
    id BIGSERIAL PRIMARY KEY,
    ppt_page_id BIGINT NOT NULL,

    script_text TEXT NOT NULL,
    script_version INT NOT NULL DEFAULT 1,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    voice VARCHAR(64),
    tts_model VARCHAR(64),
    tts_params JSONB,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_ppt_scripts_page FOREIGN KEY (ppt_page_id)
        REFERENCES slide_ppt_pages(id) ON DELETE CASCADE
);

-- Partial unique: 每个 PPT page 最多一个 active script
CREATE UNIQUE INDEX uk_ppt_scripts_active
    ON slide_ppt_page_scripts(ppt_page_id)
    WHERE is_active = TRUE;

CREATE INDEX idx_ppt_scripts_page_history
    ON slide_ppt_page_scripts(ppt_page_id, script_version DESC);

COMMENT ON TABLE slide_ppt_page_scripts IS 'PPT 讲述稿历史版本 (V301)';
COMMENT ON COLUMN slide_ppt_page_scripts.is_active IS '最新 active=true, 历史版本 active=false';
COMMENT ON COLUMN slide_ppt_page_scripts.tts_params IS 'TTS 参数 JSON (speed / pitch / emotion)';