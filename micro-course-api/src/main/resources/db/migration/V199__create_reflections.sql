-- V199: section_reflections — 反思日志模板
CREATE TABLE IF NOT EXISTS section_reflections (
  id BIGSERIAL PRIMARY KEY,
  section_id BIGINT NOT NULL REFERENCES course_sections(id) ON DELETE CASCADE,
  template TEXT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_section_reflections_section ON section_reflections(section_id);

COMMENT ON TABLE section_reflections IS 'section 反思日志模板';
