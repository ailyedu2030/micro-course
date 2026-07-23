package com.microcourse.event;

import com.microcourse.event.HermesEventPushClient.PushException;
import com.microcourse.event.repository.DomainEventDeadLetterRepository;
import com.microcourse.event.repository.DomainEventDedupRepository;
import com.microcourse.event.repository.DomainEventOutboxRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class OutboxPollerWorkerTransactionUnitTest {

    @Test
    void handleOneInTransactionMustRunWithinTransaction() {
        DomainEventOutboxRepository outboxRepo = mock(DomainEventOutboxRepository.class);
        DomainEventDeadLetterRepository dlqRepo = mock(DomainEventDeadLetterRepository.class);
        DomainEventDedupRepository dedupRepo = mock(DomainEventDedupRepository.class);
        HermesEventPushClient pushClient = mock(HermesEventPushClient.class);

        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        when(transactionTemplate.execute(any(TransactionCallback.class))).thenAnswer(inv -> {
            TransactionCallback<?> cb = inv.getArgument(0);
            cb.doInTransaction(mock(TransactionStatus.class));
            return null;
        });

        OutboxPollerWorker worker = new OutboxPollerWorker(
                outboxRepo,
                dlqRepo,
                dedupRepo,
                pushClient,
                new SimpleMeterRegistry(),
                transactionTemplate
        );

        String eventId = "tx-unit-" + System.currentTimeMillis();
        DomainEventOutbox row = new DomainEventOutbox();
        row.setEventId(eventId);
        row.setAggregateType("TEST");
        row.setAggregateId(1L);
        row.setEventType("UPDATED");
        row.setTraceId("trace-" + eventId);
        row.setOccurredAt(LocalDateTime.now());
        row.setStatus(OutboxStatus.PENDING);
        row.setAttemptCount(4);
        row.setNextAttemptAt(LocalDateTime.now());

        DomainEvent event = new DomainEvent()
                .setEventId(eventId)
                .setTraceId(row.getTraceId())
                .setOccurredAt(row.getOccurredAt())
                .setAggregateType(row.getAggregateType())
                .setAggregateId(row.getAggregateId())
                .setEventType(row.getEventType())
                .setEndpoint("/api/test")
                .setPayload(new HashMap<>());
        row.setPayload(event.toJsonPayload());

        when(dedupRepo.existsByEventId(anyString())).thenReturn(false);
        when(pushClient.push(any())).thenThrow(new PushException("network/timeout", 0, "boom"));
        when(dlqRepo.insert(any())).thenReturn(1);
        when(outboxRepo.markDeadLetter(anyString(), any(LocalDateTime.class))).thenReturn(1);

        worker.handleOneInTransaction(row);

        var inOrder = inOrder(transactionTemplate, dlqRepo);
        inOrder.verify(transactionTemplate).execute(any(TransactionCallback.class));
        inOrder.verify(dlqRepo).insert(any());
    }
}
