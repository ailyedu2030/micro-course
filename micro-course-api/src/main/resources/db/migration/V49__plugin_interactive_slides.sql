-- V49__plugin_interactive_slides.sql
-- Phase 11.1: 互动课程插件 - 幻灯片数据模型

CREATE TABLE IF NOT EXISTS course_slides (
    id BIGSERIAL PRIMARY KEY,
    course_id BIGINT NOT NULL REFERENCES courses(id),
    file_name VARCHAR(255) NOT NULL,
    file_url VARCHAR(500) NOT NULL,
    total_pages INTEGER NOT NULL DEFAULT 0,
    status INTEGER NOT NULL DEFAULT 0,
    error_message VARCHAR(500),
    file_hash VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_slides_course ON course_slides(course_id);
CREATE INDEX IF NOT EXISTS idx_slides_status ON course_slides(status);

CREATE TABLE IF NOT EXISTS slide_pages (
    id BIGSERIAL PRIMARY KEY,
    slide_id BIGINT NOT NULL REFERENCES course_slides(id),
    course_id BIGINT NOT NULL REFERENCES courses(id),
    page_number INTEGER NOT NULL,
    image_url VARCHAR(500) NOT NULL,
    thumbnail_url VARCHAR(500),
    image_width INTEGER,
    image_height INTEGER,
    extracted_text TEXT,
    has_animation BOOLEAN NOT NULL DEFAULT FALSE,
    has_embedded_media BOOLEAN NOT NULL DEFAULT FALSE,
    narration_script TEXT,
    narration_audio_url VARCHAR(500),
    audio_duration INTEGER,
    narration_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sp_course_page ON slide_pages(course_id, page_number);
CREATE INDEX IF NOT EXISTS idx_sp_slide_id ON slide_pages(slide_id);
CREATE INDEX IF NOT EXISTS idx_sp_narration_status ON slide_pages(narration_status);
