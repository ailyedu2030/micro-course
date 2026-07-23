package com.microcourse.plugin.interactive.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microcourse.dto.R;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.plugin.interactive.dto.BatchTtsRequest;
import com.microcourse.plugin.interactive.dto.BatchTtsResponse;
import com.microcourse.plugin.interactive.dto.TtsStatusResponse;
import com.microcourse.plugin.interactive.service.TtsService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

@RestController
@RequestMapping("/api/admin/tts")
@ConditionalOnProperty(value = "plugin.interactive.enabled", havingValue = "true", matchIfMissing = true)
public class AdminTtsController {

    private static final Logger log = LoggerFactory.getLogger(AdminTtsController.class);
    private static final long TASK_STATE_TTL_MILLIS = 30 * 60 * 1000L;

    private final TtsService ttsService;
    private final ExecutorService slideRenderExecutor;
    private final ObjectMapper objectMapper;

    @Value("${plugin.interactive.slides.storage-path:/data/slides}")
    private String storagePath;

    public AdminTtsController(TtsService ttsService, ExecutorService slideRenderExecutor, ObjectMapper objectMapper) {
        this.ttsService = ttsService;
        this.slideRenderExecutor = slideRenderExecutor;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/batch")
    @PreAuthorize("hasRole('ADMIN')")
    public R<BatchTtsResponse> batchGenerate(@Valid @RequestBody BatchTtsRequest request) {
        String batchTaskId = "batch-" + System.currentTimeMillis();
        Long courseId = request.getCourseId();

        List<Long> sections = request.getSections();
        int estimatedSec = Math.max(30, sections.size() * 60);

        List<BatchTtsResponse.BatchTtsSectionTask> sectionTasks = new ArrayList<>();
        for (Long sectionId : sections) {
            String taskId = "tts-" + System.currentTimeMillis() + "-" + sectionId;
            sectionTasks.add(new BatchTtsResponse.BatchTtsSectionTask(sectionId, taskId, "queued"));
        }

        BatchTaskState state = new BatchTaskState(batchTaskId, courseId, sections.size(), estimatedSec, sectionTasks);
        persistNewBatchState(state);

        doBatchAsync(batchTaskId, courseId, sections,
                request.getVoice(), request.getModel(), request.getSpeed(), request.getSplitByPage());

        BatchTtsResponse response = BatchTtsResponse.of(batchTaskId, sections.size(), estimatedSec, sectionTasks);
        return R.ok(response);
    }

    @GetMapping("/batch/{batchTaskId}")
    @PreAuthorize("hasRole('ADMIN')")
    public R<BatchTtsResponse> getBatchStatus(@PathVariable String batchTaskId) {
        BatchTaskState state = readBatchState(batchTaskId);
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

                    updateBatchSection(batchTaskId, sectionId, result.getTaskId(), result.getStatus());
                } catch (Exception e) {
                    log.warn("[BatchTTS] section {} failed: {}", sectionId, e.getMessage());
                    updateBatchSection(batchTaskId, sectionId, null, "failed");
                }
            }

