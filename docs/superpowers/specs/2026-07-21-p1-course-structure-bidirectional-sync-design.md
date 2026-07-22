# P1 · 课程架构双向同步机制 · 设计 Spec

> **项目**: 微课管理平台 · Hermes 双向集成
> **设计者**: 项目总负责人 / 总工程师 (本 AI Agent)
> **状态**: Draft v1.0 · 待用户评审 → writing-plans
> **关联**: P2 冲突解决 + P3 Hermes VO 扩展 (本期不实现)

---

## 〇、设计原则 (作为总负责人的 hard rules)

1. **不破坏性**: 任何同步失败都不能破坏本地数据 (上一条 P0 事故的根源)
2. **用户体验至上**: 即使 hermes 长期不可用, 本地体验不能受影响
3. **不赶工期**: 复杂度高于工业界标准 (outbox + retry + dedup + dead letter)
4. **可重放**: 任何 event 必须能"重新派发"而不破坏状态
5. **可观测**: 一条 event 从 publish → consume 的全程有 trace_id
6. **可逆**: 任何环节手动可强制 reset

---

## 一、问题与目标

### 1.1 当前现状

#### Hermes 集成现状
- **入站 (Hermes → 本地)**: 已存在 `HermesWebhookController` + `HermesCourseSyncServiceImpl`
  - 行 412 `course.setPrice(pricing.getPrice());` ✅ 价格已同步
  - 行 510 `pricing.setPrice(course.getPrice());` ✅ DTO 已包含
- **出站 (本地 → Hermes)**: **完全没有**
  - 没有 `PUT /api/hermes/webhook/courses/{hermesCourseId}` endpoint
  - `CourseServiceImpl` 不注入 `HermesCourseSyncService`
  - 本地后台改 course / chapter / lesson 后, hermes 完全无感知

#### 架构对齐
| 层 | 本地 | Hermes |
|----|------|--------|
| Course 元 | ✅ | ✅ |
| Chapter | ✅ | ✅ |
| **Section** | ✅ | ❌ (无 section 层) |
| Lesson | ✅ | ✅ |
| **7 表课件 (V300-V308)** | ✅ 完整 | ❌ HermesCourseDetailVO 仅 lessonType/contentUrl |
| Flow / Script / Audio 历史 | ✅ | ❌ 仅 scriptContent 快照 |

### 1.2 用户授权
> "时间和成本不要考虑, 用户体验至上, 这是铁律"
> 双向同步架构: 本地改 → Hermes; Hermes 推 → 本地

### 1.3 目标 (本期 P1)
只解决"能不能同步", 不解决"对不上怎么办"——后者在 P2。
- 本地后台对 course / chapter / section / lesson 的 CRUD 后: Hermes 端最终一致
- Hermes 端推送过来: 本地端最终一致
- Hermes 长期不可用: 本地端体验不受影响
- 双向并发时: P1 不解决 (P2 处理), 此处默认 last-write-wins (by event timestamp)

---

## 二、整体架构

```
┌────────────────────────────────────────────────────────────────┐
│                         本地微课平台                              │
│                                                                │
│  ┌──────────────────┐         ┌───────────────────────┐        │
│  │ CourseServiceImpl│ publish │  domain_event_outbox  │ ←──┐  │
│  │ ChapterService   │ ──────► │  (同事务落库)           │     │  │
│  │ SectionService   │         │ event_id / aggregate  │     │  │
│  │ LessonService    │         │ event_type / payload  │     │  │
│  └──────────────────┘         │ status / retry_count  │     │  │
│         │                     └───────────────────────┘     │  │
│         │                                                    │  │
│         ▼                          ▲                          │  │
│  ┌──────────────────┐    ┌────────────────────────┐          │  │
│  │  MySQL / PG       │    │ OutboxPoller Worker   │          │  │
│  │  course / chapter │    │ 每 5s 扫 PENDING 行    │──────────┘  │
│  │  section / lesson │    │ 推到 Hermes webhook       │          │
│  │  hermes_event     │◄───│ 推成功 mark DELIVERED  │          │
│  │  _dedup / _dlq    │    │ 推失败 retry-backoff    │          │
│  └──────────────────┘    └────────────────────────┘          │  │
│                                                                │
└────────────────────┬─────────────────┬────────────────────────┘
                     │ 推               │ 推
                     ▼                 ▼
        ┌──────────────────────────────────────┐
        │  Hermes Webhook Receiver (已有)       │
        │  /api/hermes/webhook/courses          │
        │  /api/hermes/webhook/courses/.../events│
        └──────────┬───────────────────────────┘
                   │
                   ▼ 即处理 + 反推回本地
        ┌──────────────────────────────────────┐
        │  HermesCourseSyncService (已有)       │
        │  upsert + 一致 event_id 关联           │
        └──────────────────────────────────────┘
```

