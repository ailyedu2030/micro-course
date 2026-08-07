package com.microcourse.plugin.interactive.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.exception.BusinessException;
import com.microcourse.plugin.interactive.entity.SlideHtmlSegmentAudio;
import com.microcourse.plugin.interactive.entity.SlideHtmlSegmentScript;
import com.microcourse.plugin.interactive.entity.SlideHtmlUnit;
import com.microcourse.plugin.interactive.entity.SlidePptPage;
import com.microcourse.plugin.interactive.entity.SlidePptPageAudio;
import com.microcourse.plugin.interactive.entity.SlidePptPageScript;
import com.microcourse.plugin.interactive.mapper.SlideHtmlSegmentAudioMapper;
import com.microcourse.plugin.interactive.mapper.SlideHtmlSegmentScriptMapper;
import com.microcourse.plugin.interactive.mapper.SlideHtmlUnitMapper;
import com.microcourse.plugin.interactive.mapper.SlidePptPageAudioMapper;
import com.microcourse.plugin.interactive.mapper.SlidePptPageMapper;
import com.microcourse.plugin.interactive.mapper.SlidePptPageScriptMapper;
import com.microcourse.plugin.interactive.service.TtsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * v2 TTS 消费 worker（P0-3 / R-11）。
 * <p>
 * 修复 D3：slide_ppt_page_audios / slide_html_segment_audios 的 GENERATING 行
 * 此前只插不消费、永不 READY。本 worker 定时轮询两张表，调用 MiniMax（TtsService.synthesize）
 * 完成生成：写文件 → status=READY（audio_duration_ms / file_size_bytes / storage_path / completed_at）。
 * </p>
 * 约束（R2b N-1 / R-11）：
 * <ul>
 *   <li>并发 ≤ 2（AtomicInteger inFlight）</li>
 *   <li>同一行轮询重试；generation_started_at 超 10 分钟仍失败 → 标记 FAILED（超时）</li>
 *   <li>历史非法音色枚举（male-young 等）→ MiniMax 官方 voice_id 别名映射（R-6）</li>
 *   <li>幂等：仅消费 status=GENERATING 行，成功后置 READY 不再轮询</li>
 * </ul>
 */
@Service
public class TtsWorkerService {

    private static final Logger log = LoggerFactory.getLogger(TtsWorkerService.class);

    private static final int MAX_CONCURRENCY = 2;
    private static final long GENERATION_TIMEOUT_MINUTES = 10;
    private static final long MIN_AGE_BEFORE_PROCESS_MS = 3_000; // 队列插入后 3s 再消费，避开事务未提交

    private final SlidePptPageAudioMapper pptAudioMapper;
    private final SlidePptPageScriptMapper pptScriptMapper;
    private final SlidePptPageMapper pptPageMapper;
    private final SlideHtmlSegmentAudioMapper htmlAudioMapper;
    private final SlideHtmlSegmentScriptMapper htmlSegmentScriptMapper;
    private final SlideHtmlUnitMapper htmlUnitMapper;
    private final TtsService ttsService;
    private final TransactionTemplate transactionTemplate;

    @Value("${mc.audio.storage-root:${java.io.tmpdir}/microcourse-audio}")
    private String audioStorageRoot;

    @Value("${plugin.interactive.minimax.tts-model:speech-2.8-hd}")
    private String defaultModel;

    @Value("${plugin.interactive.minimax.tts-voice:female-shaonv}")
    private String defaultVoice;

    private final AtomicInteger inFlight = new AtomicInteger(0);

    /** 历史前端枚举 → MiniMax 官方 voice_id（R-6 别名映射） */
    private static final Map<String, String> VOICE_ALIASES = Map.of(
            "male-young", "male-qingnian",
            "male-mid", "male-dashu",
            "female-young", "female-shaonv",
            "female-mid", "female-yujie",
            "vivian", "female-shaonv");

    public TtsWorkerService(SlidePptPageAudioMapper pptAudioMapper,
                            SlidePptPageScriptMapper pptScriptMapper,
                            SlidePptPageMapper pptPageMapper,
                            SlideHtmlSegmentAudioMapper htmlAudioMapper,
                            SlideHtmlSegmentScriptMapper htmlSegmentScriptMapper,
                            SlideHtmlUnitMapper htmlUnitMapper,
                            TtsService ttsService,
                            TransactionTemplate transactionTemplate) {
        this.pptAudioMapper = pptAudioMapper;
        this.pptScriptMapper = pptScriptMapper;
        this.pptPageMapper = pptPageMapper;
        this.htmlAudioMapper = htmlAudioMapper;
        this.htmlSegmentScriptMapper = htmlSegmentScriptMapper;
        this.htmlUnitMapper = htmlUnitMapper;
        this.ttsService = ttsService;
        this.transactionTemplate = transactionTemplate;
    }

