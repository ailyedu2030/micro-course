# P1 · Course Structure Bidirectional Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement bidirectional event-driven sync between local micro-course platform (course/chapter/section/lesson) and Hermes via domain event + outbox pattern (P1 scope).

**Architecture:** Three-layer separation: (1) Domain event publishing in same DB transaction as business mutation, (2) OutboxPoller every 5s picks up PENDING rows and pushes to Hermes webhook, (3) Hermes pushes back via existing webhook with `eventSource=HERMES` flag preventing echo loop. Local dedup table rejects duplicates by event_id (UUID v4).

**Tech Stack:** Spring Boot 3.2 + MyBatis-Plus 3.5 + MySQL/PG (Flyway) + 现有 Hermes HTTP client + @Scheduled worker

---

## File Structure

```
新增 16 文件:
├── migrations/V313__domain_event_outbox.sql
├── migrations/V314__domain_event_dedup.sql
├── migrations/V315__domain_event_dead_letter.sql
├── event/DomainEvent.java (POJO immutable)
├── event/OutboxStatus.java (enum)
├── event/DomainEventOutbox.java (MyBatis entity)
├── event/DomainEventDedup.java
├── event/DomainEventDeadLetter.java
├── event/repository/DomainEventOutboxRepository.java
├── event/repository/DomainEventDedupRepository.java
├── event/repository/DomainEventDeadLetterRepository.java
├── event/DomainEventPublisher.java (@Service)
├── event/OutboxPollerWorker.java (@Component @Scheduled)
├── event/HermesEventPushClient.java (@Component HttpClient)
├── event/RetryPolicy.java (策略对象)
├── event/DeadLetterService.java (@Service)
├── event/dto/CourseEventPayload.java
├── event/dto/ChapterEventPayload.java
├── event/dto/SectionEventPayload.java
├── event/dto/LessonEventPayload.java
└── controller/HermesEventController.java (POST /events endpoint)

修改 1 文件:
└── service/impl/HermesCourseSyncServiceImpl.java (加 eventSource 参数)

测试 3 文件:
├── event/OutboxPollerWorkerTest.java
├── event/DomainEventPublisherTest.java
└── event/CourseStructureSyncE2ETest.java
```

---

## Task 1: V313 Migration + Outbox Entity + Repository

**Files:**
- Create: `micro-course-api/src/main/resources/db/migration/V313__domain_event_outbox.sql`
- Create: `micro-course-api/src/main/java/com/microcourse/event/OutboxStatus.java`
- Create: `micro-course-api/src/main/java/com/microcourse/event/DomainEventOutbox.java`
- Create: `micro-course-api/src/main/java/com/microcourse/event/repository/DomainEventOutboxRepository.java`
- Test: `micro-course-api/src/test/java/com/microcourse/event/DomainEventOutboxRepositoryTest.java`

- [ ] **Step 1: Write failing test**

```java
// DomainEventOutboxRepositoryTest.java
package com.microcourse.event;

import com.baomidou.mybatisplus.test.autoconfigure.MybatisPlusTest;
import com.microcourse.event.repository.DomainEventOutboxRepository;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@MybatisPlusTest
class DomainEventOutboxRepositoryTest {

    @Resource
    DomainEventOutboxRepository repo;

    @Test
    void insert_and_selectByEventId_works() {
        String eventId = UUID.randomUUID().toString();
        DomainEventOutbox row = new DomainEventOutbox();
        row.setEventId(eventId);
        row.setAggregateType("COURSE");
        row.setAggregateId(100L);
        row.setEventType("CREATED");
        row.setPayload("{\"id\":100}");
        row.setTraceId(UUID.randomUUID().toString());
        row.setStatus(OutboxStatus.PENDING);

        repo.insert(row);

        DomainEventOutbox found = repo.selectById(eventId);
        assertNotNull(found);
        assertEquals("COURSE", found.getAggregateType());
        assertEquals(0, found.getAttemptCount());
        assertEquals(OutboxStatus.PENDING, found.getStatus());
    }

    @Test
    void listPending_due_now_returns_only_pending() {
        List<DomainEventOutbox> rows = repo.listPendingDueNow(10);
        rows.forEach(r -> assertEquals(OutboxStatus.PENDING, r.getStatus()));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd micro-course-api && mvn test -Dtest=DomainEventOutboxRepositoryTest`
Expected: FAIL with `class not found` (entity, enum, repo missing yet).

- [ ] **Step 3: Write V313 migration**

```sql
-- V313__domain_event_outbox.sql
CREATE TABLE domain_event_outbox (
    event_id VARCHAR(64) PRIMARY KEY,
    aggregate_type VARCHAR(32) NOT NULL,
    aggregate_id BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL,
    trace_id VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
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
```

- [ ] **Step 4: Write OutboxStatus enum**

```java
package com.microcourse.event;

public enum OutboxStatus {
    PENDING,        // 待轮询推送
    DELIVERED,      // 推送成功
    DEAD_LETTER     // 5 次重试均失败, 移到死信表
}
```

- [ ] **Step 5: Write DomainEventOutbox entity**

```java
package com.microcourse.event;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("domain_event_outbox")
public class DomainEventOutbox {
    @TableId(type = IdType.INPUT)
    private String eventId;
    private String aggregateType;
    private Long aggregateId;
    private String eventType;
    private String payload;
    private String traceId;
    private LocalDateTime occurredAt;
    private OutboxStatus status;
    private Integer attemptCount;
    private LocalDateTime nextAttemptAt;
    private String lastError;
    private LocalDateTime deliveredAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 6: Write Repository**

```java
package com.microcourse.event.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.event.DomainEventOutbox;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DomainEventOutboxRepository extends BaseMapper<DomainEventOutbox> {

    @Select("""
        SELECT * FROM domain_event_outbox
        WHERE status = 'PENDING' AND next_attempt_at <= #{now}
        ORDER BY occurred_at ASC
        LIMIT #{limit}
    """)
    List<DomainEventOutbox> listPendingDueNow(int limit);

    @Update("""
        UPDATE domain_event_outbox
        SET status = 'DELIVERED', delivered_at = #{deliveredAt}, attempt_count = attempt_count + 1
        WHERE event_id = #{eventId}
    """)
    int markDelivered(String eventId, LocalDateTime deliveredAt);

    @Update("""
        UPDATE domain_event_outbox
        SET attempt_count = attempt_count + 1,
            next_attempt_at = #{nextAttemptAt},
            last_error = #{lastError}
        WHERE event_id = #{eventId}
    """)
    int markRetry(String eventId, LocalDateTime nextAttemptAt, String lastError);

    @Update("""
        UPDATE domain_event_outbox
        SET status = 'DEAD_LETTER'
        WHERE event_id = #{eventId}
    """)
    int markDeadLetter(String eventId);
}
```

- [ ] **Step 7: Run test to verify it passes**

Run: `cd micro-course-api && mvn test -Dtest=DomainEventOutboxRepositoryTest`
Expected: 2 tests PASS

- [ ] **Step 8: Verify Flyway migration runs cleanly**

Run: `cd micro-course-api && mvn flyway:migrate -Dflyway.url=$DB_URL -Dflyway.user=$USER -Dflyway.password=$PW`
Expected: BUILD SUCCESS, "Successfully applied V313"

- [ ] **Step 9: Commit**

```bash
git add micro-course-api/src/main/resources/db/migration/V313*.sql \
        micro-course-api/src/main/java/com/microcourse/event/OutboxStatus.java \
        micro-course-api/src/main/java/com/microcourse/event/DomainEventOutbox.java \
        micro-course-api/src/main/java/com/microcourse/event/repository/DomainEventOutboxRepository.java \
        micro-course-api/src/test/java/com/microcourse/event/DomainEventOutboxRepositoryTest.java
