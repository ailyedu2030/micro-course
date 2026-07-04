-- P0 修复: 添加 needs_manual_grading 列，替代 JSON LIKE 全表扫描
-- 原查询: .like(ExerciseRecord::getAnswers, "\"needsManualGrading\":true") 导致全表扫描
ALTER TABLE exercise_records
  ADD COLUMN needs_manual_grading BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_exercise_records_needs_manual_grading
  ON exercise_records(needs_manual_grading)
  WHERE needs_manual_grading = TRUE;
