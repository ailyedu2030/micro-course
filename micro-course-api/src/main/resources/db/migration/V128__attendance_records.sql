-- =============================================================================
-- V128__attendance_records.sql
-- -----------------------------------------------------------------------------
-- 签到记录表
-- 记录学生对每次线下课的签到情况。支持幂等签到（UNIQUE 约束防重复）。
-- updated_by 记录教师手动修改签到状态时的操作者。
-- =============================================================================

CREATE TABLE IF NOT EXISTS attendance_records (
    id              BIGSERIAL       PRIMARY KEY,
    session_id      BIGINT          NOT NULL REFERENCES chapter_offline_sessions(id) ON DELETE CASCADE,
    user_id         BIGINT          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status          VARCHAR(20)     NOT NULL DEFAULT 'PRESENT',
    checkin_time    TIMESTAMP,
    updated_by      BIGINT          REFERENCES users(id) ON DELETE SET NULL,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_att_session_user UNIQUE (session_id, user_id)
);

CREATE INDEX idx_att_session ON attendance_records(session_id);
CREATE INDEX idx_att_user    ON attendance_records(user_id);

COMMENT ON TABLE  attendance_records IS '签到记录表';
COMMENT ON COLUMN attendance_records.session_id IS '排期 FK';
COMMENT ON COLUMN attendance_records.user_id IS '学生 FK';
COMMENT ON COLUMN attendance_records.status IS 'PRESENT / LATE / ABSENT / EXCUSED';
COMMENT ON COLUMN attendance_records.checkin_time IS '签到时间（学生自行签到时记录）';
COMMENT ON COLUMN attendance_records.updated_by IS '最后修改人（教师手动修改时记录）';
