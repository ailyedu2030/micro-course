-- V313__domain_event_outbox.sql
-- P1 spec §三: 本地 → Hermes 推送 outbox 表.
-- 同事务落库, OutboxPoller 5s 一轮扫 PENDING → Hermes webhook.

CREATE TABLE domain_event_outbox (
    event_id VARCHAR(64) PRIMARY KEY,                  -- UUID v4 幂等键
    aggregate_type VARCHAR(32) NOT NULL,               -- COURSE / CHAPTER / SECTION / LESSON
    aggregate_id BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,                   -- CREATED / UPDATED / DELETED / REORDERED
    payload JSONB NOT NULL,                            -- 完整 DTO (Jackson 序列化)
    trace_id VARCHAR(64) NOT NULL,                     -- 链路追踪
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',     -- PENDING / DELIVERED / DEAD_LETTER
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error TEXT,
    delivered_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_outbox_pending ON domain_event_outbox(status, next_attempt_at)
    WHERE status IN ('PENDING');
CREATE INDEX idx_outbox_aggregate ON domain_event_outbox(aggregate_type, aggregate_id, occurred_at DESC);

COMMENT ON TABLE domain_event_outbox IS 'P1 域事件 outbox: 本地 → Hermes 推送通道, 5s 一轮扫 PENDING';
COMMENT ON COLUMN domain_event_outbox.event_id IS 'UUID v4, 与 domain_event_dedup.event_id 共享形成幂等键';
COMMENT ON COLUMN domain_event_outbox.payload IS '完整 DomainEvent JSON, 包含 endpoint + payload 字段';
COMMENT ON COLUMN domain_event_outbox.last_error IS '最近一次推送失败的 status code + body, 用于排错';
