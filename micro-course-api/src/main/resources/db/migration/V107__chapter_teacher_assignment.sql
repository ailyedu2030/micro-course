-- V107__chapter_teacher_assignment.sql
-- Phase 1: 申报表加章节支持
-- Phase 2: 章节-教师映射(表先建好,使用在Phase 2+)

-- ============================================================
-- 1. proposal_chapters — 申报中的章节
-- ============================================================
CREATE TABLE IF NOT EXISTS proposal_chapters (
    id              BIGSERIAL    PRIMARY KEY,
    proposal_id     BIGINT       NOT NULL REFERENCES micro_specialty_proposals(id) ON DELETE CASCADE,
    course_id       BIGINT       NOT NULL REFERENCES proposal_courses(id) ON DELETE CASCADE,
    title           VARCHAR(200) NOT NULL,
    description     VARCHAR(1000),
    sort_order      INTEGER      DEFAULT 0,
    hours           INTEGER      DEFAULT 0,
    version         INTEGER      NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_pc_chapter UNIQUE (course_id, sort_order),
    CONSTRAINT chk_pc_chapter_hours CHECK (hours >= 0)
);
CREATE INDEX IF NOT EXISTS idx_pc_chapter_course ON proposal_chapters(course_id);
CREATE INDEX IF NOT EXISTS idx_pc_chapter_proposal ON proposal_chapters(proposal_id);

-- ============================================================
-- 2. chapter_teacher_assignments — 章节-教师映射
-- ============================================================
CREATE TABLE IF NOT EXISTS chapter_teacher_assignments (
    id                  BIGSERIAL    PRIMARY KEY,
    proposal_id         BIGINT       NOT NULL REFERENCES micro_specialty_proposals(id) ON DELETE RESTRICT,
    course_id           BIGINT       NOT NULL REFERENCES proposal_courses(id) ON DELETE CASCADE,
    chapter_id          BIGINT       NOT NULL REFERENCES proposal_chapters(id) ON DELETE CASCADE,
    teacher_id          BIGINT       NOT NULL REFERENCES users(id) ON DELETE RESTRICT,

    -- 来源决策
    source              VARCHAR(20)  NOT NULL DEFAULT 'TBD',
    source_course_id    BIGINT       REFERENCES courses(id),
    source_chapter_id   BIGINT       REFERENCES course_chapters(id),

    -- 接受状态
    accept_status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    accepted_at         TIMESTAMP,

    frozen_price        DECIMAL(10,2),
    responsibility      VARCHAR(500),
    version             INTEGER      NOT NULL DEFAULT 0,

    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at          TIMESTAMP,

    CONSTRAINT uk_cta_chapter_teacher UNIQUE (chapter_id, teacher_id),
    CONSTRAINT chk_cta_source CHECK (source IN ('TBD', 'existing', 'new')),
    CONSTRAINT chk_cta_accept_status CHECK (accept_status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'REVOKED', 'LEFT')),
    CONSTRAINT chk_cta_source_consistency CHECK (
        (source = 'existing' AND source_course_id IS NOT NULL AND source_chapter_id IS NOT NULL)
        OR (source = 'new')
        OR (source = 'TBD' AND source_course_id IS NULL AND source_chapter_id IS NULL)
    )
);
CREATE INDEX IF NOT EXISTS idx_cta_teacher ON chapter_teacher_assignments(teacher_id);
CREATE INDEX IF NOT EXISTS idx_cta_chapter ON chapter_teacher_assignments(chapter_id);
CREATE INDEX IF NOT EXISTS idx_cta_proposal ON chapter_teacher_assignments(proposal_id);
CREATE INDEX IF NOT EXISTS idx_cta_source_chapter ON chapter_teacher_assignments(source_chapter_id);

-- 软删除后允许重新分配的部分唯一索引
CREATE UNIQUE INDEX IF NOT EXISTS uk_cta_active
    ON chapter_teacher_assignments(chapter_id, teacher_id)
    WHERE deleted_at IS NULL;

-- ============================================================
-- 3. 触发器: source_chapter_id 必须属于 source_course_id
-- ============================================================
CREATE OR REPLACE FUNCTION trg_cta_source_chapter_belongs_course_fn()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.source = 'existing' AND NEW.source_chapter_id IS NOT NULL THEN
        IF NOT EXISTS (
            SELECT 1 FROM course_chapters
            WHERE id = NEW.source_chapter_id AND course_id = NEW.source_course_id
        ) THEN
            RAISE EXCEPTION 'source_chapter_id % does not belong to source_course_id %',
                NEW.source_chapter_id, NEW.source_course_id;
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_cta_source_chapter_belongs_course ON chapter_teacher_assignments;
CREATE TRIGGER trg_cta_source_chapter_belongs_course
    BEFORE INSERT OR UPDATE OF source_chapter_id, source_course_id ON chapter_teacher_assignments
    FOR EACH ROW EXECUTE FUNCTION trg_cta_source_chapter_belongs_course_fn();
