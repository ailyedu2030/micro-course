package com.microcourse.plugin.interactive;

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
import com.microcourse.plugin.interactive.service.impl.TtsWorkerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TtsWorkerService (Q-1 幂等抢占) 单元测试.
 *
 * 覆盖:
 * <ul>
 *   <li>poll: 原子抢占后仅消费本 worker 抢占的行（claimPending → selectClaimed）</li>
 *   <li>成功路径: synthesize 成功 → status=READY + storage_path + audio_url</li>
 *   <li>失败回滚: synthesize 抛异常 → releaseClaim（回滚 GENERATING 下轮重试）+ error_message 保留</li>
 *   <li>抢占失败: claimPending 返回 0 → 本周期不消费（无重复处理）</li>
 *   <li>孤儿回收: reclaimOrphans + markTimedOut 在每轮 poll 调用</li>
 * </ul>
 */
class TtsWorkerServiceTest {

    private SlidePptPageAudioMapper pptAudioMapper;
    private SlidePptPageScriptMapper pptScriptMapper;
    private SlidePptPageMapper pptPageMapper;
    private SlideHtmlSegmentAudioMapper htmlAudioMapper;
    private SlideHtmlSegmentScriptMapper htmlSegmentScriptMapper;
    private SlideHtmlUnitMapper htmlUnitMapper;
    private TtsService ttsService;
    private TransactionTemplate transactionTemplate;
    private TtsWorkerService worker;
    private Path audioRoot;

