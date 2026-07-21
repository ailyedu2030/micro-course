-- V315__domain_event_dead_letter.sql
-- P1 spec §三.3.3 + §五.5.2: 本地 → Hermes 推送失败最终死信.
-- Hermes 不可用 5 次重试后 → 移到 dead_letter 表 → 24h 自动重试一次.
-- 6 次仍失败 → 永久死信, 人工 ack 处理 (P1 spec §五.5.5).

CREATE TABLE domain_event_dead_letter (
    event_id VARCHAR(64) PRIMARY KEY,
    aggregate_type VARCHAR(32) NOT NULL,
    aggregate_id BIGINT,
    payload JSONB NOT NULL,
    last_error TEXT,
    failed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    operator VARCHAR(32),                                  -- 人工 ack 时填写
    acknowledged_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_dlq_unacked ON domain_event_dead_letter(acknowledged_at)
    WHERE acknowledged_at IS NULL;

CREATE INDEX idx_dlq_failed_at ON domain_event_dead_letter(failed_at);

COMMENT ON TABLE domain_event_dead_letter IS '本地→Hermes 推送最终失败; 人工 ack 或 wait retry after 24h';
COMMENT ON COLUMN domain_event_dead_letter.last_error IS 'status code + body 排错信息';
COMMENT ON COLUMN domain_event_dead_letter.operator IS '人工 ack 的操作人, 用于审计';