### 2.1 三层清晰
1. **领域事件层**: 在业务事务内 publish 事件, 同事务落 outbox (原子)
2. **传输层**: OutboxPoller 异步推送, Hermes 处理 + 反推 (异步, 不阻塞业务)
3. **应用层**: 双方都按 event_id 做幂等 dedup

---

## 三、数据模型

### 3.1 V313 · `domain_event_outbox` (本地)

```sql
CREATE TABLE domain_event_outbox (
    event_id VARCHAR(64) PRIMARY KEY,           -- UUID v4
    aggregate_type VARCHAR(32) NOT NULL,        -- COURSE / CHAPTER / SECTION / LESSON / CHAPTER_REORDER ...
    aggregate_id BIGINT NOT NULL,               -- 实体 id
    event_type VARCHAR(64) NOT NULL,            -- Created / Updated / Deleted / Reordered
    payload JSONB NOT NULL,                     -- 完整 DTO (Hermes API 期望的形态)
    trace_id VARCHAR(64) NOT NULL,              -- 追踪用
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',  -- PENDING / DELIVERED / DEAD_LETTER
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
```

### 3.2 V314 · `domain_event_dedup` (本地幂等, 接收端)

```sql
CREATE TABLE domain_event_dedup (
    event_id VARCHAR(64) PRIMARY KEY,           -- 与 outbox.event_id 或 hermes 端 event_id 共享
    source VARCHAR(16) NOT NULL,                -- LOCAL / HERMES
    processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    trace_id VARCHAR(64) NOT NULL
);
```

### 3.3 V315 · `domain_event_dead_letter` (本地死信)

```sql
CREATE TABLE domain_event_dead_letter (
    event_id VARCHAR(64) PRIMARY KEY,
    payload JSONB NOT NULL,
    aggregate_type VARCHAR(32) NOT NULL,
    aggregate_id BIGINT,
    last_error TEXT,
    failed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    operator VARCHAR(32),                        -- 人工 ack 时填写
    acknowledged_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE domain_event_dead_letter IS
    '本地 → Hermes 推送失败最终死信; 人工 ack 或 wait retry after 24h';
```

### 3.4 Event Schema (JSON payload)

```json
{
  "event_id": "uuid-v4",
  "trace_id": "uuid-v4",
  "occurred_at": "2026-07-21T10:00:00Z",
  "aggregate_type": "LESSON",
  "aggregate_id": 12345,
  "event_type": "UPDATED",
  "action": "HTTP_POST",
  "endpoint": "/api/hermes/webhook/courses/{hermesCourseId}/lessons",
  "hermes_course_id": "HER-2026-XYZ",
  "payload": {
    "id": 12345,
    "title": "...",
    "lessonType": "PPT",
    "contentUrl": "...",
    ...
  }
}
```

---

## 四、触发点与边界

### 4.1 本地发方向 (publish)

> **P1 边界**: 只覆盖 course / chapter / section / lesson 4 层元数据 + lesson 内容快照.
> **P3 范围** (本期不做): PPT 渲染图 / HTML 内容 / 讲述稿历史 / 音频版本 / flow 跳转 —— 这些是内部教师工作流, 不对外暴露给 Hermes.