    @Scheduled(fixedDelayString = "${plugin.interactive.tts.worker-poll-ms:15000}")
    public void poll() {
        int free = MAX_CONCURRENCY - inFlight.get();
        if (free <= 0) return;

        List<SlidePptPageAudio> pptPendings = pptAudioMapper.selectList(
                new LambdaQueryWrapper<SlidePptPageAudio>()
                        .eq(SlidePptPageAudio::getStatus, "GENERATING")
                        .orderByAsc(SlidePptPageAudio::getId));
        List<SlideHtmlSegmentAudio> htmlPendings = htmlAudioMapper.selectList(
                new LambdaQueryWrapper<SlideHtmlSegmentAudio>()
                        .eq(SlideHtmlSegmentAudio::getStatus, "GENERATING")
                        .orderByAsc(SlideHtmlSegmentAudio::getId));

        int processed = 0;
        for (SlidePptPageAudio row : pptPendings) {
            if (isTimedOut(row.getGenerationStartedAt())) {
                markFailed(row.getId(), "生成超时（>10 分钟）");
                continue;
            }
            if (processed >= MAX_CONCURRENCY) break;
            if (handlePpt(row)) processed++;
        }
        for (SlideHtmlSegmentAudio row : htmlPendings) {
            if (isTimedOut(row.getGenerationStartedAt())) {
                markFailedHtml(row.getId(), "生成超时（>10 分钟）");
                continue;
            }
            if (processed >= MAX_CONCURRENCY) break;
            if (handleHtml(row)) processed++;
        }
    }

    private boolean handlePpt(SlidePptPageAudio row) {
        if (!claimAndTimeoutCheck(row.getGenerationStartedAt())) return false;
        inFlight.incrementAndGet();
        try {
            SlidePptPageScript script = pptScriptMapper.selectById(row.getScriptId());
            if (script == null || script.getScriptText() == null || script.getScriptText().isBlank()) {
                markFailed(row.getId(), "PPT script 不存在或为空");
                return true;
            }
            SlidePptPage page = pptPageMapper.selectById(row.getPptPageId());
            if (page == null) {
                markFailed(row.getId(), "PPT page 不存在");
                return true;
            }
            synthesizeAndFinalize(row.getId(), page.getCourseId(), script.getScriptText(),
                    row.getVoiceUsed(), row.getModelUsed(), true);
            return true;
        } finally {
            inFlight.decrementAndGet();
        }
    }

    private boolean handleHtml(SlideHtmlSegmentAudio row) {
        if (!claimAndTimeoutCheck(row.getGenerationStartedAt())) return false;
        inFlight.incrementAndGet();
        try {
            SlideHtmlSegmentScript script = htmlSegmentScriptMapper.selectById(row.getSegmentScriptId());
            if (script == null || script.getScriptText() == null || script.getScriptText().isBlank()) {
                markFailedHtml(row.getId(), "HTML segment script 不存在或为空");
                return true;
            }
            SlideHtmlUnit unit = htmlUnitMapper.selectById(row.getHtmlUnitId());
            if (unit == null) {
                markFailedHtml(row.getId(), "HTML unit 不存在");
                return true;
            }
            synthesizeAndFinalizeHtml(row.getId(), unit.getCourseId(), script.getScriptText(),
                    row.getVoiceUsed(), row.getModelUsed());
            return true;
        } finally {
            inFlight.decrementAndGet();
        }
    }

    /**
     * 3s 内刚插入的行跳过本周期（等待事务提交）；超 10 分钟未消费的孤儿行标记 FAILED。
     */
    private boolean claimAndTimeoutCheck(LocalDateTime startedAt) {
        if (startedAt == null) return false;
        LocalDateTime now = LocalDateTime.now();
        return java.time.Duration.between(startedAt, now).toMillis() >= MIN_AGE_BEFORE_PROCESS_MS;
    }

    private boolean isTimedOut(LocalDateTime startedAt) {
        return startedAt != null
                && startedAt.plusMinutes(GENERATION_TIMEOUT_MINUTES).isBefore(LocalDateTime.now());
    }