    @BeforeEach
    void setUp() throws Exception {
        pptAudioMapper = mock(SlidePptPageAudioMapper.class);
        pptScriptMapper = mock(SlidePptPageScriptMapper.class);
        pptPageMapper = mock(SlidePptPageMapper.class);
        htmlAudioMapper = mock(SlideHtmlSegmentAudioMapper.class);
        htmlSegmentScriptMapper = mock(SlideHtmlSegmentScriptMapper.class);
        htmlUnitMapper = mock(SlideHtmlUnitMapper.class);
        ttsService = mock(TtsService.class);
        // 真实 TransactionTemplate + mock PlatformTransactionManager：
        // executeWithoutResult 回调真实执行（验证事务内状态更新），无需触碰 protected 方法
        PlatformTransactionManager ptm = mock(PlatformTransactionManager.class);
        when(ptm.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        transactionTemplate = new TransactionTemplate(ptm);

        worker = new TtsWorkerService(pptAudioMapper, pptScriptMapper, pptPageMapper,
                htmlAudioMapper, htmlSegmentScriptMapper, htmlUnitMapper, ttsService, transactionTemplate);
        audioRoot = Files.createTempDirectory("tts-worker-test");
        ReflectionTestUtils.setField(worker, "audioStorageRoot", audioRoot.toString());
    }

    private SlidePptPageAudio newPptAudioRow(Long id, LocalDateTime startedAt) {
        SlidePptPageAudio row = new SlidePptPageAudio();
        row.setId(id);
        row.setScriptId(100L);
        row.setPptPageId(10L);
        row.setStatus("GENERATING");
        row.setGenerationStartedAt(startedAt);
        row.setVoiceUsed("female-shaonv");
        row.setModelUsed("speech-2.8-hd");
        row.setAudioToken(UUID.randomUUID().toString().replace("-", ""));
        return row;
    }

    @Test
    @DisplayName("Q-1: poll 原子抢占后仅消费本 worker 抢占的行 → READY")
    void poll_ClaimAndSynthesizeSuccess() {
        SlidePptPageAudio row = newPptAudioRow(1L, LocalDateTime.now().minusMinutes(1));
        // claim 抢占 1 行
        when(pptAudioMapper.claimPending(anyString(), any(), anyInt())).thenReturn(1);
        when(htmlAudioMapper.claimPending(anyString(), any(), anyInt())).thenReturn(0);
        // 只取回本 worker 抢占的行
        when(pptAudioMapper.selectClaimed(anyString())).thenReturn(List.of(row));
        when(htmlAudioMapper.selectClaimed(anyString())).thenReturn(List.of());
        // script / page 依赖
        SlidePptPageScript script = new SlidePptPageScript();
        script.setId(100L); script.setScriptText("讲述稿内容");
        when(pptScriptMapper.selectById(100L)).thenReturn(script);
        SlidePptPage page = new SlidePptPage();
        page.setId(10L); page.setCourseId(42L);
        when(pptPageMapper.selectById(10L)).thenReturn(page);
        // audio 行 selectById（loadToken + 事务回调内重新读取）
        when(pptAudioMapper.selectById(1L)).thenReturn(row);
        // TTS 成功
        when(ttsService.synthesize(eq("讲述稿内容"), any(), any(), anyDouble()))
                .thenReturn(new TtsService.SynthesizedAudio(new byte[]{1, 2, 3}, 5));

        worker.poll();

        // 事务内 updateById 置 READY
        verify(pptAudioMapper, atLeastOnce()).updateById(argThat(r ->
                "READY".equals(r.getStatus())
                        && Integer.valueOf(5000).equals(r.getAudioDurationMs())
                        && r.getStoragePath() != null));
        // 不重复：仅 claim + 消费本 worker 行
        verify(pptAudioMapper, never()).releaseClaim(anyLong(), anyString());
        assertTrue(Files.exists(Path.of(audioRoot.toString(), "42", "audio")), "音频目录已创建");
    }

    @Test
    @DisplayName("Q-1: synthesize 失败 → releaseClaim 回滚 GENERATING + error_message 保留（下轮重试）")
    void poll_SynthesizeFailureReleasesClaim() {
        SlidePptPageAudio row = newPptAudioRow(2L, LocalDateTime.now().minusMinutes(1));
        when(pptAudioMapper.claimPending(anyString(), any(), anyInt())).thenReturn(1);
        when(htmlAudioMapper.claimPending(anyString(), any(), anyInt())).thenReturn(0);
        when(pptAudioMapper.selectClaimed(anyString())).thenReturn(List.of(row));
        when(htmlAudioMapper.selectClaimed(anyString())).thenReturn(List.of());
        SlidePptPageScript script = new SlidePptPageScript();
        script.setId(100L); script.setScriptText("讲述稿");
        when(pptScriptMapper.selectById(100L)).thenReturn(script);
        SlidePptPage page = new SlidePptPage();
        page.setId(10L); page.setCourseId(42L);
        when(pptPageMapper.selectById(10L)).thenReturn(page);
        // audio 行 selectById（loadToken + persistFailureMessage 回调内重新读取）
        when(pptAudioMapper.selectById(2L)).thenReturn(row);
        // TTS 抛异常（余额不足/限流）
        when(ttsService.synthesize(any(), any(), any(), anyDouble()))
                .thenThrow(new RuntimeException("balance insufficient"));

        worker.poll();

        // 失败 → 回滚抢占（releaseClaim 恢复 GENERATING 下轮重试）
        verify(pptAudioMapper).releaseClaim(eq(2L), anyString());
        // error_message 保留用户可见原因（R-15）
        verify(pptAudioMapper, atLeastOnce()).updateById(argThat(r ->
                "balance insufficient".equals(r.getErrorMessage())));
        // 状态不得置 READY
        verify(pptAudioMapper, never()).updateById(argThat(r -> "READY".equals(r.getStatus())));
    }

    @Test
    @DisplayName("Q-1: claim 抢占 0 行 → 本周期不消费（无重复处理）")
    void poll_NoClaimNoConsume() {
        when(pptAudioMapper.claimPending(anyString(), any(), anyInt())).thenReturn(0);
        when(htmlAudioMapper.claimPending(anyString(), any(), anyInt())).thenReturn(0);

        worker.poll();

        // 抢占 0 行 → 不查已抢占、不调用 TTS
        verify(pptAudioMapper, never()).selectClaimed(anyString());
        verify(htmlAudioMapper, never()).selectClaimed(anyString());
        verify(ttsService, never()).synthesize(any(), any(), any(), any());
    }

    // ====== G3-P1-C-2: 确定性错误 → 立即 FAILED（不再等 10 分钟 markTimedOut 兜底） ======

    /** 构造"已抢占 1 行 PPT、TTS 抛指定异常"的最小环境并执行 poll。 */
    private void pollWithTtsFailure(String errorMessage) {
        SlidePptPageAudio row = newPptAudioRow(9L, LocalDateTime.now().minusMinutes(1));
        when(pptAudioMapper.claimPending(anyString(), any(), anyInt())).thenReturn(1);
        when(htmlAudioMapper.claimPending(anyString(), any(), anyInt())).thenReturn(0);
        when(pptAudioMapper.selectClaimed(anyString())).thenReturn(List.of(row));
        when(htmlAudioMapper.selectClaimed(anyString())).thenReturn(List.of());
        SlidePptPageScript script = new SlidePptPageScript();
        script.setId(100L); script.setScriptText("讲述稿");
        when(pptScriptMapper.selectById(100L)).thenReturn(script);
        SlidePptPage page = new SlidePptPage();
        page.setId(10L); page.setCourseId(42L);
        when(pptPageMapper.selectById(10L)).thenReturn(page);
        when(pptAudioMapper.selectById(9L)).thenReturn(row);
        // 用 doThrow 而非 when().thenThrow()：循环多次 poll 时 when() 内部会执行上一次的
        // thenThrow stub（Mockito 已知行为）导致异常逃逸到测试层。
        doThrow(new RuntimeException(errorMessage))
                .when(ttsService).synthesize(any(), any(), any(), anyDouble());
        worker.poll();
    }

    @Test
    @DisplayName("G3-P1-C-2: 余额不足（1008）→ 立即 FAILED，不 releaseClaim 重试")
    void deterministicError_BalanceNotEnough_FailsImmediately() {
        pollWithTtsFailure("账户余额不足");

        verify(pptAudioMapper, atLeastOnce()).updateById(argThat(r ->
                "FAILED".equals(r.getStatus())
                        && "账户余额不足".equals(r.getErrorMessage())));
        verify(pptAudioMapper, never()).releaseClaim(anyLong(), anyString());
    }

    @Test
    @DisplayName("G3-P1-C-2: API Key 无效（2049）→ 立即 FAILED，不 releaseClaim 重试")
    void deterministicError_InvalidApiKey_FailsImmediately() {
        pollWithTtsFailure("MiniMax API Key 无效，请检查 backend 配置");

        verify(pptAudioMapper, atLeastOnce()).updateById(argThat(r ->
                "FAILED".equals(r.getStatus())
                        && "MiniMax API Key 无效，请检查 backend 配置".equals(r.getErrorMessage())));
        verify(pptAudioMapper, never()).releaseClaim(anyLong(), anyString());
    }

    @Test
    @DisplayName("G3-P1-C-2: 限流（1002）→ 立即 FAILED（提示 5 分钟后重试），不 releaseClaim 重试")
    void deterministicError_RateLimit_FailsImmediately() {
        pollWithTtsFailure("TTS 限流，请 5 分钟后重试");

        verify(pptAudioMapper, atLeastOnce()).updateById(argThat(r ->
                "FAILED".equals(r.getStatus())
                        && r.getErrorMessage() != null
                        && r.getErrorMessage().contains("限流")));
        verify(pptAudioMapper, never()).releaseClaim(anyLong(), anyString());
    }

    @Test
    @DisplayName("G3-P1-C-2: 未配置 API Key → 立即 FAILED，不 releaseClaim 重试")
    void deterministicError_NoApiKey_FailsImmediately() {
        pollWithTtsFailure("MiniMax API key 未配置");

        verify(pptAudioMapper, atLeastOnce()).updateById(argThat(r ->
                "FAILED".equals(r.getStatus())
                        && "MiniMax API key 未配置".equals(r.getErrorMessage())));
        verify(pptAudioMapper, never()).releaseClaim(anyLong(), anyString());
    }

    @Test
    @DisplayName("G3-P1-C-2: 网络失败 → 保留 GENERATING 重试（releaseClaim），不立即 FAILED")
    void transientError_Network_Retries() {
        pollWithTtsFailure("MiniMax 调用失败: Connection refused");

        verify(pptAudioMapper, atLeastOnce()).updateById(argThat(r ->
                r.getErrorMessage() != null && r.getErrorMessage().contains("MiniMax 调用失败")));
        verify(pptAudioMapper).releaseClaim(eq(9L), anyString());
        // 网络瞬时故障不得直接 FAILED
        verify(pptAudioMapper, never()).updateById(argThat(r -> "FAILED".equals(r.getStatus())));
    }

    @Test
    @DisplayName("G3-P1-C-2: 未知错误重试 3 次后 → FAILED")
    void unknownError_RetriesThenFails() {
        // 连续 3 次 UNKNOWN 失败（同一行 9）：前 2 次 releaseClaim 重试，第 3 次 FAILED
        for (int i = 0; i < 3; i++) {
            pollWithTtsFailure("some unknown error");
        }
        verify(pptAudioMapper, times(2)).releaseClaim(eq(9L), anyString());
        verify(pptAudioMapper, atLeastOnce()).updateById(argThat(r ->
                "FAILED".equals(r.getStatus())
                        && "some unknown error".equals(r.getErrorMessage())));
    }

    @Test
    @DisplayName("Q-1: 每轮 poll 先回收孤儿 PROCESSING + 标记超时 GENERATING")
    void poll_ReclaimsOrphansAndTimesOut() {
        when(pptAudioMapper.claimPending(anyString(), any(), anyInt())).thenReturn(0);
        when(htmlAudioMapper.claimPending(anyString(), any(), anyInt())).thenReturn(0);

        worker.poll();

        verify(pptAudioMapper).reclaimOrphans(any());
        verify(htmlAudioMapper).reclaimOrphans(any());
        verify(pptAudioMapper).markTimedOut(any());
        verify(htmlAudioMapper).markTimedOut(any());
    }

    @Test
    @DisplayName("Q-1: HTML 段音频同样走抢占流程（成功 → READY）")
    void poll_HtmlSegmentAudioClaimAndSuccess() {
        SlideHtmlSegmentAudio row = new SlideHtmlSegmentAudio();
        row.setId(3L);
        row.setSegmentScriptId(200L);
        row.setHtmlUnitId(300L);
        row.setSegmentIndex(1);
        row.setStatus("GENERATING");
        row.setGenerationStartedAt(LocalDateTime.now().minusMinutes(1));
        row.setVoiceUsed("female-shaonv");
        row.setModelUsed("speech-2.8-hd");
        row.setAudioToken(UUID.randomUUID().toString().replace("-", ""));
        when(pptAudioMapper.claimPending(anyString(), any(), anyInt())).thenReturn(0);
        when(htmlAudioMapper.claimPending(anyString(), any(), anyInt())).thenReturn(1);
        when(pptAudioMapper.selectClaimed(anyString())).thenReturn(List.of());
        when(htmlAudioMapper.selectClaimed(anyString())).thenReturn(List.of(row));
        SlideHtmlSegmentScript script = new SlideHtmlSegmentScript();
        script.setId(200L); script.setScriptText("段讲述稿");
        when(htmlSegmentScriptMapper.selectById(200L)).thenReturn(script);
        SlideHtmlUnit unit = new SlideHtmlUnit();
        unit.setId(300L); unit.setCourseId(7L);
        when(htmlUnitMapper.selectById(300L)).thenReturn(unit);
        when(htmlAudioMapper.selectById(3L)).thenReturn(row);
        when(ttsService.synthesize(eq("段讲述稿"), any(), any(), anyDouble()))
                .thenReturn(new TtsService.SynthesizedAudio(new byte[]{9}, 3));

        worker.poll();

        verify(htmlAudioMapper, atLeastOnce()).updateById(argThat(r ->
                "READY".equals(r.getStatus()) && r.getStoragePath() != null));
    }

    @Test
    @DisplayName("N-5: spring.task.scheduling.pool.size=1（单线程，默认）→ 启动校验通过")
    void schedulerPoolSizeDefaultAllowed() {
        ReflectionTestUtils.setField(worker, "schedulerPoolSize", 1);
        // @PostConstruct 逻辑直接调用（单元测试不经 Spring 容器，需手动触发）
        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(worker, "verifySingleThreadedScheduler"),
                "pool.size=1 单线程 scheduler 不应阻止启动");
    }

    @Test
    @DisplayName("N-5: spring.task.scheduling.pool.size>1（多线程 scheduler）→ fail-fast 抛 IllegalStateException")
    void schedulerPoolSizeAboveOneFailsFast() {
        ReflectionTestUtils.setField(worker, "schedulerPoolSize", 2);
        // 多线程 scheduler 并发 poll 可能重复合成同一行（重复扣费）→ 启动必须被阻止
        assertThrows(IllegalStateException.class,
                () -> ReflectionTestUtils.invokeMethod(worker, "verifySingleThreadedScheduler"),
                "pool.size>1 多线程 scheduler 与本 worker 单线程设计不兼容，必须 fail-fast");
    }
}
