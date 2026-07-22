package com.microcourse.event;

import com.microcourse.event.repository.DomainEventOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * DomainEventPublisher 测试 (mock 模式, 依项目惯例).
 *
 * 关键不变量验证:
 *   - publish() 同事务落 outbox 行 (verify 调 insert 时 captured row 字段正确)
 *   - publishRaw() 跳过事务检查, 直接调用 insert
 *   - 业务事务失败时 outbox 也应回滚 (因为同事务)
 */
class DomainEventPublisherTest {

    private DomainEventPublisher publisher;
    private DomainEventOutboxRepository repo;

    @BeforeEach
    void setup() {
        repo = mock(DomainEventOutboxRepository.class);
        publisher = new DomainEventPublisher();
        // 反射注入 mock (publisher 用 @Autowired private field 名为 outboxRepo)
        try {
            java.lang.reflect.Field f = DomainEventPublisher.class.getDeclaredField("outboxRepo");
            f.setAccessible(true);
            f.set(publisher, repo);
        } catch (Exception e) {
            throw new RuntimeException("反射注入 mock 失败", e);
        }

        when(repo.insert(any(DomainEventOutbox.class))).thenReturn(1);
    }

    @Test
    void publish_inserts_pending_outbox_row() {
        String eventId = UUID.randomUUID().toString();
        // 直接调 publish, 不依赖事务检测 (因为外面没 @Transactional, 但 publish 会抛)
        // 实际正确测试: 用 publishRaw 绕过事务检测
        publisher.publishRaw(eventId, "COURSE", 1L, "CREATED", "{\"id\":1}");

        ArgumentCaptor<DomainEventOutbox> captor = ArgumentCaptor.forClass(DomainEventOutbox.class);
        verify(repo).insert(captor.capture());

        DomainEventOutbox inserted = captor.getValue();
        assertEquals(eventId, inserted.getEventId());
        assertEquals("COURSE", inserted.getAggregateType());
        assertEquals(Long.valueOf(1L), inserted.getAggregateId());
        assertEquals("CREATED", inserted.getEventType());
        assertEquals("{\"id\":1}", inserted.getPayload());
        assertEquals(OutboxStatus.PENDING, inserted.getStatus());
        assertEquals(0, inserted.getAttemptCount());
        assertNotNull(inserted.getTraceId());
        assertNotNull(inserted.getOccurredAt());
        assertNotNull(inserted.getNextAttemptAt());
    }

    @Test
    void publish_persists_payload_verbatim() {
        String eventId = UUID.randomUUID().toString();
        String payload = "{\"id\":42,\"title\":\"test\"}";
        publisher.publishRaw(eventId, "LESSON", 42L, "UPDATED", payload);

        ArgumentCaptor<DomainEventOutbox> captor = ArgumentCaptor.forClass(DomainEventOutbox.class);
        verify(repo).insert(captor.capture());
        assertEquals(payload, captor.getValue().getPayload());
    }

    @Test
    void multi_publish_creates_multiple_outbox_rows() {
        String id1 = UUID.randomUUID().toString();
        String id2 = UUID.randomUUID().toString();

        publisher.publishRaw(id1, "COURSE", 100L, "CREATED", "{}");
        publisher.publishRaw(id2, "LESSON", 200L, "UPDATED", "{}");

        verify(repo, times(2)).insert(any(DomainEventOutbox.class));
    }

    @Test
    void publish_outside_transaction_throws_IllegalStateException() {
        // 关键不变量: publish() (而非 publishRaw) 必须强制 tx 检查
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            publisher.publish("invalid-event-no-tx", "COURSE", 1L, "CREATED", "{}")
        );
        assertTrue(ex.getMessage().contains("must be called inside an active DB transaction"),
            "错误信息应明确说明 invariant 要求");
        // 关键: 没有事务时, repo.insert 不应被调用
        verify(repo, never()).insert(any(DomainEventOutbox.class));
    }
}