    private void synthesizeAndFinalize(Long audioRowId, Long courseId, String script,
                                       String voiceUsed, String modelUsed, boolean ppt) {
        try {
            String voice = resolveVoice(voiceUsed);
            String model = (modelUsed == null || modelUsed.isBlank()) ? defaultModel : modelUsed;
            TtsService.SynthesizedAudio audio = ttsService.synthesize(script, voice, model, 1.0);
            Path dir = Paths.get(audioStorageRoot, String.valueOf(courseId), "audio");
            Files.createDirectories(dir);
            Path file = dir.resolve(loadToken(audioRowId, ppt) + ".mp3");
            Files.write(file, audio.getBytes());
            transactionTemplate.executeWithoutResult(tx -> {
                if (ppt) {
                    SlidePptPageAudio row = pptAudioMapper.selectById(audioRowId);
                    if (row == null) return;
                    row.setStatus("READY");
                    row.setAudioDurationMs(audio.getEstimatedSec() * 1000);
                    row.setFileSizeBytes((long) audio.getBytes().length);
                    row.setStoragePath(file.toString());
                    row.setCompletedAt(LocalDateTime.now());
                    row.setAudioUrl("/api/courses/" + courseId + "/courseware/audio/" + row.getAudioToken());
                    pptAudioMapper.updateById(row);
                } else {
                    SlideHtmlSegmentAudio row = htmlAudioMapper.selectById(audioRowId);
                    if (row == null) return;
                    row.setStatus("READY");
                    row.setAudioDurationMs(audio.getEstimatedSec() * 1000);
                    row.setFileSizeBytes((long) audio.getBytes().length);
                    row.setStoragePath(file.toString());
                    row.setCompletedAt(LocalDateTime.now());
                    row.setAudioUrl("/api/courses/" + courseId + "/courseware/audio/" + row.getAudioToken());
                    htmlAudioMapper.updateById(row);
                }
            });
            log.info("[TtsWorker] READY audioRowId={} type={} bytes={} ~{}s", audioRowId, ppt ? "PPT" : "HTML",
                    audio.getBytes().length, audio.getEstimatedSec());
        } catch (Exception e) {
            log.error("[TtsWorker] 生成失败 audioRowId={} type={}: {}", audioRowId, ppt ? "PPT" : "HTML",
                    e.getMessage(), e);
            // 保留 GENERATING，下个周期重试；超时由清理路径标记 FAILED
        }
    }

    private void synthesizeAndFinalizeHtml(Long audioRowId, Long courseId, String script,
                                           String voiceUsed, String modelUsed) {
        synthesizeAndFinalize(audioRowId, courseId, script, voiceUsed, modelUsed, false);
    }

    private void markFailed(Long audioRowId, String reason) {
        try {
            transactionTemplate.executeWithoutResult(tx -> {
                SlidePptPageAudio row = pptAudioMapper.selectById(audioRowId);
                if (row == null) return;
                row.setStatus("FAILED");
                row.setCompletedAt(LocalDateTime.now());
                pptAudioMapper.updateById(row);
            });
            log.warn("[TtsWorker] PPT FAILED audioRowId={} reason={}", audioRowId, reason);
        } catch (Exception e) {
            log.error("[TtsWorker] 标记 PPT FAILED 失败 audioRowId={}", audioRowId, e);
        }
    }

    private void markFailedHtml(Long audioRowId, String reason) {
        try {
            transactionTemplate.executeWithoutResult(tx -> {
                SlideHtmlSegmentAudio row = htmlAudioMapper.selectById(audioRowId);
                if (row == null) return;
                row.setStatus("FAILED");
                row.setCompletedAt(LocalDateTime.now());
                htmlAudioMapper.updateById(row);
            });
            log.warn("[TtsWorker] HTML FAILED audioRowId={} reason={}", audioRowId, reason);
        } catch (Exception e) {
            log.error("[TtsWorker] 标记 HTML FAILED 失败 audioRowId={}", audioRowId, e);
        }
    }

    private String resolveVoice(String voiceUsed) {
        if (voiceUsed == null || voiceUsed.isBlank()) return defaultVoice;
        return VOICE_ALIASES.getOrDefault(voiceUsed, voiceUsed);
    }

    private String loadToken(Long audioRowId, boolean ppt) {
        if (ppt) {
            SlidePptPageAudio row = pptAudioMapper.selectById(audioRowId);
            return row != null && row.getAudioToken() != null ? row.getAudioToken() : ("v2-ppt-" + audioRowId);
        }
        SlideHtmlSegmentAudio row = htmlAudioMapper.selectById(audioRowId);
        return row != null && row.getAudioToken() != null ? row.getAudioToken() : ("v2-html-" + audioRowId);
    }
}
