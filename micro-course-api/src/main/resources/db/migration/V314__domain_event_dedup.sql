-- V314__domain_event_dedup.sql
-- P1 spec §三.3.2: event_id 幂等表.
-- Hermes 推过来 → 本地用 event_id 去重
-- 本地推出去   → Hermes 端用 event_id 去重 (回送 409 = duplicate)
-- event_id 与 V313 domain_event_outbox.event_id 共享 UUID 命名空间

CREATE TABLE domain_event_dedup (
    event_id VARCHAR(64) PRIMARY KEY,
    source VARCHAR(16) NOT NULL,        -- LOCAL / HERMES
    trace_id VARCHAR(64) NOT NULL,
    processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_dedup_source_time ON domain_event_dedup(source, processed_at DESC);

COMMENT ON TABLE domain_event_dedup IS 'P1 域事件幂等表: event_id 共享 outbox.event_id, 用 INSERT ... ON CONFLICT DO NOTHING 拒重';
COMMENT ON COLUMN domain_event_dedup.source IS '记录这条 event 来自哪边, 用于排查双向并发冲突';