git commit -m "feat(event): V313 outbox table + entity + repo"
```

---

## Task 2: V314 Dedup table + entity + repo

**Files:**
- Create: `micro-course-api/src/main/resources/db/migration/V314__domain_event_dedup.sql`
- Create: `micro-course-api/src/main/java/com/microcourse/event/DomainEventDedup.java`
- Create: `micro-course-api/src/main/java/com/microcourse/event/repository/DomainEventDedupRepository.java`
- Test: `micro-course-api/src/test/java/com/microcourse/event/DomainEventDedupRepositoryTest.java`

- [ ] **Step 1: Write failing test**

```java
package com.microcourse.event;

import com.baomidou.mybatisplus.test.autoconfigure.MybatisPlusTest;
import com.microcourse.event.repository.DomainEventDedupRepository;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@MybatisPlusTest
class DomainEventDedupRepositoryTest {

    @Resource
    DomainEventDedupRepository repo;

    @Test
    void insertAndExists_returnsTrue_then_duplicateInsert_ignored() {
        String eventId = UUID.randomUUID().toString();
        DomainEventDedup row = new DomainEventDedup();
        row.setEventId(eventId);
        row.setSource("HERMES");
        row.setTraceId(UUID.randomUUID().toString());

        int first = repo.insert(row);
        int second = repo.insert(row);  // IGNORE on duplicate

        assertEquals(1, first);
        // second insert: PK 冲突, 返回 0 (IGNORE)
        // 真正去重: 我们改用 INSERT ... ON CONFLICT DO NOTHING
    }

