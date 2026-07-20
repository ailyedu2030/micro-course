-- V305: HTML 分段音频表 (1 script = N audio, audio_token UK)
--
-- 关键设计: 与 v1.22.1 P1-C 修复完全兼容 (audio_token UK 校验)
-- Rollback 路径: DROP TABLE slide_html_segment_audios CASCADE;

CREATE TABLE slide_html_segment_audios (
    id BIGSERIAL PRIMARY KEY,
    segment_script_id BIGINT NOT NULL,
    html_unit_id BIGINT NOT NULL,
    segment_index INT NOT NULL,

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

    CONSTRAINT fk_html_seg_audios_script FOREIGN KEY (segment_script_id)
        REFERENCES slide_html_segment_scripts(id) ON DELETE CASCADE,
    CONSTRAINT chk_html_seg_audios_status CHECK (status IN ('GENERATING','READY','FAILED'))
);

CREATE INDEX idx_html_seg_audios_script ON slide_html_segment_audios(segment_script_id);
CREATE INDEX idx_html_seg_audios_unit_status ON slide_html_segment_audios(html_unit_id, status);
CREATE INDEX idx_html_seg_audios_token ON slide_html_segment_audios(audio_token) WHERE audio_token IS NOT NULL;

COMMENT ON TABLE slide_html_segment_audios IS 'HTML 分段音频 (V305)';