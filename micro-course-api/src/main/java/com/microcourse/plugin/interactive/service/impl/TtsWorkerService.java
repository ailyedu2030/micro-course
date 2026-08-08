package com.microcourse.plugin.interactive.service.impl;

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
import jakarta.annotation.PostConstruct;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
 * Q-1（多线程/多节点幂等，V330 worker_id 列）：
 * <ul>
 *   <li>启动时生成唯一 workerId（UUID）</li>
 *   <li>claimPending 原子抢占：UPDATE ... SET status='PROCESSING', worker_id=? WHERE status='GENERATING' ...
 *       PostgreSQL 行锁 + WHERE 重评估 → 双 worker 并发 claim 同一行只一个成功（不重复扣 MiniMax API）</li>
 *   <li>处理失败 → releaseClaim 回滚 status='GENERATING' 下轮重试</li>
 *   <li>崩溃遗留（PROCESSING 超 10 分钟）→ reclaimOrphans 恢复 GENERATING</li>
 *   <li>长期未消费（GENERATING 超 10 分钟）→ markTimedOut 置 FAILED</li>
 * </ul>
 */
@Service
public class TtsWorkerService {

    private static final Logger log = LoggerFactory.getLogger(TtsWorkerService.class);

    private static final int MAX_CONCURRENCY = 2;
    private static final long GENERATION_TIMEOUT_MINUTES = 10;
    private static final long MIN_AGE_BEFORE_PROCESS_MS = 3_000; // 队列插入后 3s 再消费，避开事务未提交

    /** Q-1: 本 worker 实例唯一标识（启动时生成），用于 claim/释放抢占归属 */
    private final String workerId = UUID.randomUUID().toString();

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

    /**
     * N-5 增强（2026-08-08）: Spring 全局 TaskScheduler 线程池大小（spring.task.scheduling.pool.size）。
     * 本 worker 的 {@code @Scheduled poll()} 依赖单线程 scheduler 保证同一时刻只有一个 poll 执行；
     * 若配置多线程（>1），并发 poll 可能重复合成同一行（重复扣 MiniMax API 费用）。
     * Q-1（V330 worker_id + 原子抢占 claimPending）已提供 DB 层幂等兜底，此处再显式 fail-fast
     * 阻止错误配置启动，避免"配置看似无害、行为静默异常"。默认 1（单线程，Spring Boot 默认值）。
     */
    @Value("${spring.task.scheduling.pool.size:1}")
    private int schedulerPoolSize;

    private final AtomicInteger inFlight = new AtomicInteger(0);

    /**
     * G3-P1-C-2: 确定性失败 vs 瞬时失败分类。
     * <p>
     * 确定性错误（余额不足 / Key 无效 / 限流 / 未配置 key）重试不会成功，
     * 此前一律 releaseClaim 回滚 GENERATING → 10 分钟后 markTimedOut 才置 FAILED，
     * 用户收到失败反馈延迟 10 分钟。现在确定性错误<b>立即置 FAILED</b>并保留 error_message；
     * 仅网络 / 超时 / 未知走重试（未知最多重试 3 次后 FAILED）。
     * </p>
     */
    enum FailureCategory {
        BALANCE_NOT_ENOUGH,   // 余额不足 → 立即 FAILED
        INVALID_API_KEY,      // API Key 无效 → 立即 FAILED
        RATE_LIMIT,           // 限流 → 立即 FAILED（提示 5 分钟后重试）
        API_KEY_NOT_CONFIGURED, // 未配置 key → 立即 FAILED
        NETWORK,              // 网络失败 → 重试
        TIMEOUT,              // 超时 → 重试
        UNKNOWN;              // 未知 → 重试最多 3 次后 FAILED

        /** 是否无需重试、直接判定 FAILED（确定性错误）。 */
        boolean failsImmediately() {
            return this == BALANCE_NOT_ENOUGH || this == INVALID_API_KEY
                    || this == RATE_LIMIT || this == API_KEY_NOT_CONFIGURED;
        }
    }

