-- V304: HTML 分段脚本表 (1 unit = N segments, 与 HTML DOM 节点关联)
--
-- 设计动机: HTML 课件不分页, 但有多个 <audio> 段, 每段独立脚本
-- Rollback 路径: DROP TABLE slide_html_segment_scripts CASCADE;

CREATE TABLE slide_html_segment_scripts (
    id BIGSERIAL PRIMARY KEY,
    html_unit_id BIGINT NOT NULL,

    segment_index INT NOT NULL,           -- 1..N
    segment_marker VARCHAR(64),           -- HTML 内的 id, 如 "seg-3", NULL = 按顺序
    segment_text TEXT,                    -- 从 HTML 抽取的相关文本 (TTS 上下文)
    script_text TEXT NOT NULL,
    script_version INT NOT NULL DEFAULT 1,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    voice VARCHAR(64),
    tts_model VARCHAR(64),
    tts_params JSONB,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_html_seg_scripts_unit FOREIGN KEY (html_unit_id)
        REFERENCES slide_html_units(id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX uk_html_seg_scripts_active
    ON slide_html_segment_scripts(html_unit_id, segment_index)
    WHERE is_active = TRUE;

CREATE INDEX idx_html_seg_scripts_unit_history
    ON slide_html_segment_scripts(html_unit_id, segment_index, script_version DESC);

COMMENT ON TABLE slide_html_segment_scripts IS 'HTML 分段讲述稿 (V304)';
COMMENT ON COLUMN slide_html_segment_scripts.segment_marker IS 'HTML DOM id, NULL=按 segment_index 顺序';