-- I-24: questions.question_type 和 questions.difficulty 缺少 CHECK 约束
-- I-25: attendance_records.status 缺少 CHECK 约束
-- 使用 DO $$ 块确保幂等性（约束已存在时不报错）

-- I-24: questions.question_type CHECK
ALTER TABLE questions DROP CONSTRAINT IF EXISTS chk_questions_question_type;
ALTER TABLE questions ADD CONSTRAINT chk_questions_question_type
    CHECK (question_type IN ('SINGLE','MULTIPLE','JUDGE','FILL','SHORT_ANSWER','ESSAY'));

-- I-24: questions.difficulty CHECK
ALTER TABLE questions DROP CONSTRAINT IF EXISTS chk_questions_difficulty;
ALTER TABLE questions ADD CONSTRAINT chk_questions_difficulty
    CHECK (difficulty IN (1, 2, 3));

-- I-25: attendance_records.status CHECK
ALTER TABLE attendance_records DROP CONSTRAINT IF EXISTS chk_attendance_status;
ALTER TABLE attendance_records ADD CONSTRAINT chk_attendance_status
    CHECK (status IN ('PRESENT', 'LATE', 'ABSENT', 'EXCUSED'));
