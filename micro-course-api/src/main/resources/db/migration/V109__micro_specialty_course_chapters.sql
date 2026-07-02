-- V109__micro_specialty_course_chapters.sql
-- Phase 5: 审批后章节物化 — 微专业与课程章节的 M:N 关系

CREATE TABLE IF NOT EXISTS micro_specialty_course_chapters (
    id                  BIGSERIAL    PRIMARY KEY,
    micro_specialty_id  BIGINT       NOT NULL REFERENCES micro_specialties(id) ON DELETE CASCADE,
    course_id           BIGINT       NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    chapter_id          BIGINT       NOT NULL REFERENCES course_chapters(id) ON DELETE CASCADE,
    source              VARCHAR(20)  NOT NULL,
    proposal_chapter_id BIGINT       REFERENCES proposal_chapters(id),
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_ms_cc UNIQUE (micro_specialty_id, chapter_id)
);

CREATE INDEX IF NOT EXISTS idx_mscc_ms ON micro_specialty_course_chapters(micro_specialty_id);
CREATE INDEX IF NOT EXISTS idx_mscc_course ON micro_specialty_course_chapters(course_id);