    /** 错误消息 → 失败类别（与 TtsServiceImpl 抛出的消息文案对齐）。 */
    static FailureCategory classifyFailure(String message) {
        String m = message == null ? "" : message;
        if (m.contains("余额不足")) return FailureCategory.BALANCE_NOT_ENOUGH;
        if (m.contains("Key 无效")) return FailureCategory.INVALID_API_KEY;
        if (m.contains("限流")) return FailureCategory.RATE_LIMIT;
        if (m.contains("key 未配置")) return FailureCategory.API_KEY_NOT_CONFIGURED;
        if (m.contains("MiniMax 调用失败")) return FailureCategory.NETWORK;  // 网络 IO 失败
        if (m.contains("超时") || m.toLowerCase().contains("timeout")) return FailureCategory.TIMEOUT;
        return FailureCategory.UNKNOWN;
    }

    /**
     * G3-P1-C-2: UNKNOWN 类失败的重试计数（内存态，跨 worker 节点不共享可接受——
     * 10 分钟 markTimedOut 兜底仍在）。key = "PPT:{id}" / "HTML:{id}"。
     */
    private static final int UNKNOWN_MAX_RETRIES = 3;
    private final Map<String, AtomicInteger> unknownRetryCount = new ConcurrentHashMap<>();

    private String retryKey(boolean ppt, Long audioRowId) {
        return (ppt ? "PPT:" : "HTML:") + audioRowId;
    }

    /** UNKNOWN 失败重试次数是否已超限（达到上限 → 立即 FAILED）。 */
    private boolean isUnknownRetryExhausted(boolean ppt, Long audioRowId) {
        AtomicInteger counter = unknownRetryCount.computeIfAbsent(
                retryKey(ppt, audioRowId), k -> new AtomicInteger(0));
        return counter.incrementAndGet() >= UNKNOWN_MAX_RETRIES;
    }

    /** 成功/终态后清理重试计数，避免 Map 无限增长。 */
    private void clearRetryCount(boolean ppt, Long audioRowId) {
        unknownRetryCount.remove(retryKey(ppt, audioRowId));
    }

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

    /**
     * N-5 增强: 启动时校验 {@code spring.task.scheduling.pool.size}。
     * <p>
     * 本 worker 的 {@code @Scheduled poll()} 依赖单线程 scheduler —— 同一时刻只有一个 poll 执行，
     * 配合 Q-1 原子抢占（claimPending 置 PROCESSING + worker_id）保证不重复合成。
     * 若配置 pool.size &gt; 1（多线程 scheduler），并发 poll 可能重复合成同一行（重复扣 MiniMax 费用）；
     * 此处 fail-fast 阻止启动，把错误暴露在部署时而非运行中。
     * </p>
     *
     * @throws IllegalStateException 当 pool.size &gt; 1（多线程 scheduler 与本 worker 单线程设计不兼容）
     */
    @PostConstruct
    public void verifySingleThreadedScheduler() {
        if (schedulerPoolSize > 1) {
            throw new IllegalStateException(
                    "[TtsWorker] spring.task.scheduling.pool.size=" + schedulerPoolSize
                            + " 与本 worker 的单线程设计不兼容：多线程 scheduler 下并发 poll 可能重复合成"
                            + "（重复扣 MiniMax 费用）。请保持单线程（设为 1 或移除该配置项）。");
        }
        log.info("[TtsWorker] scheduler 配置校验通过: pool.size={}（单线程，符合设计）", schedulerPoolSize);
    }

