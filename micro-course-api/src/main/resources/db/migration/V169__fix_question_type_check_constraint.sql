-- CI 修复：V166 中 chk_questions_question_type 只包含 6 个标准类型名，
-- 但测试数据和遗留代码中还使用 SINGLE_CHOICE / MULTIPLE_CHOICE / FILL_BLANK。
-- 这些遗留值在 code 层通过 ExerciseRecordServiceImpl.normalizeQuestionType 映射到标准类型。
-- 在统一迁移完成前，CHECK 约束需同时允许标准类型和遗留类型。

ALTER TABLE questions DROP CONSTRAINT IF EXISTS chk_questions_question_type;
ALTER TABLE questions ADD CONSTRAINT chk_questions_question_type
    CHECK (question_type IN (
        'SINGLE','MULTIPLE','JUDGE','FILL','SHORT_ANSWER','ESSAY',
        'SINGLE_CHOICE','MULTIPLE_CHOICE','FILL_BLANK'
    ));