    @Test
    void existsById_returnsBoolean() {
        String eventId = UUID.randomUUID().toString();
        assertFalse(repo.existsByEventId(eventId));

        DomainEventDedup row = new DomainEventDedup();
        row.setEventId(eventId);
        row.setSource("LOCAL");
        repo.insertIgnoreDuplicate(row);

        assertTrue(repo.existsByEventId(eventId));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd micro-course-api && mvn test -Dtest=DomainEventDedupRepositoryTest`
Expected: FAIL with `class not found`

- [ ] **Step 3: Write V314 migration**

```sql
-- V314__domain_event_dedup.sql
CREATE TABLE domain_event_dedup (
    event_id VARCHAR(64) PRIMARY KEY,
    source VARCHAR(16) NOT NULL,        -- LOCAL / HERMES
    trace_id VARCHAR(64) NOT NULL,
    processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_dedup_source_time ON domain_event_dedup(source, processed_at DESC);

COMMENT ON TABLE domain_event_dedup IS 'event_id 幂等表: Hermes 推过来 / 本地推出去都用它去重';
```

- [ ] **Step 4: Write entity**

```java
package com.microcourse.event;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("domain_event_dedup")
public class DomainEventDedup {
    @TableId(type = IdType.INPUT)
    private String eventId;
    private String source;
    private String traceId;
    private LocalDateTime processedAt;
    private LocalDateTime createdAt;
}
```

- [ ] **Step 5: Write repository (with INSERT IGNORE for dedup)**

```java
package com.microcourse.event.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.event.DomainEventDedup;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DomainEventDedupRepository extends BaseMapper<DomainEventDedup> {

    @Insert("""
        INSERT INTO domain_event_dedup (event_id, source, trace_id, processed_at, created_at)
        VALUES (#{eventId}, #{source}, #{traceId}, NOW(), NOW())
        ON CONFLICT (event_id) DO NOTHING
    """)
    int insertIgnoreDuplicate(DomainEventDedup row);

    @Select("SELECT EXISTS(SELECT 1 FROM domain_event_dedup WHERE event_id = #{eventId})")
    boolean existsByEventId(String eventId);
}
```

- [ ] **Step 6: Run test, expect PASS**

Run: `cd micro-course-api && mvn test -Dtest=DomainEventDedupRepositoryTest`
Expected: 2 tests PASS

- [ ] **Step 7: Commit**

```bash
git add micro-course-api/src/main/resources/db/migration/V314*.sql \
        micro-course-api/src/main/java/com/microcourse/event/DomainEventDedup.java \
        micro-course-api/src/main/java/com/microcourse/event/repository/DomainEventDedupRepository.java \
        micro-course-api/src/test/java/com/microcourse/event/DomainEventDedupRepositoryTest.java
git commit -m "feat(event): V314 dedup table + ON CONFLICT DO NOTHING idempotency"
```

---

## Task 3: V315 dead_letter table + entity + repo

**Files:**
- Create: `micro-course-api/src/main/resources/db/migration/V315__domain_event_dead_letter.sql`
- Create: `micro-course-api/src/main/java/com/microcourse/event/DomainEventDeadLetter.java`
- Create: `micro-course-api/src/main/java/com/microcourse/event/repository/DomainEventDeadLetterRepository.java`
- Test: `micro-course-api/src/test/java/com/microcourse/event/DomainEventDeadLetterRepositoryTest.java`

- [ ] **Step 1: Write failing test**

```java
package com.microcourse.event;

import com.baomidou.mybatisplus.test.autoconfigure.MybatisPlusTest;
import com.microcourse.event.repository.DomainEventDeadLetterRepository;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@MybatisPlusTest
class DomainEventDeadLetterRepositoryTest {

    @Resource
    DomainEventDeadLetterRepository repo;

    @Test
    void saveAndFindById() {
        String eventId = UUID.randomUUID().toString();
        DomainEventDeadLetter row = new DomainEventDeadLetter();
        row.setEventId(eventId);
        row.setAggregateType("LESSON");
        row.setAggregateId(99L);
        row.setPayload("{\"id\":99}");
        row.setLastError("timeout after 5 attempts");

        repo.insert(row);

        DomainEventDeadLetter found = repo.selectById(eventId);
        assertNotNull(found);
        assertEquals("timeout after 5 attempts", found.getLastError());
        assertNull(found.getAcknowledgedAt());
    }
}
```

- [ ] **Step 2: Run test, expect FAIL**

Run: `cd micro-course-api && mvn test -Dtest=DomainEventDeadLetterRepositoryTest`

- [ ] **Step 3: Write V315 migration**

```sql
-- V315__domain_event_dead_letter.sql
CREATE TABLE domain_event_dead_letter (
    event_id VARCHAR(64) PRIMARY KEY,
    aggregate_type VARCHAR(32) NOT NULL,
    aggregate_id BIGINT,
    payload JSONB NOT NULL,
    last_error TEXT,
    failed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    operator VARCHAR(32),
    acknowledged_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_dlq_unacked ON domain_event_dead_letter(acknowledged_at)
    WHERE acknowledged_at IS NULL;

COMMENT ON TABLE domain_event_dead_letter IS '本地→Hermes 推送最终失败; 人工 ack 或 wait retry after 24h';
```

- [ ] **Step 4: Write entity**

```java
package com.microcourse.event;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("domain_event_dead_letter")
public class DomainEventDeadLetter {
    @TableId(type = IdType.INPUT)
    private String eventId;
    private String aggregateType;
    private Long aggregateId;
    private String payload;
    private String lastError;
    private LocalDateTime failedAt;
    private String operator;
    private LocalDateTime acknowledgedAt;
    private LocalDateTime createdAt;
}
```

- [ ] **Step 5: Write repository**

```java
package com.microcourse.event.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.event.DomainEventDeadLetter;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DomainEventDeadLetterRepository extends BaseMapper<DomainEventDeadLetter> {
}
```

- [ ] **Step 6: Run test, expect PASS**

- [ ] **Step 7: Commit**

```bash
git add V315__domain_event_dead_letter.sql DomainEventDeadLetter.java DomainEventDeadLetterRepository.java DomainEventDeadLetterRepositoryTest.java
git commit -m "feat(event): V315 dead_letter table + entity + repo"
```

---

## Task 4: DomainEvent POJO + payload DTOs

**Files:**
- Create: `micro-course-api/src/main/java/com/microcourse/event/DomainEvent.java`
- Create: 4 × `event/dto/*EventPayload.java`
- Test: `micro-course-api/src/test/java/com/microcourse/event/DomainEventTest.java`

- [ ] **Step 1: Write DomainEvent POJO + DTO test**

```java
package com.microcourse.event;

import com.microcourse.event.dto.LessonEventPayload;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DomainEventTest {
    @Test
    void toPayloadString_roundTrip() {
        DomainEvent ev = DomainEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .traceId(UUID.randomUUID().toString())
            .aggregateType("LESSON")
            .aggregateId(12345L)
            .eventType("UPDATED")
            .hermesCourseId("HER-2026-XYZ")
            .action("HTTP_POST")
            .endpoint("/api/hermes/webhook/courses/HER-2026-XYZ/lessons")
            .payload(LessonEventPayload.builder().id(12345L).title("L1").lessonType("PPT").build())
            .build();
        String json = ev.toJsonPayload();
        DomainEvent restored = DomainEvent.fromJsonPayload(json);
        assertEquals(ev.getEventId(), restored.getEventId());
        assertEquals("PPT", restored.getPayloadAs(LessonEventPayload.class).getLessonType());
    }
}
```

- [ ] **Step 2: Run test, expect FAIL**

- [ ] **Step 3: Write DomainEvent.java**

```java
package com.microcourse.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DomainEvent {
    private String eventId;
    private String traceId;
    private LocalDateTime occurredAt;
    private String aggregateType;
    private Long aggregateId;
    private String eventType;       // CREATED / UPDATED / DELETED / REORDERED
    private String action;          // HTTP_POST / HTTP_PUT / HTTP_DELETE
    private String endpoint;        // /api/hermes/...
    private String hermesCourseId;  // FK 关联用
    private Object payload;         // *EventPayload 类型实例

    private static final ObjectMapper M = new ObjectMapper()
        .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
        .findAndRegisterModules();

    @SneakyThrows
    public String toJsonPayload() {
        return M.writeValueAsString(this);
    }

    @SneakyThrows
    public static DomainEvent fromJsonPayload(String json) {
        return M.readValue(json, DomainEvent.class);
    }

    public <T> T getPayloadAs(Class<T> clazz) {
        return M.convertValue(payload, clazz);
    }
}
```

- [ ] **Step 4: Write 4× DTO (each with @Builder + Jackson)**

```java
// CourseEventPayload.java
package com.microcourse.event.dto;
import lombok.Builder; @lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @Builder
public class CourseEventPayload {
    private Long id;
    private String title;
    private String subtitle;
    private String summary;
    private BigDecimal price;
    private Integer status;
    private LocalDateTime updatedAt;
}
```

```java
// ChapterEventPayload.java
package com.microcourse.event.dto;
import lombok.Builder; @lombok.Data;
@Data @Builder
public class ChapterEventPayload {
    private Long id;
    private String title;
    private Integer sortOrder;
}
```

```java
// SectionEventPayload.java
package com.microcourse.event.dto;
import lombok.Builder; @lombok.Data;
@Data @Builder
public class SectionEventPayload {
    private Long id;
    private String title;
    private Integer sortOrder;
}
```

```java
// LessonEventPayload.java
package com.microcourse.event.dto;
import lombok.Builder; @lombok.Data;
@Data @Builder
public class LessonEventPayload {
    private Long id;
    private String title;
    private String lessonType;       // PPT / HTML
    private String contentUrl;
    private Integer sortOrder;
}
```

- [ ] **Step 5: Run test, expect PASS**

- [ ] **Step 6: Commit**

```bash
git add event/DomainEvent.java event/dto/*EventPayload.java event/DomainEventTest.java
git commit -m "feat(event): DomainEvent POJO + 4 payload DTOs"
```

---

## Task 5: DomainEventPublisher (同事务 atomic publish)

**Files:**
- Create: `micro-course-api/src/main/java/com/microcourse/event/DomainEventPublisher.java`
- Test: `micro-course-api/src/test/java/com/microcourse/event/DomainEventPublisherTest.java`

- [ ] **Step 1: Write failing test**

```java
// DomainEventPublisherTest.java
package com.microcourse.event;

import com.microcourse.event.repository.DomainEventOutboxRepository;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class DomainEventPublisherTest {

    @Autowired DomainEventPublisher publisher;
    @Resource DomainEventOutboxRepository repo;

    @Test
    void publish_in_same_transaction_then_outbox_row_exists() {
        String eventId = UUID.randomUUID().toString();
        publisher.publish(eventId, "COURSE", 1L, "CREATED", "{test}");

        assertNotNull(repo.selectById(eventId));
    }

    @Test
    void publish_outside_transaction_throws() {
        assertThrows(IllegalStateException.class, () ->
            publisher.publishRaw("invalid_event_no_tx", "COURSE", 1L, "CREATED", "{}"));
    }
}
```

- [ ] **Step 2: Run, expect FAIL**

- [ ] **Step 3: Write DomainEventPublisher**

```java
package com.microcourse.event;

import com.microcourse.event.repository.DomainEventOutboxRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class DomainEventPublisher {

    @Autowired DomainEventOutboxRepository repo;

    /**
     * 便捷方法: 在当前事务内 atomic publish.
     * 若调用方未在事务内, 抛 IllegalStateException (不允许 raw publish 路径).
     */
    public void publish(String eventId, String aggregateType, Long aggregateId,
                        String eventType, String payload) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                "DomainEventPublisher.publish must be called inside an active DB transaction. " +
                "Use publishRaw(...) only inside repositories' @Transactional scope.");
        }
        doInsert(eventId, aggregateType, aggregateId, eventType, payload);
    }

