-- V198: section_tasks — 截图任务
CREATE TABLE IF NOT EXISTS section_tasks (
  id BIGSERIAL PRIMARY KEY,
  section_id BIGINT NOT NULL REFERENCES course_sections(id) ON DELETE CASCADE,
  slide INT NOT NULL,
  description TEXT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_section_tasks_section ON section_tasks(section_id);

COMMENT ON TABLE section_tasks IS 'section 截图任务(slide 12)';
COMMENT ON COLUMN section_tasks.slide IS '关联的 slide 序号(如 12)';