| 服务 | 触发方法 | 事件 |
|------|---------|------|
| `CourseServiceImpl` | `createCourse` / `updateCourse` / `deleteCourse` | Created/Updated/Deleted |
| `ChapterServiceImpl` | `createChapter` / `updateChapter` / `deleteChapter` / `reorderChapters` | Created/Updated/Deleted/Reordered |
| `SectionServiceImpl` | `createSection` / `updateSection` / `deleteSection` | Created/Updated/Deleted |
| `LessonServiceImpl` | `createLesson` / `updateLesson` / `deleteLesson` | Created/Updated/Deleted |
| 课件内容级 (PPT/HTML 编辑) | **不入本期 P1** —— P3 再做 | - |

### 4.2 Hermes 收方向 (consume)

- **入站**: `HermesWebhookController` 已有 endpoints, 流程不变
- **本地不重复 publish outbox** (避免循环) — **明确边界**:
  - 标志: 当一个写入事件的来源是 Hermes 提供 (event_id 来自 Hermes 端, 或 dedup.source=HERMES), 本地处理后**不**再 publish 到 outbox
  - 实现: `HermesCourseSyncServiceImpl` 各 upsert 方法接收参数 `eventSource` (LOCAL/HERMES), 标记 `eventSource == HERMES` 时跳过 publish
  - 反向: `CourseServiceImpl` 等本地 service 默认 `eventSource=LOCAL`, 一律 publish
- **去重**: dedup 表按 event_id PK 一键拒绝, 幂等

### 4.3 双向并发冲突 (P1 默认策略)

- **本期默认**: last-write-wins, 以 `occurred_at` 时间戳为权威
- **P2 升级**: 字段级合并 / 锁定策略
- **audit**: 每次冲突落 `domain_event_conflict` 表 (本期预留 schema, P2 实现)

---

## 五、容错与错误处理

### 5.1 重试策略

| 尝试次数 | 退避 | 行为 |
|---------|------|------|
| 1 | 立即 | OutboxPoller 默认 5s 一轮 |
| 2 | 30s | next_attempt_at = now + 30s |
| 3 | 5min | next_attempt_at = now + 5min |
| 4 | 30min | next_attempt_at = now + 30min |
| 5 | 2h | 第 5 次仍失败 → 移到 dead_letter |
| (5+ 死信) | 24h | 自动重试一次, 第 6 次失败 → 永久死信 |

### 5.2 Herme s不可用处理

- OutboxPoller 持续 retry, 本地业务无感
- 监控: `domain_event_outbox WHERE status='PENDING' AND occurred_at < now() - 5min` → alert
- 死信: alertmanager WebHook on `domain_event_dead_letter.insert`

### 5.3 本地不可用处理

- Hermes 推送过来落到 502 → hermes 内部 retry (本期假设 hermes 自己做)

### 5.4 幂等保证

- event_id (UUID v4) 作为幂等键
- Hermes 端用 `event_id` dedup; 本地端用 `domain_event_dedup` PK 拒收
- 重发不会导致重复副作用

### 5.5 强制 reset (运营手段)

```sql
-- 一次性把所有 PENDING 重新推
UPDATE domain_event_outbox SET status='PENDING', attempt_count=0
  WHERE status IN ('DEAD_LETTER', 'PENDING');
```

- 走 PR + 手动 SQL (SPEC 7-19 P0 铁律: 不在生产直接 SQL, 必须 ask user)

---

## 六、API 契约

### 6.1 本地 → Hermes (新, P1 范围)

```
POST   /api/hermes/webhook/courses/{hermesCourseId}/events
       Content-Type: application/json
       { event_id, trace_id, occurred_at, aggregate_type, aggregate_id, event_type, action, endpoint, payload }

        ⇓ 接受: 202 Accepted + body { ack: true, deduped: bool }
        ⇓ 拒绝: 409 Conflict (重复 event_id) / 422 (数据校验失败) / 500 (服务异常)
```