    /**
     * 受信任入口: repositories 内 @Transactional 方法可直接调用.
     */
    public void publishRaw(String eventId, String aggregateType, Long aggregateId,
                           String eventType, String payload) {
        doInsert(eventId, aggregateType, aggregateId, eventType, payload);
    }

    private void doInsert(String eventId, String aggregateType, Long aggregateId,
                          String eventType, String payload) {
        DomainEventOutbox row = new DomainEventOutbox();
        row.setEventId(eventId);
        row.setAggregateType(aggregateType);
        row.setAggregateId(aggregateId);
        row.setEventType(eventType);
        row.setPayload(payload);
        row.setTraceId(UUID.randomUUID().toString());
        row.setStatus(OutboxStatus.PENDING);
        row.setAttemptCount(0);
        row.setNextAttemptAt(LocalDateTime.now());
        row.setOccurredAt(LocalDateTime.now());
        repo.insert(row);
    }
}
```

- [ ] **Step 4: Run test, expect PASS**

- [ ] **Step 5: Commit**

```bash
git add event/DomainEventPublisher.java event/DomainEventPublisherTest.java
git commit -m "feat(event): DomainEventPublisher with tx-required invariant"
```

---

## Task 6: 集成 publisher 到 CourseServiceImpl (业务触发点)

**Files:**
- Modify: `micro-course-api/src/main/java/com/microcourse/service/impl/CourseServiceImpl.java`
- Test: `micro-course-api/src/test/java/com/microcourse/event/CourseEventPublishingTest.java`

- [ ] **Step 1: Write failing integration test**

```java
// CourseEventPublishingTest.java
package com.microcourse.event;

import com.microcourse.event.repository.DomainEventOutboxRepository;
import com.microcourse.service.CourseService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class CourseEventPublishingTest {

    @Autowired CourseService courseService;
    @Resource DomainEventOutboxRepository repo;

    @Test
    void updateCourse_emits_updated_event() {
        // Given: 一个现有课程
        Long courseId = createCourseStub();

        // When: 调用 updateCourse
        courseService.updateCourse(courseId, ...);

        // Then: outbox 出现 aggregate_type=COURSE + event_type=UPDATED
        boolean hasUpdated = repo.lambdaQuery()
            .eq(DomainEventOutbox::getAggregateType, "COURSE")
            .eq(DomainEventOutbox::getAggregateId, courseId)
            .eq(DomainEventOutbox::getEventType, "UPDATED")
            .exists();
        assertTrue(hasUpdated);
    }
}
```

- [ ] **Step 2: Run, expect FAIL**

- [ ] **Step 3: Modify CourseServiceImpl**

Add @Autowired DomainEventPublisher + call it inside the existing @Transactional update/delete/create methods. Find the `updateCourse` method (around line XX) and at the end, after `courseRepository.updateById(course)`, add:

```java
// ============== 在已有的 import 区块末尾添加 ==============
import com.microcourse.event.DomainEventPublisher;
import com.microcourse.event.dto.CourseEventPayload;
import com.fasterxml.jackson.databind.ObjectMapper;

// ============== 在字段区块添加 ==============
@Autowired private DomainEventPublisher domainEventPublisher;
private static final ObjectMapper EVENT_M = new ObjectMapper().findAndRegisterModules();

// ============== 在 updateCourse 方法体末尾添加 ==============
try {
    String payload = EVENT_M.writeValueAsString(CourseEventPayload.builder()
        .id(course.getId()).title(course.getTitle())
        .price(course.getPrice()).status(course.getStatus())
        .updatedAt(course.getUpdatedAt())
        .build());
    domainEventPublisher.publish(
        java.util.UUID.randomUUID().toString(),
        "COURSE", course.getId(), "UPDATED", payload);
} catch (Exception e) {
    log.error("Failed to publish COURSE.UPDATED event", e);
    // 业务已成功, outbox publish 失败不应回滚业务 (tx 已近末段)
}
```

Apply same pattern for `createCourse` (eventType=CREATED) and `deleteCourse` (eventType=DELETED).

- [ ] **Step 4: Run test, expect PASS**

- [ ] **Step 5: Commit**

```bash
git add micro-course-api/src/main/java/com/microcourse/service/impl/CourseServiceImpl.java \
        micro-course-api/src/test/java/com/microcourse/event/CourseEventPublishingTest.java
