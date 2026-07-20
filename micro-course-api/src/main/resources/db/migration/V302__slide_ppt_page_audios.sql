-- V302: PPT 音频表 (1:N 音频版本, 用于音色对比 + 7-19 P1-C 修复兼容)
--
-- 关键设计: audio_token 是 UK, 流式 GET 不依赖 pageNumber (避免 7-19 P0 类问题)
-- Rollback 路径: DROP TABLE slide_ppt_page_audios CASCADE;

CREATE TABLE slide_ppt_page_audios (
    id BIGSERIAL PRIMARY KEY,
    script_id BIGINT NOT NULL,
    ppt_page_id BIGINT NOT NULL,  -- 冗余, 便于快速查询

    audio_url VARCHAR(500) NOT NULL,
    audio_token VARCHAR(64),
    audio_duration_ms INT,

    voice_used VARCHAR(64) NOT NULL,
    model_used VARCHAR(64) NOT NULL,
    generation_params JSONB,

    generation_started_at TIMESTAMP,
    completed_at TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'GENERATING',

    file_size_bytes BIGINT,
    storage_path VARCHAR(500),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_ppt_audios_script FOREIGN KEY (script_id)
        REFERENCES slide_ppt_page_scripts(id) ON DELETE CASCADE,
    CONSTRAINT chk_ppt_audios_status CHECK (status IN ('GENERATING','READY','FAILED'))
);

CREATE INDEX idx_ppt_audios_script ON slide_ppt_page_audios(script_id);
CREATE INDEX idx_ppt_audios_page_status ON slide_ppt_page_audios(ppt_page_id, status);
CREATE INDEX idx_ppt_audios_token ON slide_ppt_page_audios(audio_token) WHERE audio_token IS NOT NULL;

COMMENT ON TABLE slide_ppt_page_audios IS 'PPT 音频历史版本 (V302)';
COMMENT ON COLUMN slide_ppt_page_audios.audio_token IS '流式 GET token (UK 验证, 不依赖 pageNumber)';
COMMENT ON COLUMN slide_ppt_page_audios.status IS 'GENERATING -> READY / FAILED';