> 注意: 这与 V174 的现有 webhook 共用一个 base path `/api/hermes/webhook/courses`, 在该路径下新增 `/events`. 不破坏既有 11 endpoint 的契约.

### 6.2 Hermes → 本地 (既有, P1 不动)

- `/api/hermes/webhook/courses` POST 课程创建
- `/api/hermes/webhook/courses/{hermesCourseId}/sections` POST 章节创建
- … 既有 11 endpoint 不变

---

## 七、测试与验证

### 7.1 单元测试
- `OutboxPollerWorkerTest` (10+ cases):
  - happy path
  - 404 / 500 重试 + 退避
  - 死信搬移
  - 强制 reset
- `DomainEventPublisherTest` (10+):
  - 各 Service 方法触发事件正确
  - 同事务原子
  - dedup 拒绝

### 7.2 集成测试
- `CourseStructureSyncE2ETest`: 本地 → Hermes, 含 HTTP mock
- `HermesInboundSyncE2ETest`: Hermes → 本地

### 7.3 性能预算
- 本地 publish 入 outbox: p99 < 50ms (同事务 INSERT)
- OutboxPoller 5s 一轮, 单行 push p99 < 200ms (hermes 应答)
- 100 events/min 流量无 backpressure

### 7.4 监控
- `mc:outbox:pending_count` alert on > 100 (status=PENDING 计数)
- `mc:outbox:dead_letter_count` alert on > 0
- `mc:hermes:round_trip_latency` p99 dashboard

### 7.5 7-19 P0 防御 4 项
- ✅ `OUTBOX_INSERT` 与业务事务同事务 (非 destructive UPSERT)
- ✅ commit message 含 rollback 步骤
- ✅ 所有 DB 操作 ask user
- ✅ AGENTS.md 铁律 + production-safety skill

---

## 八、Spec 自检

1. **Placeholder scan**: 全部具体化, 数字 / 表名 / endpoint 均明确
2. **Internal consistency**: 5 段描述与 HermesCourseDetailVO/EventSchema 一致
3. **Scope check**: 边界 P1/P2/P3 明确, 不含冲突解决/VO 扩展
4. **Ambiguity**: 关键术语 (event_id / aggregate_id / outbox / dedup / dead_letter) 已明确

---

## 九、决策与下一步

### 决策列表 (项目总负责人签发)
- ✅ 选题: 双向同步架构
- ✅ 拆分: 3 个独立 spec (P1 同步机制 / P2 冲突 / P3 VO 扩展)
- ✅ P1 同步机制选型: 领域事件 + Outbox pattern
- ✅ P1 边界: course / chapter / section / lesson (课件内 7 表留 P3)
- ✅ 重试策略: 5 步指数退避 + 死信
- ✅ 幂等键: UUID v4 event_id

### 后续 (本文档 output 之后)
1. writing-plans skill → 出 P1 实施计划
2. P2 spec (冲突解决) 启动
3. P3 spec (VO 扩展) 启动
4. 实施 PR (本期 P1 spec 闭环)

### 当前 spec 范围覆盖
| spec/章节 | 状态 |
|-----------|------|
| §一 问题与目标 | ✅ |
| §二 整体架构 | ✅ |
| §三 数据模型 (V313/V314/V315) | ✅ |
| §四 触发点 + 边界 (P1 only) | ✅ |
| §五 容错 + 重试 + 死信 + 幂等 | ✅ |
| §六 API 契约 | ✅ |
| §七 测试 + 性能 + 监控 | ✅ |
| §八 自检 | ✅ |

---

**Spec 自检完成 (v1.0)**.

- [ ] 用户 review 书面 spec
- [ ] 调起 writing-plans skill 出实施计划
- [ ] P2/P3 spec 后续启动 (不在本次 PR 范围)
