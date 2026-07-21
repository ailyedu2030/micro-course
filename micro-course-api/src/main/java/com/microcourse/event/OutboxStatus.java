package com.microcourse.event;

/**
 * Outbox 状态枚举.
 * P1 spec §三.3.1: outbox 行状态机 PENDING → DELIVERED / DEAD_LETTER.
 */
public enum OutboxStatus {
    /** 待 OutboxPoller 推送 */
    PENDING,
    /** 推送成功 (含 Hermes 端 409 去重) */
    DELIVERED,
    /** 5 次重试均失败, 移到 dead_letter 表 */
    DEAD_LETTER
}
