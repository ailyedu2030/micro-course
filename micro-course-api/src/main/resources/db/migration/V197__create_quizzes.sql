-- V197: section_quizzes — 嵌入式自测题
CREATE TABLE IF NOT EXISTS section_quizzes (
  id BIGSERIAL PRIMARY KEY,
  section_id BIGINT NOT NULL REFERENCES course_sections(id) ON DELETE CASCADE,
  slide INT NOT NULL,
  prompt TEXT NOT NULL,
  options JSONB NOT NULL,
  correct_index INT NOT NULL,
  explanation TEXT,
  created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_section_quizzes_section ON section_quizzes(section_id);

COMMENT ON TABLE section_quizzes IS 'section 嵌入式自测题(每题独立上传)';
COMMENT ON COLUMN section_quizzes.options IS '选项 JSON 数组: ["A. xxx", "B. xxx", ...]';
COMMENT ON COLUMN section_quizzes.slide IS '关联的 slide 序号(如 3, 8, 10)';