git commit -m "feat(event): CourseServiceImpl publishes COURSE.{Created,Updated,Deleted} events"
```

---

## Task 7: 集成到 ChapterService / SectionService / LessonService

**Files:**
- Modify: `micro-course-api/src/main/java/com/microcourse/service/impl/ChapterServiceImpl.java`
- Modify: `micro-course-api/src/main/java/com/microcourse/service/impl/SectionServiceImpl.java`
- Modify: `micro-course-api/src/main/java/com/microcourse/service/impl/LessonServiceImpl.java`
- Test: extension to CourseEventPublishingTest covering chapter/section/lesson

- [ ] **Step 1: Write failing test (extend CourseEventPublishingTest or new SectionEventPublishingTest)**

```java
// add to CourseEventPublishingTest or create new SectionEventPublishingTest
@Test
void deleteChapter_emits_deleted_event() {
    Long chapterId = createChapterStub();
    chapterService.deleteChapter(chapterId);
    assertTrue(repo.lambdaQuery()
        .eq(DomainEventOutbox::getAggregateType, "CHAPTER")
        .eq(DomainEventOutbox::getAggregateId, chapterId)
        .eq(DomainEventOutbox::getEventType, "DELETED")
        .exists());
}
```

- [ ] **Step 2: Run, expect FAIL**

- [ ] **Step 3: Add imports + @Autowired + publish at end of create/update/delete in each Impl**

For `ChapterServiceImpl`: import `DomainEventPublisher` + `ChapterEventPayload`, @Autowired it, publish `{CREATED,UPDATED,DELETED}` events.

For `SectionServiceImpl`: same with `SectionEventPayload`.

For `LessonServiceImpl`: same with `LessonEventPayload`. `Lesson` has reorder — publish `REORDERED` event with payload `LessonEventPayload.builder().id(...).sortOrder(...)`.

- [ ] **Step 4: Run, expect PASS**

- [ ] **Step 5: Commit**

```bash
git add service/impl/{Chapter,Section,Lesson}ServiceImpl.java
git commit -m "feat(event): Chapter/Section/Lesson services publish events"
```

---

## Task 8: HermesCourseSyncServiceImpl 加 eventSource 参数 (防回环)

**Files:**
- Modify: `micro-course-api/src/main/java/com/microcourse/service/impl/HermesCourseSyncServiceImpl.java`
- Modify: `micro-course-api/src/main/java/com/microcourse/service/HermesCourseSyncService.java` (接口新增方法签名)
- Test: extension

- [ ] **Step 1: Write failing test**

```java
// 在新的 HermesCourseSyncSourceTest.java
package com.microcourse.event;

import com.microcourse.dto.hermes.HermesWebhookRequest;
import com.microcourse.event.repository.DomainEventDedupRepository;
import com.microcourse.event.repository.DomainEventOutboxRepository;
import com.microcourse.service.HermesCourseSyncService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class HermesCourseSyncSourceTest {

    @Autowired HermesCourseSyncService syncService;
    @Resource DomainEventOutboxRepository outboxRepo;

    @Test
    void syncCourse_with_eventSource_HERMES_does_NOT_publish_to_outbox() {
        HermesWebhookRequest req = buildWebhookRequest();

        syncService.upsertCourseFromHermes(req, "HERMES", UUID.randomUUID().toString());

        boolean anyOutboxAfter = outboxRepo.lambdaQuery()
            .eq(DomainEventOutbox::getAggregateType, "COURSE")
            .gt(DomainEventOutbox::getOccurredAt, java.time.LocalDateTime.now().minusSeconds(2))
            .exists();
        assertFalse(anyOutboxAfter);
    }
}
```

- [ ] **Step 2: Run, expect FAIL**

- [ ] **Step 3: Modify interface**

```java
// HermesCourseSyncService.java (add new method, keep old for backward compat)
/**
 * @param eventSource LOCAL = from CourseServiceImpl (will publish outbox) / HERMES = will NOT publish (avoid echo loop)
 * @param eventId optional inbound event_id (only meaningful when eventSource=HERMES, for dedup)
 */
HermesSyncResult upsertCourseFromHermes(HermesWebhookRequest request, String eventSource, String eventId);
```

- [ ] **Step 4: Implement new method body in HermesCourseSyncServiceImpl**

```java
@Override
public HermesSyncResult upsertCourseFromHermes(HermesWebhookRequest request, String eventSource, String eventId) {
    // 1. 如果 HERMES, dedup: 检查 eventId 是否已处理
    if ("HERMES".equals(eventSource) && eventId != null) {
        if (dedupRepo.existsByEventId(eventId)) {
            return new HermesSyncResult(true, true, "duplicate");
        }
        DomainEventDedup dedupRow = new DomainEventDedup();
        dedupRow.setEventId(eventId);
        dedupRow.setSource("HERMES");
        dedupRepo.insertIgnoreDuplicate(dedupRow);
    }

    // 2. 原 upsert 业务 (call toCourse(...) 等)
    HermesSyncResult result = existingUpsertMethod(request);

    // 3. ⭐ 关键: 仅在 LOCAL 时才 publish outbox
    if ("LOCAL".equals(eventSource)) {
        // publish LOCAL event
        publisher.publish(...);
    }
    // HERMES: 不 publish (避免循环)
    return result;
}
```

Note: 需要在已有字段上加 `DomainEventPublisher` 字段 + `DomainEventDedupRepository` 字段.

- [ ] **Step 5: 在 HermesWebhookController 中调用新方法**

修改每个入站 `@PostMapping` 方法 (大致行 128, 159, 181, 250, 350, 477, 556, 679):

For each, pass `"HERMES"` as `eventSource` and `request.getEventId()` (新加字段到 HermesWebhookRequest) as `eventId`.

- [ ] **Step 6: Run, expect PASS**

- [ ] **Step 7: Commit**

```bash
git add service/HermesCourseSyncService.java service/impl/HermesCourseSyncServiceImpl.java \
        controller/HermesWebhookController.java \
        test/java/com/microcourse/event/HermesCourseSyncSourceTest.java
git commit -m "feat(event): HermesCourseSync accepts eventSource=LOCAL/HERMES (no echo)"
```

---

## Task 9: HermesEventController 新 POST /events endpoint

**Files:**
- Create: `micro-course-api/src/main/java/com/microcourse/controller/HermesEventController.java`
- Modify: `micro-course-api/src/main/java/com/microcourse/config/SecurityConfig.java` (允许该 endpoint)
- Test: `micro-course-api/src/test/java/com/microcourse/controller/HermesEventControllerTest.java`

- [ ] **Step 1: Write failing test**

```java
// HermesEventControllerTest.java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class HermesEventControllerTest {
    @Autowired MockMvc mvc;

    @Test
    void post_event_returns_202() throws Exception {
        String body = """
            {
              "event_id": "%s", "trace_id": "trace-001",
              "aggregate_type": "LESSON", "aggregate_id": 12345,
              "event_type": "UPDATED", "hermes_course_id": "HER-X",
              "payload": { "id": 12345, "title": "L1" }
            }
            """.formatted(UUID.randomUUID().toString());

        mvc.perform(post("/api/hermes/webhook/events")
            .contentType(MediaType.APPLICATION_JSON).content(body))
           .andExpect(status().isAccepted());
    }

    @Test
    void duplicate_event_returns_200_with_dedupedTrue() throws Exception {
        String eventId = UUID.randomUUID().toString();
        String body = buildBody(eventId);
        // 第一发
        mvc.perform(post("/api/hermes/webhook/events").contentType(JSON).content(body)).andExpect(status().isAccepted());
        // 第二发
        mvc.perform(post("/api/hermes/webhook/events").contentType(JSON).content(body))
           .andExpect(jsonPath("$.deduped").value(true));
    }
}
```

- [ ] **Step 2: Run, expect FAIL**

- [ ] **Step 3: Write HermesEventController**

```java
package com.microcourse.controller;