    @Scheduled(fixedDelayString = "${plugin.interactive.tts.worker-poll-ms:15000}")
    public void poll() {
        int free = MAX_CONCURRENCY - inFlight.get();
        if (free <= 0) return;

        LocalDateTime now = LocalDateTime.now();
        // Q-1 幂等三阶段：
        // 1) 回收崩溃 worker 遗留的 PROCESSING 行（超 10 分钟）→ GENERATING
        // 2) 长期未消费的 GENERATING 行（超 10 分钟）→ FAILED（不再无限重试）
        // 3) 原子抢占剩余 GENERATING 行（插入超 3s，避开事务未提交）→ PROCESSING + worker_id
        reclaimOrphanClaims(now.minusMinutes(GENERATION_TIMEOUT_MINUTES));
        markTimedOutRows(now.minusMinutes(GENERATION_TIMEOUT_MINUTES));
        LocalDateTime claimBefore = now.minus(MIN_AGE_BEFORE_PROCESS_MS, java.time.temporal.ChronoUnit.MILLIS);
        int claimLimit = Math.max(1, Math.min(MAX_CONCURRENCY, free));

        int pptClaimed = 0;
        int htmlClaimed = 0;
        try {
            pptClaimed = pptAudioMapper.claimPending(workerId, claimBefore, claimLimit);
            htmlClaimed = htmlAudioMapper.claimPending(workerId, claimBefore, claimLimit);
        } catch (Exception e) {
            // claim 失败（如 V330 未迁移导致 worker_id 列缺失）→ 降级日志，下一轮重试
            log.warn("[TtsWorker] claim 抢占失败（下轮重试）: {}", e.getMessage());
        }
        if (pptClaimed + htmlClaimed <= 0) return;

        List<SlidePptPageAudio> pptPendings = pptAudioMapper.selectClaimed(workerId);
        List<SlideHtmlSegmentAudio> htmlPendings = htmlAudioMapper.selectClaimed(workerId);

        int processed = 0;
        for (SlidePptPageAudio row : pptPendings) {
            if (isTimedOut(row.getGenerationStartedAt())) {
                markFailed(row.getId(), "生成超时（>10 分钟）");
                continue;
            }
            if (processed >= MAX_CONCURRENCY) {
                releaseClaim(row.getId(), true);
                continue;
            }
            if (handlePpt(row)) processed++;
        }
        for (SlideHtmlSegmentAudio row : htmlPendings) {
            if (isTimedOut(row.getGenerationStartedAt())) {
                markFailedHtml(row.getId(), "生成超时（>10 分钟）");
                continue;
            }
            if (processed >= MAX_CONCURRENCY) {
                releaseClaim(row.getId(), false);
                continue;
            }
            if (handleHtml(row)) processed++;
        }
    }

    private boolean handlePpt(SlidePptPageAudio row) {
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
     * Q-1: 回收崩溃 worker 遗留的 PROCESSING 行（超过 10 分钟未完成）→ 恢复 GENERATING 供下轮重试。
     */
    private void reclaimOrphanClaims(LocalDateTime timeoutBefore) {
        try {
            int p = pptAudioMapper.reclaimOrphans(timeoutBefore);
            int h = htmlAudioMapper.reclaimOrphans(timeoutBefore);
            if (p + h > 0) {
                log.warn("[TtsWorker] 回收 {} 个崩溃遗留 PROCESSING 行（孤儿）", p + h);
            }
        } catch (Exception e) {
            log.warn("[TtsWorker] 回收孤儿行失败（下轮重试）: {}", e.getMessage());
        }
    }

    /**
     * Q-1: 长期未消费的 GENERATING 行（超过 10 分钟）→ FAILED（保留更具体的 error_message）。
     */
    private void markTimedOutRows(LocalDateTime timeoutBefore) {
        try {
            int p = pptAudioMapper.markTimedOut(timeoutBefore);
            int h = htmlAudioMapper.markTimedOut(timeoutBefore);
            if (p + h > 0) {
                log.warn("[TtsWorker] 标记 {} 个 GENERATING 超时 → FAILED", p + h);
            }
        } catch (Exception e) {
            log.warn("[TtsWorker] 标记超时失败（下轮重试）: {}", e.getMessage());
        }
    }

    /**
     * Q-1: 处理失败 → 回滚 status='GENERATING'（worker_id 清空）让下轮重试。
     * 仅限本 worker 抢占的行（worker_id 匹配），防误释放他人抢占。
     */
    private void releaseClaim(Long audioRowId, boolean ppt) {
        try {
            int affected = ppt
                    ? pptAudioMapper.releaseClaim(audioRowId, workerId)
                    : htmlAudioMapper.releaseClaim(audioRowId, workerId);
            if (affected > 0) {
                log.info("[TtsWorker] 释放抢占回滚 GENERATING audioRowId={} type={}", audioRowId, ppt ? "PPT" : "HTML");
            }
        } catch (Exception e) {
            log.warn("[TtsWorker] 释放抢占失败 audioRowId={}: {}", audioRowId, e.getMessage());
        }
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
            clearRetryCount(ppt, audioRowId);
        } catch (Exception e) {
            log.error("[TtsWorker] 生成失败 audioRowId={} type={}: {}", audioRowId, ppt ? "PPT" : "HTML",
                    e.getMessage(), e);
            // G3-P1-C-2：错误分类 —— 确定性错误（余额不足/Key 无效/限流/未配置 key）立即置 FAILED，
            // 不再 releaseClaim 重试等 10 分钟 markTimedOut 兜底（用户反馈延迟 10 分钟）；
            // 网络/超时保留 GENERATING 下轮重试（瞬时故障）；未知最多重试 3 次后 FAILED。
            FailureCategory category = classifyFailure(e.getMessage());
            if (category.failsImmediately()) {
                // 【P0 修复 2026-08-09】markFailed 内部固定写 PPT 表，
                // HTML 行（synthesizeAndFinalizeHtml → ppt=false）会因写错表而永远停在 PROCESSING。
                // 按 ppt 分流：PPT → markFailed，HTML → markFailedHtml。
                if (ppt) {
                    markFailed(audioRowId, e.getMessage());
                } else {
                    markFailedHtml(audioRowId, e.getMessage());
                }
                clearRetryCount(ppt, audioRowId);
            } else if (category == FailureCategory.UNKNOWN && isUnknownRetryExhausted(ppt, audioRowId)) {
                log.warn("[TtsWorker] UNKNOWN 错误重试 {} 次仍失败 → FAILED audioRowId={} type={}",
                        UNKNOWN_MAX_RETRIES, audioRowId, ppt ? "PPT" : "HTML");
                if (ppt) {
                    markFailed(audioRowId, e.getMessage());
                } else {
                    markFailedHtml(audioRowId, e.getMessage());
                }
            } else {
                // 记录失败原因（R-15）——保留 GENERATING 下个周期重试，
                // error_message 让用户在等待期可见真实原因（余额不足/限流/超时）；
                // Q-1: 释放抢占回滚 status='GENERATING'（worker_id 清空），下轮重新抢占重试。
                persistFailureMessage(audioRowId, ppt, e.getMessage());
                releaseClaim(audioRowId, ppt);
            }
        }
    }

