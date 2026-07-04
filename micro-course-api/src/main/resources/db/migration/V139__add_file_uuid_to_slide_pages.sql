ALTER TABLE slide_pages ADD COLUMN file_uuid VARCHAR(64);
CREATE INDEX idx_slide_pages_uuid ON slide_pages(file_uuid);