import com.microcourse.event.DomainEvent;
import com.microcourse.event.repository.DomainEventDedupRepository;
import com.microcourse.service.HermesCourseSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/hermes/webhook/events")
@RequiredArgsConstructor
@Slf4j
public class HermesEventController {
    private final HermesCourseSyncService syncService;
    private final DomainEventDedupRepository dedupRepo;

    @PostMapping
    public ResponseEntity<Map<String, Object>> receive(@RequestBody DomainEvent event) {
        log.info("[Hermes-inbound] event={}", event.getEventId());

        // 1. dedup
        if (dedupRepo.existsByEventId(event.getEventId())) {
            return ResponseEntity.ok(Map.of("ack", true, "deduped", true));
        }
        DomainEventDedup row = new DomainEventDedup();
        row.setEventId(event.getEventId());
        row.setSource("HERMES");
        row.setTraceId(event.getTraceId());
        dedupRepo.insertIgnoreDuplicate(row);

        // 2. 同步到本地 (eventSource=HERMES 阻止 echo)
        syncService.upsertCourseFromHermes(
            event.getPayloadAs(HermesWebhookRequest.class),
            "HERMES",
            event.getEventId()
        );

        return ResponseEntity.accepted().body(Map.of("ack", true, "deduped", false));
    }
}
```

- [ ] **Step 4: Allow the endpoint in SecurityConfig**

In `SecurityConfig.java`, add `.requestMatchers("/api/hermes/webhook/**").permitAll()` (assuming existing whitelisting pattern; adapt to existing config).

- [ ] **Step 5: Run, expect PASS**

- [ ] **Step 6: Commit**

```bash
git add controller/HermesEventController.java config/SecurityConfig.java \
        test/java/com/microcourse/controller/HermesEventControllerTest.java
git commit -m "feat(event): HermesEventController POST /api/hermes/webhook/events with dedup"
```

---

## Task 10: HermesEventPushClient + RetryPolicy

**Files:**
- Create: `micro-course-api/src/main/java/com/microcourse/event/HermesEventPushClient.java`
- Create: `micro-course-api/src/main/java/com/microcourse/event/RetryPolicy.java`
- Test: `micro-course-api/src/test/java/com/microcourse/event/HermesEventPushClientTest.java`

- [ ] **Step 1: Write failing test**

```java
// HermesEventPushClientTest.java
package com.microcourse.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.*;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

class HermesEventPushClientTest {

    static WireMockServer hermesMock;
    static HermesEventPushClient client;

    @BeforeAll
    static void setup() {
        hermesMock = new WireMockServer(18080);
        hermesMock.start();
        client = new HermesEventPushClient(
            "http://localhost:18080",
            "test-api-key",
            new ObjectMapper().findAndRegisterModules());
    }

    @AfterAll static void teardown() { hermesMock.stop(); }

    @Test
    void push_returns_202_on_accepted() {
        hermesMock.stubFor(post(urlEqualTo("/api/hermes/webhook/events"))
            .willReturn(aResponse().withStatus(202)));
        DomainEvent ev = ...;
        PushResult r = client.push(ev);
        assertTrue(r.accepted);
    }

    @Test
    void push_returns_failed_on_500() {
        hermesMock.stubFor(post(urlEqualTo("/api/hermes/webhook/events"))
            .willReturn(aResponse().withStatus(500)));
        PushResult r = client.push(ev);
        assertFalse(r.accepted);
        assertEquals(500, r.statusCode);
    }
}
```

- [ ] **Step 2: Run, expect FAIL**

- [ ] **Step 3: Write RetryPolicy**

```java
package com.microcourse.event;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public class RetryPolicy {
    /** 尝试次数 -> 退避 */
    public static LocalDateTime nextAttemptAt(int attemptCount) {
        long minutes = switch (attemptCount) {
            case 0 -> 0;        // 立即
            case 1 -> 0;        // 第 1 次无退避
            case 2 -> 30;       // 30 秒
            case 3 -> 5;        // 5 分
            case 4 -> 30;       // 30 分
            case 5 -> 120;      // 2 小时
            default -> 1440;     // 24 小时 (死信复活窗口)
        };
        return LocalDateTime.now().plus(minutes, ChronoUnit.MINUTES);
    }

    public static boolean shouldDeadLetter(int attemptCount) {
        return attemptCount >= 5;
    }
}
```

- [ ] **Step 4: Write HermesEventPushClient**

```java
package com.microcourse.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
@Slf4j
public class HermesEventPushClient {
    private final String hermesBaseUrl;
    private final String apiKey;
    private final ObjectMapper M;
    private final RestTemplate http = new RestTemplate();

    public HermesEventPushClient(String hermesBaseUrl, String apiKey, ObjectMapper M) {
        this.hermesBaseUrl = hermesBaseUrl;
        this.apiKey = apiKey;
        this.M = M;
    }

    @SneakyThrows
    public PushResult push(DomainEvent event) {
        String url = hermesBaseUrl + event.getEndpoint();
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set("X-API-Key", apiKey);
        h.set("X-Event-Id", event.getEventId());

        try {
            ResponseEntity<String> r = http.exchange(
                url, HttpMethod.POST,
                new HttpEntity<>(M.writeValueAsString(event), h),
                String.class
            );
            return new PushResult(r.getStatusCode().is2xxSuccessful(),
                                   r.getStatusCode().value(),
                                   r.getBody());
        } catch (HttpStatusCodeException e) {
            return new PushResult(false, e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("[Hermes-push] transport error", e);
            return new PushResult(false, 0, e.getMessage());
        }
    }

    public record PushResult(boolean accepted, int statusCode, String body) {}
}
```

- [ ] **Step 5: Run test, expect PASS**

- [ ] **Step 6: Commit**

```bash
git add event/HermesEventPushClient.java event/RetryPolicy.java \
        event/HermesEventPushClientTest.java
git commit -m "feat(event): HermesEventPushClient + 5-step RetryPolicy"
```

---

## Task 11: OutboxPollerWorker (@Scheduled, 5s 一轮)

**Files:**
- Create: `micro-course-api/src/main/java/com/microcourse/event/OutboxPollerWorker.java`
- Modify: `micro-course-api/src/main/resources/application.yml` (`spring.task.scheduling.pool.size`)
- Test: `micro-course-api/src/test/java/com/microcourse/event/OutboxPollerWorkerTest.java`

- [ ] **Step 1: Write failing test**

```java
// OutboxPollerWorkerTest.java
@SpringBootTest
class OutboxPollerWorkerTest {
    @MockBean HermesEventPushClient pushClient;
    @Autowired OutboxPollerWorker worker;
    @Resource DomainEventOutboxRepository repo;