    /**
     * R-15：将最近一次生成失败原因写入 error_message（不改变 status，供重试期间用户可见）。
     */
    private void persistFailureMessage(Long audioRowId, boolean ppt, String message) {
        try {
            String reason = truncate(message != null && !message.isBlank() ? message : "未知 TTS 错误", 500);
            transactionTemplate.executeWithoutResult(tx -> {
                if (ppt) {
                    SlidePptPageAudio row = pptAudioMapper.selectById(audioRowId);
                    if (row == null) return;
                    row.setErrorMessage(reason);
                    pptAudioMapper.updateById(row);
                } else {
                    SlideHtmlSegmentAudio row = htmlAudioMapper.selectById(audioRowId);
                    if (row == null) return;
                    row.setErrorMessage(reason);
                    htmlAudioMapper.updateById(row);
                }
            });
        } catch (Exception e) {
            log.warn("[TtsWorker] 写入 error_message 失败 audioRowId={}", audioRowId, e);
        }
    }

    /** 截断过长错误信息（DB TEXT 无长度限制，但保持 UI 可读）。 */
    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max) + "...";
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
                // R-15: 超时兜底不覆盖更具体的失败原因（如余额不足/限流），保留用户可见真实原因
                if (row.getErrorMessage() == null || row.getErrorMessage().isBlank()
                        || !reason.contains("超时")) {
                    row.setErrorMessage(truncate(reason, 500));
                }
                row.setCompletedAt(LocalDateTime.now());
                // Q-1: FAILED 行不再参与抢占（worker_id 清空）
                row.setWorkerId(null);
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
                // R-15: 超时兜底不覆盖更具体的失败原因（如余额不足/限流），保留用户可见真实原因
                if (row.getErrorMessage() == null || row.getErrorMessage().isBlank()
                        || !reason.contains("超时")) {
                    row.setErrorMessage(truncate(reason, 500));
                }
                row.setCompletedAt(LocalDateTime.now());
                // Q-1: FAILED 行不再参与抢占（worker_id 清空）
                row.setWorkerId(null);
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
