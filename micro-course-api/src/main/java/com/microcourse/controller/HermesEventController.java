package com.microcourse.controller;

import com.microcourse.event.DomainEvent;
import com.microcourse.event.DomainEventDedup;
import com.microcourse.event.repository.DomainEventDedupRepository;
import com.microcourse.event.dto.CourseEventPayload;
import com.microcourse.service.HermesCourseSyncService;
import com.microcourse.dto.hermes.HermesWebhookRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * P1 plan Task 9: Hermes 通用事件入口.
 * 接收 Hermes 反推过来的 DomainEvent (aggregateType = COURSE/CHAPTER/SECTION/LESSON),
 * 通过 V314 dedup 表保证幂等 (event_id 共享 PK + ON CONFLICT DO NOTHING),
 * 分流到对应 service 处理.
 *
 * 设计:
 *   - endpoint: POST /api/hermes/webhook/events
 *   - body: DomainEvent (P1 spec §三.3.4)
 *   - 返 202 Accepted (eventId 已入 dedup 视为接住)
 *   - 返 200 OK (重复 eventId, 视为 deduped)
 *   - 返 501 (aggregateType 未实现, 留后续)
 *   - 返 400 (payload 解析失败 / 未知 aggregateType)
 *   - 返 500 (业务处理异常)
 *
 * 现有 P1 仅 aggregateType=COURSE 路径已实现, 其余 3 类留 P3 课件架构延伸.
 *
 * 手写构造器 + LOG (项目禁用 Lombok 注解处理器).
 */
@RestController
@RequestMapping("/api/hermes/webhook/events")
public class HermesEventController {

    private static final Logger LOG = LoggerFactory.getLogger(HermesEventController.class);

    private final DomainEventDedupRepository dedupRepo;
    private final HermesCourseSyncService courseSyncService;

    public HermesEventController(DomainEventDedupRepository dedupRepo,
                                  HermesCourseSyncService courseSyncService) {
        this.dedupRepo = dedupRepo;
        this.courseSyncService = courseSyncService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> receive(@RequestBody DomainEvent event) {
        LOG.info("[Hermes-inbound] eventId={}, aggregateType={}, aggregateId={}, eventType={}",
                event.getEventId(), event.getAggregateType(), event.getAggregateId(), event.getEventType());

        // 1. V314 dedup 幂等: 同一 event_id 二次到达 silent skip
        if (dedupRepo.existsByEventId(event.getEventId())) {
            LOG.info("[Hermes-inbound] dedup hit, ack with 200, eventId={}", event.getEventId());
            return ResponseEntity.ok(Map.of(
                    "ack", true,
                    "deduped", true,
                    "eventId", event.getEventId()
            ));
        }

        // 2. insert dedup 行 (ON CONFLICT DO NOTHING 保证幂等)
        DomainEventDedup dedupRow = new DomainEventDedup();
        dedupRow.setEventId(event.getEventId());
        dedupRow.setSource("HERMES");
        dedupRow.setTraceId(event.getTraceId());
        dedupRepo.insertIgnoreDuplicate(dedupRow);

        // 3. 按 aggregateType 分流
        if ("COURSE".equals(event.getAggregateType())) {
            try {
                CourseEventPayload payload = event.getPayloadAs(CourseEventPayload.class);
                if (payload == null) {
                    LOG.warn("[Hermes-inbound] COURSE event payload 解析失败, eventId={}", event.getEventId());
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(Map.of("ack", false, "error", "payload_parse_failed"));
                }
                // 构造 HermesWebhookRequest, 复用 HermesCourseSyncService.upsertCourse
                HermesWebhookRequest req = new HermesWebhookRequest();
                req.setHermesCourseId(event.getHermesCourseId() != null ? event.getHermesCourseId() : "");
                // HermesWebhookRequest 仅含基础课程信息 (title/subtitle/summary/coverUrl/categoryId/...)
                // price/status/updatedAt 等增量字段在 CourseEventPayload 中保留, 留后续 P3 增量同步 spec
                req.setTitle(payload.getTitle());
                req.setSubtitle(payload.getSubtitle());
                req.setSummary(payload.getSummary());

                // eventSource=HERMES, 防回环 (依 P1 plan Task 8 ECHO_GUARD)
                courseSyncService.upsertCourseFromHermes(req, event.getEventId());
                LOG.info("[Hermes-inbound] COURSE upserted, eventId={}", event.getEventId());
                return ResponseEntity.accepted().body(Map.of(
                        "ack", true,
                        "deduped", false,
                        "eventId", event.getEventId()
                ));
            } catch (Exception e) {
                LOG.error("[Hermes-inbound] COURSE 处理失败 eventId={}", event.getEventId(), e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("ack", false, "error", e.getMessage()));
            }
        } else if ("CHAPTER".equals(event.getAggregateType())
                || "SECTION".equals(event.getAggregateType())
                || "LESSON".equals(event.getAggregateType())) {
            // P3 课件架构延伸 (W36 课件编辑独立 reactive 流程)
            LOG.info("[Hermes-inbound] {} 业务暂未实现, 仅记录 dedup, eventId={}",
                    event.getAggregateType(), event.getEventId());
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                    .body(Map.of(
                            "ack", true,
                            "deduped", false,
                            "eventId", event.getEventId(),
                            "note", "P3 课件架构延伸"
                    ));
        } else {
            LOG.warn("[Hermes-inbound] 未知 aggregateType={}, eventId={}",
                    event.getAggregateType(), event.getEventId());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "ack", false,
                            "error", "unknown_aggregate_type",
                            "aggregateType", String.valueOf(event.getAggregateType())
                    ));
        }
    }
}
