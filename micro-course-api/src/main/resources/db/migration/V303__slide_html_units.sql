-- V303: HTML 课件单元表 (1 个 section 最多 1 个 unit)
--
-- 设计动机: HTML 课件不分页, 单文件表达完整内容
-- Rollback 路径: DROP TABLE slide_html_units CASCADE;

CREATE TABLE slide_html_units (
    id BIGSERIAL PRIMARY KEY,
    course_id BIGINT NOT NULL,
    chapter_id BIGINT NOT NULL,
    section_id BIGINT NOT NULL,
    slide_id BIGINT NOT NULL,

    page_title VARCHAR(200),

    file_uuid VARCHAR(64) NOT NULL,
    html_content TEXT NOT NULL,           -- 原始 HTML (sanitize 前)
    html_sanitized TEXT NOT NULL,         -- HtmlSanitizer 处理后 (入播放器)
    file_size_bytes BIGINT NOT NULL,

    detected_segments INT,                -- 自动检测的分段数
    has_interactions BOOLEAN DEFAULT FALSE,
    interaction_types JSONB,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_html_units_section UNIQUE (section_id),
    CONSTRAINT fk_html_units_section FOREIGN KEY (section_id)
        REFERENCES course_sections(id) ON DELETE CASCADE
);

CREATE INDEX idx_html_units_course ON slide_html_units(course_id, section_id);

COMMENT ON TABLE slide_html_units IS 'HTML 课件单单元 (V303)';
COMMENT ON COLUMN slide_html_units.html_content IS '原始 HTML (保留作审计)';
COMMENT ON COLUMN slide_html_units.html_sanitized IS 'HtmlSanitizer.sanitizeForCourseware 处理后';
COMMENT ON COLUMN slide_html_units.detected_segments IS 'AI 自动检测的语义分段数';