package com.microcourse.event;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.microcourse.event.repository.DomainEventDeadLetterRepository;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

/**
 * P1 plan Task 12: 死信表运营管理.
 * countUnacked: 监控告警
 * acknowledge: 人工 ack (操作员已人工修复)
 */
@Service
public class DeadLetterService {

    private final DomainEventDeadLetterRepository dlqRepo;

    public DeadLetterService(DomainEventDeadLetterRepository dlqRepo) {
        this.dlqRepo = dlqRepo;
    }

    public long countUnacked() {
        return dlqRepo.selectCount(
                new QueryWrapper<DomainEventDeadLetter>().isNull("acknowledged_at"));
    }

    public void acknowledge(String eventId, String operator) {
        DomainEventDeadLetter row = dlqRepo.selectById(eventId);
        if (row == null) {
            throw new IllegalArgumentException("not found: " + eventId);
        }
        row.setOperator(operator);
        row.setAcknowledgedAt(LocalDateTime.now());
        dlqRepo.updateById(row);
    }
}
