ALTER TABLE slide_pages ADD COLUMN chapter_id BIGINT;
CREATE INDEX idx_slide_pages_chapter_id ON slide_pages(chapter_id);
