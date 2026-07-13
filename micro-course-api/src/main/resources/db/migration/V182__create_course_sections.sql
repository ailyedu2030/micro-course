-- V182: 创建 course_sections 表（课程→章节→课时三层架构）
-- 此迁移创建表结构，数据迁移由后续版本处理

CREATE TABLE IF NOT EXISTS course_sections (
    id BIGSERIAL PRIMARY KEY,
    chapter_id BIGINT NOT NULL REFERENCES course_chapters(id),
    course_id BIGINT NOT NULL REFERENCES courses(id),
    title VARCHAR(200) NOT NULL,
    section_type VARCHAR(20) NOT NULL
        CHECK (section_type IN ('VIDEO','INTERACTIVE','OFFLINE','EXERCISE')),
    sort_order INTEGER NOT NULL DEFAULT 0,
    duration INTEGER NOT NULL DEFAULT 0,
    visible BOOLEAN NOT NULL DEFAULT TRUE,
    description TEXT,
    script_content TEXT,
    version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_cs_chapter ON course_sections(chapter_id);
CREATE INDEX IF NOT EXISTS idx_cs_course ON course_sections(course_id);
CREATE INDEX IF NOT EXISTS idx_cs_type ON course_sections(section_type);
CREATE INDEX IF NOT EXISTS idx_cs_chapter_sort ON course_sections(chapter_id, sort_order);
