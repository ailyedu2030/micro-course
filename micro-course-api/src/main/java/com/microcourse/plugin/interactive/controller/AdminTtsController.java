package com.microcourse.plugin.interactive.controller;

import com.microcourse.dto.R;
import com.microcourse.plugin.interactive.dto.BatchTtsRequest;
import com.microcourse.plugin.interactive.dto.BatchTtsResponse;
import com.microcourse.plugin.interactive.dto.TtsStatusResponse;
import com.microcourse.plugin.interactive.service.TtsService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;

@RestController
@RequestMapping("/api/admin/tts")
@ConditionalOnProperty(value = "plugin.interactive.enabled", havingValue = "true", matchIfMissing = true)
public class AdminTtsController {

    private static final Logger log = LoggerFactory.getLogger(AdminTtsController.class);
    private final TtsService ttsService;
    private final ExecutorService slideRenderExecutor;

    private final ConcurrentMap<String, BatchTaskState> batchTasks = new ConcurrentHashMap<>();

    public AdminTtsController(TtsService ttsService, ExecutorService slideRenderExecutor) {
        this.ttsService = ttsService;
        this.slideRenderExecutor = slideRenderExecutor;
    }

    @PostMapping("/batch")
    @PreAuthorize("hasRole('ADMIN')")
    public R<BatchTtsResponse> batchGenerate(@Valid @RequestBody BatchTtsRequest request) {
        String batchTaskId = "batch-" + System.currentTimeMillis();

        List<Long> sections = request.getSections();
        int estimatedSec = Math.max(30, sections.size() * 60);

        List<BatchTtsResponse.BatchTtsSectionTask> sectionTasks = new java.util.ArrayList<>();
        for (Long sectionId : sections) {
            String taskId = "tts-" + System.currentTimeMillis() + "-" + sectionId;
            sectionTasks.add(new BatchTtsResponse.BatchTtsSectionTask(sectionId, taskId, "queued"));
        }

        batchTasks.put(batchTaskId, new BatchTaskState(batchTaskId, sections.size(), estimatedSec, sectionTasks));

        doBatchAsync(batchTaskId, request.getCourseId(), sections,
                request.getVoice(), request.getModel(), request.getSpeed(), request.getSplitByPage());

        BatchTtsResponse response = BatchTtsResponse.of(batchTaskId, sections.size(), estimatedSec, sectionTasks);
        return R.ok(response);
    }

    @GetMapping("/batch/{batchTaskId}")
    @PreAuthorize("hasRole('ADMIN')")
    public R<BatchTtsResponse> getBatchStatus(@PathVariable String batchTaskId) {
        BatchTaskState state = batchTasks.get(batchTaskId);
        if (state == null) {
            return R.fail(404, "批次任务不存在或已过期");
        }
        return R.ok(state.toResponse());
    }

    private void doBatchAsync(String batchTaskId, Long courseId, List<Long> sectionIds,
                            String voice, String model, Double speed, boolean splitByPage) {
        slideRenderExecutor.submit(() -> {
            for (Long sectionId : sectionIds) {
                try {
                    TtsStatusResponse result = ttsService.generateSection(
                            courseId, sectionId, voice, model, speed, splitByPage).join();

                    BatchTaskState state = batchTasks.get(batchTaskId);
                    if (state != null) {
                        state.updateSection(sectionId, result.getTaskId(), result.getStatus());
                    }
                } catch (Exception e) {
                    log.warn("[BatchTTS] section {} failed: {}", sectionId, e.getMessage());
                    BatchTaskState state = batchTasks.get(batchTaskId);
                    if (state != null) {
                        state.updateSection(sectionId, null, "failed");
                    }
                }
            }

            BatchTaskState state = batchTasks.get(batchTaskId);
            if (state != null) {
                state.markCompleted();
            }
        });
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 300000)
    public void cleanupExpiredBatchTasks() {
        long expireThreshold = System.currentTimeMillis() - 30 * 60 * 1000L;
        batchTasks.entrySet().removeIf(entry -> {
            if (entry.getValue().completedAt > 0 && entry.getValue().completedAt < expireThreshold) {
                return true;
            }
            return false;
        });
    }

    private static class BatchTaskState {
        final String batchTaskId;
        final int totalSections;
        final int estimatedSeconds;
        volatile String status = "running";
        volatile long completedAt = 0;
        final List<BatchTtsResponse.BatchTtsSectionTask> sections;

        BatchTaskState(String batchTaskId, int totalSections, int estimatedSeconds,
                       List<BatchTtsResponse.BatchTtsSectionTask> sections) {
            this.batchTaskId = batchTaskId;
            this.totalSections = totalSections;
            this.estimatedSeconds = estimatedSeconds;
            this.sections = sections;
        }

        synchronized void updateSection(Long sectionId, String taskId, String status) {
            for (BatchTtsResponse.BatchTtsSectionTask sec : sections) {
                if (sec.getSectionId().equals(sectionId)) {
                    sec.setTaskId(taskId != null ? taskId : sec.getTaskId());
                    sec.setStatus(status);
                    break;
                }
            }
        }

        synchronized void markCompleted() {
            this.status = "completed";
            this.completedAt = System.currentTimeMillis();
        }

        BatchTtsResponse toResponse() {
            return BatchTtsResponse.of(batchTaskId, totalSections, estimatedSeconds, sections);
        }
    }
}