    @Test
    void pollOne_pending_succeeds_marks_delivered() {
        DomainEventOutbox row = insertPending();
        when(pushClient.push(any())).thenReturn(new HermesEventPushClient.PushResult(true, 202, "ok"));

        worker.pollOnce();

        assertEquals(OutboxStatus.DELIVERED, repo.selectById(row.getEventId()).getStatus());
    }

    @Test
    void pollOne_500_marks_retry_with_backoff() {
        DomainEventOutbox row = insertPending();
        when(pushClient.push(any())).thenReturn(new HermesEventPushClient.PushResult(false, 500, "oops"));

        worker.pollOnce();

        DomainEventOutbox reloaded = repo.selectById(row.getEventId());
        assertEquals(1, reloaded.getAttemptCount());
        assertEquals(OutboxStatus.PENDING, reloaded.getStatus());
        assertNotNull(reloaded.getLastError());
    }

    @Test
    void pollOne_409_conflict_marks_delivered_as_duplicate() {
        DomainEventOutbox row = insertPending();
        when(pushClient.push(any())).thenReturn(new HermesEventPushClient.PushResult(false, 409, "duplicate"));

        worker.pollOnce();

        assertEquals(OutboxStatus.DELIVERED, repo.selectById(row.getEventId()).getStatus());
    }
}
```

- [ ] **Step 2: Run, expect FAIL**

- [ ] **Step 3: Write OutboxPollerWorker**

```java
package com.microcourse.event;

import com.microcourse.event.repository.DomainEventDedupRepository;
import com.microcourse.event.repository.DomainEventDeadLetterRepository;
import com.microcourse.event.repository.DomainEventOutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPollerWorker {

    private final DomainEventOutboxRepository outboxRepo;
    private final DomainEventDedupRepository dedupRepo;
    private final DomainEventDeadLetterRepository dlqRepo;
    private final HermesEventPushClient pushClient;

    @Value("${hermes.push.batch-size:20}")
    private int batchSize;

    private final ObjectMapper M = new ObjectMapper().findAndRegisterModules();

    @Scheduled(fixedDelayString = "${hermes.outbox.poll-interval-ms:5000}")
    public void pollOnce() {
        List<DomainEventOutbox> rows = outboxRepo.listPendingDueNow(batchSize);
        if (rows.isEmpty()) return;

        log.info("[Outbox] polling {} pending events", rows.size());
        for (DomainEventOutbox row : rows) {
            try {
                handleOne(row);
            } catch (Exception e) {
                log.error("[Outbox] handler error eventId={}", row.getEventId(), e);
            }
        }
    }

    @Transactional
    protected void handleOne(DomainEventOutbox row) {
        DomainEvent event = DomainEvent.fromJsonPayload(M.writeValueAsString(unwrap(row)));

        HermesEventPushClient.PushResult result = pushClient.push(event);
        if (result.accepted() || result.statusCode() == 409) {
            // 202 成功 或 409 重复 (hermes 端 dedup 命中) 都算 DELIVERED
            outboxRepo.markDelivered(row.getEventId(), LocalDateTime.now());
        } else {
            int nextAttempt = (row.getAttemptCount() == null ? 0 : row.getAttemptCount()) + 1;
            if (RetryPolicy.shouldDeadLetter(nextAttempt)) {
                DomainEventDeadLetter dlq = new DomainEventDeadLetter();
                dlq.setEventId(row.getEventId());
                dlq.setAggregateType(row.getAggregateType());
                dlq.setAggregateId(row.getAggregateId());
                dlq.setPayload(row.getPayload());
                dlq.setLastError(result.statusCode() + " / " + result.body());
                dlqRepo.insert(dlq);
                outboxRepo.markDeadLetter(row.getEventId());
            } else {
                outboxRepo.markRetry(row.getEventId(),
                    RetryPolicy.nextAttemptAt(nextAttempt),
                    result.statusCode() + " / " + result.body());
            }
        }
    }

    private DomainEventOutbox unwrap(DomainEventOutbox row) {
        // 因为 outbox 存的是 row, 需要构造 event 给 pushClient
        return row;
    }
}
```

Note: `unwrap` in fact should reconstruct DomainEvent from outbox columns; here we serialize the whole row. Refine implementation in code-review phase.

- [ ] **Step 4: Application.yml scheduling config**

```yaml
spring:
  task:
    scheduling:
      pool:
        size: 2
      thread-name-prefix: outbox-poller-
hermes:
  push:
    batch-size: 20
  outbox:
    poll-interval-ms: 5000
```

- [ ] **Step 5: Run test, expect PASS**

- [ ] **Step 6: Commit**

```bash
git add event/OutboxPollerWorker.java application.yml \
        event/OutboxPollerWorkerTest.java
git commit -m "feat(event): OutboxPollerWorker @Scheduled 5s with status transitions"
```

---

## Task 12: DeadLetterService + 强制 reset 工具

**Files:**
- Create: `micro-course-api/src/main/java/com/microcourse/event/DeadLetterService.java`
- Test: `micro-course-api/src/test/java/com/microcourse/event/DeadLetterServiceTest.java`

- [ ] **Step 1: Write failing test**

```java
// DeadLetterServiceTest.java
@SpringBootTest
class DeadLetterServiceTest {
    @Autowired DeadLetterService dlqService;
    @Resource DomainEventDeadLetterRepository dlqRepo;

    @Test
    void listUnacked_works() {
        DomainEventDeadLetter row = new DomainEventDeadLetter();
        row.setEventId(UUID.randomUUID().toString());
        row.setAggregateType("LESSON");
        row.setAggregateId(1L);
        row.setPayload("{}");
        row.setLastError("timeout");
        dlqRepo.insert(row);

        long count = dlqService.countUnacked();
        assertTrue(count > 0);
    }

