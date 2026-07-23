package com.microcourse.event.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.event.DomainEventOutbox;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.time.LocalDateTime;
import java.util.List;

/**
 * V313 domain_event_outbox 表 MyBatis-Plus Repository.
 * 关键 SQL:
 *   - listPendingDueNow(LocalDateTime,int): OutboxPoller 5s 扫一次
 *   - markDelivered: 推成功
 *   - markRetry: 重试 + 退避 (next_attempt_at)
 *   - markDeadLetter: 5 次失败 → 死信
 *
 * P1 spec §三.3.1 + §五.5.1.
 */
@Mapper
public interface DomainEventOutboxRepository extends BaseMapper<DomainEventOutbox> {

    @Select("""
        SELECT * FROM domain_event_outbox
        WHERE status = 'PENDING' AND next_attempt_at <= #{now}
        ORDER BY occurred_at ASC
        LIMIT #{limit}
    """)
    List<DomainEventOutbox> listPendingDueNow(@Param("now") LocalDateTime now,
                                              @Param("limit") int limit);

    @Update("""
        UPDATE domain_event_outbox
        SET status = 'DELIVERED',
            delivered_at = #{deliveredAt},
            updated_at = #{updatedAt}
        WHERE event_id = #{eventId}
    """)
    int markDelivered(@Param("eventId") String eventId,
                      @Param("deliveredAt") LocalDateTime deliveredAt,
                      @Param("updatedAt") LocalDateTime updatedAt);

    @Update("""
        UPDATE domain_event_outbox
        SET attempt_count = attempt_count + 1,
            next_attempt_at = #{nextAttemptAt},
            last_error = #{lastError},
            updated_at = #{updatedAt}
        WHERE event_id = #{eventId}
    """)
    int markRetry(@Param("eventId") String eventId,
                  @Param("nextAttemptAt") LocalDateTime nextAttemptAt,
                  @Param("lastError") String lastError,
                  @Param("updatedAt") LocalDateTime updatedAt);

    @Update("""
        UPDATE domain_event_outbox
        SET status = 'DEAD_LETTER',
            updated_at = #{updatedAt}
        WHERE event_id = #{eventId}
    """)
    int markDeadLetter(@Param("eventId") String eventId,
                       @Param("updatedAt") LocalDateTime updatedAt);
}
