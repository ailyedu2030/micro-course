-- V26__question_tag_relations.sql · 题目标签关联表
-- 依据: docs/数据字典.md v0.5 §2.5

CREATE TABLE IF NOT EXISTS question_tag_relations (
    id          BIGSERIAL   PRIMARY KEY,
    question_id BIGINT      NOT NULL REFERENCES questions(id),
    tag_id      BIGINT      NOT NULL REFERENCES tags(id)
);

CREATE INDEX IF NOT EXISTS idx_qtr_question ON question_tag_relations(question_id);
CREATE INDEX IF NOT EXISTS idx_qtr_tag ON question_tag_relations(tag_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_qtr_unique ON question_tag_relations(question_id, tag_id);