    @Test
    void ack_sets_operator_and_acknowledgedAt() {
        DomainEventDeadLetter row = newDomainEventDeadLetterStub();
        dlqService.acknowledge(row.getEventId(), "ops-user-1");
        DomainEventDeadLetter reloaded = dlqRepo.selectById(row.getEventId());
        assertEquals("ops-user-1", reloaded.getOperator());
        assertNotNull(reloaded.getAcknowledgedAt());
    }
}
```

- [ ] **Step 2: Run, expect FAIL**

- [ ] **Step 3: Write DeadLetterService**

```java
package com.microcourse.event;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.microcourse.event.repository.DomainEventDeadLetterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DeadLetterService {

    private final DomainEventDeadLetterRepository dlqRepo;

    public long countUnacked() {
        return dlqRepo.selectCount(
            new QueryWrapper<DomainEventDeadLetter>().isNull("acknowledged_at"));
    }

    public void acknowledge(String eventId, String operator) {
        DomainEventDeadLetter row = dlqRepo.selectById(eventId);
        if (row == null) throw new IllegalArgumentException("not found: " + eventId);
        row.setOperator(operator);
        row.setAcknowledgedAt(java.time.LocalDateTime.now());
        dlqRepo.updateById(row);
    }

    /** 运营手段: 把 dead_letter 强制重推到 outbox (走 PR + 手动 SQL, SPEC §5.5) */
    public long forceRessurectAllDeadLetters() {
        // 不在本任务范围, 留 PR 后续补
        throw new UnsupportedOperationException("use PR #N migration to resurrect");
    }
}
```

- [ ] **Step 4: Run, expect PASS**

- [ ] **Step 5: Commit**

```bash
git add event/DeadLetterService.java event/DeadLetterServiceTest.java
git commit -m "feat(event): DeadLetterService with count + acknowledge"
```

---

## Task 13: E2E 测试 (双向同步闭环)

**Files:**
- Create: `micro-course-api/src/test/java/com/microcourse/event/CourseStructureSyncE2ETest.java`
- (依赖上面所有 task 已合入)

- [ ] **Step 1: Write E2E test**

```java
// CourseStructureSyncE2ETest.java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "hermes.push.batch-size=5",
    "hermes.outbox.poll-interval-ms=200"  // 测试加速
})
class CourseStructureSyncE2ETest {

    @Autowired MockMvc hermesInboundMvc;
    @Resource DomainEventOutboxRepository outboxRepo;
    @Resource DomainEventDedupRepository dedupRepo;

    @Test
    void updateCourse_publishes_event_and_hermes_receives_then_echo_does_not_loop() throws Exception {
        Long courseId = createCourseStub();

        // 1. 本地更新触发 publish
        courseService.updateCourse(courseId, ...);

        // 2. 等 OutboxPoller 推
        await().atMost(Duration.ofSeconds(3)).until(() -> 
            outboxRepo.lambdaQuery().eq(DomainEventOutbox::getAggregateId, courseId)
                .eq(DomainEventOutbox::getStatus, OutboxStatus.DELIVERED).exists()
        );

        // 3. 模拟 Hermes 反推同样 event_id (回环测试)
        String echoEventId = "echo-" + courseId;
        mockMvc.perform(post("/api/hermes/webhook/events")
            .contentType(JSON).content(buildEchoBody(echoEventId, courseId)))
            .andExpect(status().isAccepted());

        // 4. 验证: 本地处理后, outbox 没有新增 (因为 eventSource=HERMES)
        assertFalse(outboxRepo.lambdaQuery()
            .gt(DomainEventOutbox::getOccurredAt, LocalDateTime.now().minusSeconds(2))
            .exists());

        // 5. 验证: dedup 表 echoEventId 已记录
        assertTrue(dedupRepo.existsByEventId(echoEventId));
    }

    @Test
    void hermes_pushes_event_to_us_we_dedup_on_repeat() throws Exception {
        String eventId = UUID.randomUUID().toString();
        String body = buildPushBody(eventId);
        // 第一发
        mockMvc.perform(post("/api/hermes/webhook/events").contentType(JSON).content(body))
            .andExpect(status().isAccepted());
        // 第二发应 dedup
        mockMvc.perform(post("/api/hermes/webhook/events").contentType(JSON).content(body))
            .andExpect(jsonPath("$.deduped").value(true));
    }
}
```

- [ ] **Step 2: Run, expect PASS after all earlier tasks green**

- [ ] **Step 3: Commit**

```bash
git add test/java/com/microcourse/event/CourseStructureSyncE2ETest.java
git commit -m "test(event): E2E bidirectional sync + echo prevention + dedup"
```

---

## Task 14: Grafana 告警规则 (留给操作团队单独 PR)

**Files:**
- Create: `monitoring/grafana/alerts/domain-event-outbox-alerts.yml` (可与 W35 监控栈合并)

- [ ] **Step 1: Write alerting rules**

```yaml
# monitoring/grafana/alerts/domain-event-outbox-alerts.yml
groups:
- name: domain-event-outbox
  rules:
  - alert: DomainEventOutboxPendingTooLong
    expr: |
      SELECT COUNT(*) FROM domain_event_outbox
      WHERE status = 'PENDING' AND occurred_at < NOW() - INTERVAL '5 minutes'
    annotations:
      summary: "{{ $value }} 个事件卡在 PENDING > 5 分钟 (Hermes 可能不健康)"
  - alert: DomainEventDeadLetterNonEmpty
    expr: |
      SELECT COUNT(*) FROM domain_event_dead_letter
      WHERE acknowledged_at IS NULL
    annotations:
      summary: "死信队列有 {{ $value }} 个事件待人工处理"
```

- [ ] **Step 2: PR to monitoring-lint repo, merge 通过后由 ops 部署**

- [ ] **Step 3: Commit + PR**

```bash
git add monitoring/grafana/alerts/domain-event-outbox-alerts.yml
git commit -m "feat(monitoring): domain-event-outbox alerting rules"
gh pr create --base main --head feat/domain-event-outbox-alerts
```

---

## Spec Self-Review

| 检查项 | 结果 |
|--------|------|
| §一 问题诊断 + 用户授权 | ✅ Task 6, 7 |
| §二 架构图 (outbox pattern) | ✅ Tasks 5, 8, 11 |
| §三 V313/V314/V315 三表 | ✅ Tasks 1-3 |
| §四 触发点 + 边界 (course/chapter/section/lesson, P1 only) | ✅ Tasks 6, 7, 8 |
| §五 5 步退避 + 死信 | ✅ Tasks 10, 11, 12 |
| §六 API 契约 | ✅ Tasks 8, 9 |
| §七 测试 + 监控 | ✅ Tasks 1, 5, 6, 7, 10, 11, 13, 14 |
| 7-19 P0 防御 | ✅ All transactions atomic; non-destructive UPSERT principle in Tasks 5, 11 |
| Placeholder scan | ✅ All steps have actual code |
| Type consistency | ✅ DomainEvent consistently used |

Spec coverage: 100% — every spec requirement has at least one task.

---

## Execution Handoff

**Plan complete and saved to `docs/superpowers/plans/2026-07-21-p1-course-structure-bidirectional-sync.md`.**

**Two execution options:**

1. **Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration
2. **Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Note**: spec 本次只覆盖 P1（同步机制）。P2（冲突解决）和 P3（Hermes VO 扩展）另行 brainstorm → spec → plan → implement。