            markBatchCompleted(batchTaskId);
        });
    }

    private synchronized void updateBatchSection(String batchTaskId, Long sectionId, String taskId, String status) {
        BatchTaskState state = readBatchState(batchTaskId);
        if (state == null) return;
        state.updateSection(sectionId, taskId != null ? taskId : "", status);
        writeBatchState(state);
    }

    private synchronized void markBatchCompleted(String batchTaskId) {
        BatchTaskState state = readBatchState(batchTaskId);
        if (state == null) return;
        state.setCompletedAt(System.currentTimeMillis());
        writeBatchState(state);
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 300000)
    public void cleanupExpiredBatchTasks() {
        long expireThreshold = System.currentTimeMillis() - TASK_STATE_TTL_MILLIS;
        Path batchTaskDir = Paths.get(storagePath, "batch-tasks");
        if (!Files.isDirectory(batchTaskDir)) {
            return;
        }
        try (DirectoryStream<Path> taskFiles = Files.newDirectoryStream(batchTaskDir, "*.json")) {
            for (Path taskFile : taskFiles) {
                BatchTaskState state = objectMapper.readValue(taskFile.toFile(), BatchTaskState.class);
                if (state.getCompletedAt() > 0 && state.getCompletedAt() < expireThreshold) {
                    Files.deleteIfExists(taskFile);
                    log.debug("[BatchTTS] cleanup expired batch task: {}", taskFile.getFileName());
                }
            }
        } catch (NoSuchFileException e) {
            log.debug("[BatchTTS] batch task directory not found: {}", e.getMessage());
        } catch (Exception e) {
            log.error("[BatchTTS] cleanup expired batch tasks failed", e);
        }
    }

    private void persistNewBatchState(BatchTaskState state) {
        try {
            writeBatchState(state);
        } catch (Exception e) {
            log.error("[BatchTTS] 初始化批次任务状态失败 batchTaskId={}", state.getBatchTaskId(), e);
            throw new BusinessException(ErrorCode.TTS_GENERATE_FAILED, "批次任务状态初始化失败");
        }
    }

    private synchronized BatchTaskState readBatchState(String batchTaskId) {
        try {
            Path taskPath = getBatchTaskPath(batchTaskId);
            if (!Files.exists(taskPath)) {
                return null;
            }
            return objectMapper.readValue(taskPath.toFile(), BatchTaskState.class);
        } catch (NoSuchFileException e) {
            return null;
        } catch (Exception e) {
            log.error("[BatchTTS] 读取批次任务状态失败 batchTaskId={}", batchTaskId, e);
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "批次任务状态读取失败");
        }
    }

    private synchronized void writeBatchState(BatchTaskState state) {
        try {
            Path taskDir = getBatchTaskDirectory();
            Files.createDirectories(taskDir);
            Path taskPath = getBatchTaskPath(state.getBatchTaskId());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(taskPath.toFile(), state);
        } catch (IOException e) {
            throw new IllegalStateException("写入批次任务状态失败 batchTaskId=" + state.getBatchTaskId(), e);
        }
    }

    private Path getBatchTaskDirectory() {
        return Paths.get(storagePath, "batch-tasks");
    }

    private Path getBatchTaskPath(String batchTaskId) {
        String safeTaskId = normalizeTaskId(batchTaskId);
        return getBatchTaskDirectory().resolve(safeTaskId + ".json");
    }

    private String normalizeTaskId(String taskId) {
        if (taskId == null || !taskId.matches("[A-Za-z0-9\\-]+")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "非法任务标识");
        }
        return taskId;
    }

    private static class BatchTaskState {
        private String batchTaskId;
        private Long courseId;
        private int totalSections;
        private int estimatedSeconds;
        private long completedAt = 0;
        private List<BatchTtsResponse.BatchTtsSectionTask> sections = new ArrayList<>();

        BatchTaskState() {}

        BatchTaskState(String batchTaskId, Long courseId, int totalSections, int estimatedSeconds,
                       List<BatchTtsResponse.BatchTtsSectionTask> sections) {
            this.batchTaskId = batchTaskId;
            this.courseId = courseId;
            this.totalSections = totalSections;
            this.estimatedSeconds = estimatedSeconds;
            this.sections = sections;
        }

        synchronized void updateSection(Long sectionId, String taskId, String status) {
            for (BatchTtsResponse.BatchTtsSectionTask sec : sections) {
                if (sec.getSectionId().equals(sectionId)) {
                    if (taskId != null && !taskId.isEmpty()) {
                        sec.setTaskId(taskId);
                    }
                    sec.setStatus(status);
                    break;
                }
            }
        }

        BatchTtsResponse toResponse() {
            return BatchTtsResponse.of(batchTaskId, totalSections, estimatedSeconds, sections);
        }

        public String getBatchTaskId() { return batchTaskId; }
        public void setBatchTaskId(String batchTaskId) { this.batchTaskId = batchTaskId; }
        public Long getCourseId() { return courseId; }
        public void setCourseId(Long courseId) { this.courseId = courseId; }
        public int getTotalSections() { return totalSections; }
        public void setTotalSections(int totalSections) { this.totalSections = totalSections; }
        public int getEstimatedSeconds() { return estimatedSeconds; }
        public void setEstimatedSeconds(int estimatedSeconds) { this.estimatedSeconds = estimatedSeconds; }
        public long getCompletedAt() { return completedAt; }
        public void setCompletedAt(long completedAt) { this.completedAt = completedAt; }
        public List<BatchTtsResponse.BatchTtsSectionTask> getSections() { return sections; }
        public void setSections(List<BatchTtsResponse.BatchTtsSectionTask> sections) { this.sections = sections; }
    }
}
