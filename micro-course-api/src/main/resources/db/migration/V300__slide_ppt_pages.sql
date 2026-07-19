-- V300: PPT 课件多页表 (spec 3.1)
--
-- 命名说明: V202-V210 已被历史 migration 占用 (V202 = chapter teacher id nullable)
-- 本 Phase 1 全部使用 V300-V309 段, 避开冲突
--
-- Backfill 路径: 从 slide_pages WHERE content_type='PPT_RENDERED' 一次性回填 (Phase 3)
-- Rollback 路径: DROP TABLE slide_ppt_pages CASCADE;
-- 影响行数(预计): 138 条历史 PPT page 全部回填 (Phase 3)
-- 7-19 P0 防御: 无 destructive UPSERT, 仅 CREATE TABLE

CREATE TABLE slide_ppt_pages (
    id BIGSERIAL PRIMARY KEY,
    course_id BIGINT NOT NULL,
    chapter_id BIGINT NOT NULL,
    section_id BIGINT NOT NULL,
    slide_id BIGINT NOT NULL,
    page_number INT NOT NULL,

    page_title VARCHAR(200),

    -- 渲染内容
    image_url VARCHAR(500),
    thumbnail_url VARCHAR(500),
    image_width INT,
    image_height INT,
    file_uuid VARCHAR(64),
    file_size_bytes BIGINT,

    -- 抽取特征
    extracted_text TEXT,
    has_animation BOOLEAN DEFAULT FALSE,
    has_embedded_media BOOLEAN DEFAULT FALSE,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_ppt_pages_slide_page UNIQUE (slide_id, page_number),
    CONSTRAINT fk_ppt_pages_section FOREIGN KEY (section_id)
        REFERENCES course_sections(id) ON DELETE CASCADE
);

-- Performance indexes (spec 6.1)
CREATE INDEX idx_ppt_pages_section ON slide_ppt_pages(section_id, page_number);
CREATE INDEX idx_ppt_pages_course ON slide_ppt_pages(course_id, section_id, page_number);

COMMENT ON TABLE slide_ppt_pages IS 'PPT 课件的多个渲染页面 (V300 拆分自 slide_pages)';
COMMENT ON COLUMN slide_ppt_pages.page_title IS '可选的页面标题 (POI 抽取或教师录入)';
COMMENT ON COLUMN slide_ppt_pages.image_url IS 'CDN 签名 URL (含 token)';
COMMENT ON COLUMN slide_ppt_pages.extracted_text IS 'POI 抽取的页面文本, 用于 AI 生成讲述稿';