package com.microcourse.plugin.interactive.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.entity.Course;
import com.microcourse.entity.CourseSection;
import com.microcourse.entity.Enrollment;
import com.microcourse.enums.EnrollmentStatus;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.plugin.interactive.dto.SlidePageVO;
import com.microcourse.plugin.interactive.dto.TtsStatusResponse;
import com.microcourse.repository.CourseSectionRepository;
import com.microcourse.plugin.interactive.entity.SlidePage;
import com.microcourse.plugin.interactive.mapper.SlidePageMapper;
import com.microcourse.plugin.interactive.service.TtsService;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.util.SecurityUtil;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.beans.factory.annotation.Autowired;

@Service
@ConditionalOnProperty(value = "plugin.interactive.enabled", havingValue = "true", matchIfMissing = true)
public class TtsServiceImpl implements TtsService {

    private static final Logger log = LoggerFactory.getLogger(TtsServiceImpl.class);

    private final SlidePageMapper slidePageMapper;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseSectionRepository sectionRepository;
    private final TransactionTemplate transactionTemplate;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper objectMapper;

    private static final int MAX_STRING_LENGTH = 100 * 1024 * 1024; // 100MB

    @Value("${plugin.interactive.minimax.tts-model:speech-2.8-hd}")
    private String ttsModel;

    /** Qwen3-TTS 0.6B 预定义声音 ID（vivian/serena/dylan/ryan/eric/aiden/ono_anna/sohee/uncle_fu） */
    @Value("${plugin.interactive.minimax.tts-voice:vivian}")
    private String ttsVoice;

    @Value("${plugin.interactive.minimax.api-key:}")
    private String minimaxApiKey;

    private static final String MINIMAX_TTS_URL = "https://api.minimaxi.com/v1/t2a_v2";

    @Value("${plugin.interactive.slides.storage-path:/data/slides}")
    private String storagePath;

    private ExecutorService slideRenderExecutor;

    /** Qwen3-TTS 本地服务地址 */
    @Value("${plugin.interactive.tts.local-url:http://127.0.0.1:8000}")
    private String ttsLocalUrl;

    /** TTS HTTP 调用超时（秒）—— 1 分钟音频 CPU 约 6 分钟 */
    @Value("${plugin.interactive.tts.timeout-seconds:600}")
    private int ttsTimeoutSeconds;

    private static final String MMX_CMD = "mmx";

    /** J8-01: 启动时检测 mmx CLI 是否可用 */
    private volatile boolean mmxAvailable = false;
    private volatile String mmxCheckMessage = "未检测";

    /** Qwen3-TTS 本地服务是否可用 */
    private volatile boolean ttsLocalAvailable = false;
    private volatile String ttsLocalCheckMessage = "未检测";

    private final ConcurrentHashMap<String, TtsTaskState> taskStates = new ConcurrentHashMap<>();

    public TtsServiceImpl(SlidePageMapper slidePageMapper,
                          CourseRepository courseRepository,
                          EnrollmentRepository enrollmentRepository,
                          CourseSectionRepository sectionRepository,
                          TransactionTemplate transactionTemplate,
                          ObjectMapper objectMapper,
                          ExecutorService slideRenderExecutor) {
        this.slidePageMapper = slidePageMapper;
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.sectionRepository = sectionRepository;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = configureObjectMapper(objectMapper);
        this.slideRenderExecutor = slideRenderExecutor;
    }

    private ObjectMapper configureObjectMapper(ObjectMapper mapper) {
        try {
            ObjectMapper copy = mapper.copy();
            copy.getFactory().setStreamReadConstraints(
                    StreamReadConstraints.builder().maxStringLength(MAX_STRING_LENGTH).build());
            log.info("[TTS] ObjectMapper 已配置 maxStringLength={}", MAX_STRING_LENGTH);
            return copy;
        } catch (Exception e) {
            log.warn("[TTS] ObjectMapper 复制失败，使用原始实例: {}", e.getMessage());
            return mapper;
        }
    }

    /**
     * J8-01: 启动时检测 TTS 后端可用性
     * 优先级：Qwen3-TTS 本地服务 > mmx CLI > 纯文本模式
     */
    @PostConstruct
    public void checkTtsAvailability() {
        // 1. 检测 Qwen3-TTS 本地服务
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(ttsLocalUrl + "/health"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 && resp.body().contains("\"status\":\"ok\"")) {
                ttsLocalAvailable = true;
                ttsLocalCheckMessage = "可用 (" + ttsLocalUrl + ")";
                log.info("[TTS] Qwen3-TTS 本地服务检测通过，TTS 正常可用");
                return;
            } else {
                ttsLocalCheckMessage = "本地服务响应异常: " + resp.statusCode();
            }
        } catch (Exception e) {
            ttsLocalCheckMessage = "本地服务不可用: " + e.getMessage();
            log.warn("[TTS] Qwen3-TTS 本地服务不可用: {}，尝试降级到 mmx", e.getMessage());
        }

        // 2. 降级到 MiniMax API（HTTP 直连）
        if (minimaxApiKey != null && !minimaxApiKey.isBlank()) {
            mmxAvailable = true;
            mmxCheckMessage = "MiniMax API 已配置";
            log.info("[TTS] MiniMax API key 已配置，TTS 将通过 HTTP API 调用");
        } else {
            mmxCheckMessage = "MiniMax API key 未配置";
            log.warn("[TTS] MiniMax API key 未配置，TTS 将降级为纯文本模式");
        }
    }

    @Override
    public SlidePageVO generate(Long courseId, Integer pageNumber) {
        checkOwner(courseId);
        SlidePage page = getSinglePage(courseId, pageNumber);
        return doGenerate(courseId, pageNumber, page);
    }

    private SlidePageVO doGenerate(Long courseId, Integer pageNumber, SlidePage page) {
        String script = page.getNarrationScript();
        if (script == null || script.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "讲述稿为空，请先生成讲述稿");
        }

        Long sectionId = page.getSectionId();
        String audioFileName = sectionId != null
                ? "page_" + sectionId + ".mp3"
                : "page_" + pageNumber + ".mp3";

        int audioDuration = 0;
        boolean audioGenerated = false;

        try {
            Path audioDir = Paths.get(storagePath, String.valueOf(courseId), "audio");
            Files.createDirectories(audioDir);
            Path audioPath = audioDir.resolve(audioFileName);

            txSetPageStatus(page.getId(), "AUDIO_GENERATING");

            if (ttsLocalAvailable) {
                try {
                    audioDuration = callQwen3TtsService(script, audioPath);
                    audioGenerated = true;
                } catch (Exception e) {
                    log.warn("[TTS] Qwen3-TTS 调用失败: {}，降级到 mmx", e.getMessage());
                    if (mmxAvailable) {
                        audioDuration = callMmxCli(script, audioPath);
                        audioGenerated = true;
                    }
                }
            } else if (mmxAvailable) {
                audioDuration = callMmxCli(script, audioPath);
                audioGenerated = true;
            }

            if (!audioGenerated) {
                txSetPageStatus(page.getId(), "TEACHER_EDITED");
                throw new BusinessException(ErrorCode.TTS_GENERATE_FAILED);
            }

            long fileSize = Files.size(audioPath);
            if (audioDuration <= 0) {
                audioDuration = estimateDuration(fileSize);
            }

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("TTS failed for courseId={} page={}", courseId, pageNumber, e);
            try { txSetPageStatus(page.getId(), "TEACHER_EDITED"); } catch (Exception ignored) {}
            throw new BusinessException(ErrorCode.TTS_GENERATE_FAILED);
        }

        final int finalDuration = audioDuration;
        final Long sid = sectionId;
        transactionTemplate.executeWithoutResult(tx -> {
            SlidePage p = slidePageMapper.selectById(page.getId());
            if (p == null) return;
            String audioUrl = sid != null
                    ? "/api/courses/" + courseId + "/slides/pages/" + pageNumber + "/audio?sectionId=" + sid
                    : "/api/courses/" + courseId + "/slides/pages/" + pageNumber + "/audio";
            p.setNarrationAudioUrl(audioUrl);
            p.setAudioDuration(finalDuration);
            p.setNarrationStatus("AUDIO_READY");
            p.setUpdatedAt(LocalDateTime.now());
            slidePageMapper.updateById(p);
        });

        log.info("TTS complete: courseId={}, page={}, sectionId={}, duration={}s, backend={}",
                courseId, pageNumber, sectionId, finalDuration,
                ttsLocalAvailable ? "qwen3-local" : "mmx");

        SlidePage updated = slidePageMapper.selectById(page.getId());
        return toPageVO(updated);
    }

    private void txSetPageStatus(Long pageId, String status) {
        transactionTemplate.execute(tx -> {
            SlidePage p = slidePageMapper.selectById(pageId);
            if (p == null) return null;
            p.setNarrationStatus(status);
            p.setUpdatedAt(LocalDateTime.now());
            slidePageMapper.updateById(p);
            return null;
        });
    }

    /**
     * 调用 Qwen3-TTS 本地 HTTP 服务
     * 0.6B CustomVoice 用预定义声音 ID（不是自然语言描述）
     */
    private int callQwen3TtsService(String script, Path audioPath) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("text", script);
        body.put("speaker", ttsVoice);
        body.put("language", "chinese");
        body.put("output_filename", audioPath.getFileName().toString());

        String json = objectMapper.writeValueAsString(body);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(ttsLocalUrl + "/api/tts"))
                .timeout(Duration.ofSeconds(ttsTimeoutSeconds))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IOException("Qwen3-TTS service returned " + resp.statusCode() + ": " + resp.body());
        }

        // 解析返回的 JSON 获取音频时长
        Map<String, Object> result = objectMapper.readValue(resp.body(), Map.class);
        Object durationObj = result.get("duration");
        int duration = durationObj instanceof Number ? ((Number) durationObj).intValue() : 0;

        Object pathObj = result.get("path");
        if (pathObj == null || pathObj.toString().isBlank()) {
            throw new IOException("TTS 响应缺少 path 字段");
        }
        Path ttsOutputPath = Paths.get(pathObj.toString());
        if (!ttsOutputPath.isAbsolute()) {
            throw new IOException("TTS 返回的路径不是绝对路径: " + ttsOutputPath);
        }
        if (!Files.exists(ttsOutputPath)) {
            throw new IOException("TTS 生成的音频文件不存在: " + ttsOutputPath);
        }
        if (!Files.isRegularFile(ttsOutputPath)) {
            throw new IOException("TTS 返回的路径不是普通文件: " + ttsOutputPath);
        }
        long fileSize = Files.size(ttsOutputPath);
        if (fileSize == 0) {
            throw new IOException("TTS 生成的音频文件为空: " + ttsOutputPath);
        }
        // 校验 MP3 文件魔数（ID3 tag 或 MPEG sync）
        try (InputStream is = Files.newInputStream(ttsOutputPath)) {
            byte[] header = new byte[3];
            int read = is.read(header);
            boolean validMp3 = (read >= 3) &&
                    ((header[0] == 'I' && header[1] == 'D' && header[2] == '3') ||
                     ((header[0] & 0xFF) == 0xFF && (header[1] & 0xE0) == 0xE0));
            if (!validMp3) {
                throw new IOException("TTS 生成的文件不是有效的 MP3 格式");
            }
        }

        Files.move(ttsOutputPath, audioPath, StandardCopyOption.REPLACE_EXISTING);
        log.debug("[TTS] Qwen3-TTS 音频已保存到: {} (size={})", audioPath, fileSize);

        return duration;
    }

    /**
     * 调用 MiniMax TTS HTTP API（/v1/t2a_v2）
     */
    private int callMmxCli(String script, Path audioPath) throws Exception {
        if (minimaxApiKey == null || minimaxApiKey.isBlank()) {
            log.warn("[TTS] MiniMax API key 未配置");
            throw new BusinessException(ErrorCode.TTS_GENERATE_FAILED, "MiniMax API key 未配置");
        }

        var bodyMap = new java.util.LinkedHashMap<String, Object>();
        bodyMap.put("model", ttsModel);
        bodyMap.put("text", script);

        var voiceSetting = new java.util.LinkedHashMap<String, Object>();
        voiceSetting.put("voice_id", ttsVoice);
        voiceSetting.put("speed", 1.0);
        voiceSetting.put("vol", 1.0);
        voiceSetting.put("pitch", 0);
        bodyMap.put("voice_setting", voiceSetting);

        var audioSetting = new java.util.LinkedHashMap<String, Object>();
        audioSetting.put("sample_rate", 32000);
        audioSetting.put("format", "mp3");
        audioSetting.put("bitrate", 128000);
        audioSetting.put("channel", 1);
        bodyMap.put("audio_setting", audioSetting);

        bodyMap.put("output_format", "hex");

        // subtitle_enable=true 会导致响应 JSON 嵌入 20MB+ 字幕数据，暂不启用
        // bodyMap.put("subtitle_enable", true);
        // bodyMap.put("subtitle_type", "word");

        String requestBody = objectMapper.writeValueAsString(bodyMap);
        log.debug("[TTS] MiniMax request: model={}, voice={}, textLen={}",
                ttsModel, ttsVoice, script.length());

        var request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(MINIMAX_TTS_URL))
                .header("Authorization", "Bearer " + minimaxApiKey)
                .header("Content-Type", "application/json")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(ttsTimeoutSeconds))
                .build();

        var response = httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofByteArray());
        byte[] respBody = response.body();
        String respStr = new String(respBody, StandardCharsets.UTF_8);

        // 检查 API 错误
        if (respStr.contains("\"base_resp\"")) {
            var parsed = objectMapper.readTree(respBody);
            int code = parsed.path("base_resp").path("status_code").asInt(0);
            if (code != 0) {
                String msg = parsed.path("base_resp").path("status_msg").asText("unknown");
                log.error("[TTS] MiniMax API 错误: code={}, msg={}", code, msg);
                throw new BusinessException(ErrorCode.TTS_GENERATE_FAILED, "MiniMax 错误: " + msg);
            }
        }

        // 从 data.audio 中提取 hex 音频并解码
        var root = objectMapper.readTree(respBody);
        String audioHex = root.path("data").path("audio").asText(null);
        if (audioHex == null || audioHex.isEmpty()) {
            log.error("[TTS] MiniMax 响应缺少 audio 数据: {}", respStr.length() > 200 ? respStr.substring(0, 200) : respStr);
            throw new BusinessException(ErrorCode.TTS_GENERATE_FAILED, "MiniMax 响应缺少音频数据");
        }

        int len = audioHex.length();
        if (len % 2 != 0) {
            log.error("[TTS] MiniMax audio_hex 长度为 {} (奇数)，数据损坏: {}", len, audioHex.substring(0, Math.min(64, audioHex.length())));
            throw new BusinessException(ErrorCode.TTS_GENERATE_FAILED, "MiniMax 响应 audio_hex 长度为奇数，数据损坏");
        }
        byte[] audioBytes = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            int hi = Character.digit(audioHex.charAt(i), 16);
            int lo = Character.digit(audioHex.charAt(i + 1), 16);
            if (hi < 0 || lo < 0) {
                log.error("[TTS] MiniMax audio_hex 包含非十六进制字符 at pos {}: '{}'", i, audioHex.charAt(i));
                throw new BusinessException(ErrorCode.TTS_GENERATE_FAILED, "MiniMax 响应 audio_hex 包含无效字符，数据损坏");
            }
            audioBytes[i / 2] = (byte) ((hi << 4) | lo);
        }

        Files.write(audioPath, audioBytes);
        int estimatedSec = Math.max(1, (int) (audioBytes.length / 16000));
        log.info("[TTS] MiniMax 音频生成成功: path={}, size={} bytes, estimated={}s", audioPath, audioBytes.length, estimatedSec);
        return estimatedSec;
    }

    @Override
    @Async("slideRenderExecutor")
    public void generateAll(Long courseId) {
        // P1-I-01: 检查 TTS 后端可用性
        if (!ttsLocalAvailable && !mmxAvailable) {
            log.warn("[Tts] 无可用 TTS 后端，跳过批量音频生成 courseId={}", courseId);
            markAllPagesError(courseId, "TTS 音频生成失败：无可用 TTS 后端（Qwen3-TTS 和 mmx 均不可用）");
            return;
        }
        LambdaQueryWrapper<SlidePage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SlidePage::getCourseId, courseId)
                .in(SlidePage::getNarrationStatus, "AI_GENERATED", "TEACHER_EDITED")
                .orderByAsc(SlidePage::getPageNumber);
        List<SlidePage> pages = slidePageMapper.selectList(wrapper);

        for (SlidePage page : pages) {
            try {
                doGenerate(courseId, page.getPageNumber(), page);
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (BusinessException e) {
                log.warn("TTS failed for page {}: {}", page.getPageNumber(), e.getMessage());
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CompletableFuture<TtsStatusResponse> generateSection(Long courseId, Long sectionId,
                                                               String voice, String model,
                                                               Double speed, boolean splitByPage) {
        Course course = courseRepository.selectById(courseId);
        if (course == null) throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        if (!SecurityUtil.isOwnerOrAdmin(course.getTeacherId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }

        CourseSection section = sectionRepository.selectById(sectionId);
        if (section == null) throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "小节不存在");
        if (!courseId.equals(section.getCourseId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "小节不属于该课程");
        }

        String scriptContent = section.getScriptContent();
        if (scriptContent == null || scriptContent.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "讲述稿为空，请先为该小节生成讲述稿");
        }

        String taskId = "tts-" + UUID.randomUUID();
        int estimatedSeconds = Math.max(30, scriptContent.length() / 50);

        TtsTaskState state = new TtsTaskState(taskId, courseId, sectionId, estimatedSeconds);
        taskStates.put(taskId, state);

        doGenerateSectionAsync(taskId, courseId, sectionId, scriptContent, voice, model, speed, splitByPage);

        return CompletableFuture.completedFuture(TtsStatusResponse.queued(taskId, estimatedSeconds));
    }

    private void doGenerateSectionAsync(String taskId, Long courseId, Long sectionId,
                                        String scriptContent, String voice,
                                        String model, Double speed, boolean splitByPage) {
        slideRenderExecutor.submit(() -> {
            try {
                List<SlidePage> pages = transactionTemplate.execute(tx -> {
                    LambdaQueryWrapper<SlidePage> wrapper = new LambdaQueryWrapper<>();
                    wrapper.eq(SlidePage::getCourseId, courseId)
                            .eq(SlidePage::getSectionId, sectionId)
                            .orderByAsc(SlidePage::getPageNumber);
                    return slidePageMapper.selectList(wrapper);
                });

                if (pages == null || pages.isEmpty()) {
                    markTaskFailed(taskId, "该小节没有 SlidePage 记录");
                    return;
                }

                String[] segments;
                if (splitByPage) {
                    segments = scriptContent.split("\\n\\n");
                } else {
                    segments = new String[]{scriptContent};
                }

                if (segments.length != pages.size()) {
                    log.warn("[TTS] segment count {} != page count {}, truncating", segments.length, pages.size());
                    pages = pages.subList(0, Math.min(segments.length, pages.size()));
                }

                Path audioDir = Paths.get(storagePath, String.valueOf(courseId), "audio");
                Files.createDirectories(audioDir);

                long totalDuration = 0;
                List<TtsStatusResponse.AudioSegment> resultSegments = new ArrayList<>();

                for (int i = 0; i < pages.size(); i++) {
                    SlidePage page = pages.get(i);
                    String segText = segments[i].trim();
                    if (segText.isEmpty()) continue;

                    String segFileName = "section_" + sectionId + "_page_" + page.getPageNumber() + ".mp3";
                    Path segPath = audioDir.resolve(segFileName);

                    int segDuration = 0;
                    int retries = 3;
                    while (retries-- > 0) {
                        try {
                            segDuration = callMmxCliWithVoice(segText, segPath, voice != null ? voice : ttsVoice, model, speed);
                            break;
                        } catch (Exception e) {
                            log.warn("[TTS] segment {} retry, error: {}", page.getPageNumber(), e.getMessage());
                            if (retries == 0) throw e;
                            try { Thread.sleep(2000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); throw new RuntimeException(ie); }
                        }
                    }

                    long segSize = Files.size(segPath);
                    totalDuration += segDuration;

                    String segUrl = "/api/courses/" + courseId + "/slides/pages/" + page.getPageNumber()
                            + "/audio?sectionId=" + sectionId + "&v=2";

                    TtsStatusResponse.AudioSegment seg = new TtsStatusResponse.AudioSegment(
                            page.getPageNumber(), segUrl, (long) segDuration, segSize);
                    resultSegments.add(seg);

                    final int fsegDuration = segDuration;
                    final long fsegSize = segSize;
                    final String fsegUrl = segUrl;
                    final String fsegText = segText;
                    final int fsegCount = segments.length;
                    final String fvoice = voice != null ? voice : ttsVoice;
                    final String fmodel = model != null ? model : ttsModel;
                    final LocalDateTime fnow = LocalDateTime.now();
                    final String ftaskId = taskId;
                    final List<TtsStatusResponse.AudioSegment> fResultSegs = resultSegments;

                    transactionTemplate.execute(tx -> {
                        page.setNarrationScript(fsegText);
                        page.setNarrationAudioUrl(fsegUrl);
                        page.setAudioDuration(fsegDuration);
                        page.setNarrationStatus("AUDIO_READY");
                        page.setSegmentCount(fsegCount);
                        page.setVoice(fvoice);
                        page.setTtsModel(fmodel);
                        page.setGeneratedAt(fnow);
                        page.setUpdatedAt(fnow);
                        slidePageMapper.updateById(page);
                        return null;
                    });

                    stateAppendSegment(ftaskId, seg);
                    try { Thread.sleep(1000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                }

                String mergedUrl = "/api/courses/" + courseId + "/slides/pages/1/audio?sectionId="
                        + sectionId + "&v=2&merged=true";
                markTaskCompleted(taskId, mergedUrl, totalDuration, resultSegments);

            } catch (Exception e) {
                log.error("[TTS] section async task failed: taskId={}, error={}", taskId, e.getMessage());
                markTaskFailed(taskId, e.getMessage());
            }
        });
    }

    @Override
    @Transactional(readOnly = true)
    public TtsStatusResponse getSectionTtsStatus(Long courseId, Long sectionId, String taskId) {
        TtsTaskState state = taskStates.get(taskId);
        if (state == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "任务不存在或已过期");
        }
        if (!courseId.equals(state.courseId) || !sectionId.equals(state.sectionId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "任务与课程/小节不匹配");
        }
        return state.toResponse();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<TtsStatusResponse> generateSectionsBatch(Long courseId, List<Long> sectionIds,
                                                         String voice, String model, Double speed,
                                                         boolean splitByPage) {
        Course course = courseRepository.selectById(courseId);
        if (course == null) throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        if (!SecurityUtil.isOwnerOrAdmin(course.getTeacherId()) && !SecurityUtil.isAdmin()) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }

        List<TtsStatusResponse> results = new ArrayList<>();
        for (Long sectionId : sectionIds) {
            try {
                TtsStatusResponse r = generateSection(courseId, sectionId, voice, model, speed, splitByPage).join();
                results.add(r);
            } catch (Exception e) {
                log.warn("[BatchTTS] section {} failed: {}", sectionId, e.getMessage());
            }
        }
        return results;
    }

    private int callMmxCliWithVoice(String script, Path audioPath, String voiceId, String model, Double speed) throws Exception {
        if (minimaxApiKey == null || minimaxApiKey.isBlank()) {
            throw new BusinessException(ErrorCode.TTS_GENERATE_FAILED, "MiniMax API key 未配置");
        }

        var bodyMap = new java.util.LinkedHashMap<String, Object>();
        bodyMap.put("model", model != null ? model : ttsModel);
        bodyMap.put("text", script);

        var voiceSetting = new java.util.LinkedHashMap<String, Object>();
        voiceSetting.put("voice_id", voiceId);
        voiceSetting.put("speed", speed != null ? speed : 1.0);
        voiceSetting.put("vol", 1.0);
        voiceSetting.put("pitch", 0);
        bodyMap.put("voice_setting", voiceSetting);

        var audioSetting = new java.util.LinkedHashMap<String, Object>();
        audioSetting.put("sample_rate", 32000);
        audioSetting.put("format", "mp3");
        audioSetting.put("bitrate", 128000);
        audioSetting.put("channel", 1);
        bodyMap.put("audio_setting", audioSetting);
        bodyMap.put("output_format", "hex");

        String requestBody = objectMapper.writeValueAsString(bodyMap);

        var request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(MINIMAX_TTS_URL))
                .header("Authorization", "Bearer " + minimaxApiKey)
                .header("Content-Type", "application/json")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(ttsTimeoutSeconds))
                .build();

        var response = httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofByteArray());
        byte[] respBody = response.body();
        String respStr = new String(respBody, StandardCharsets.UTF_8);

        if (respStr.contains("\"base_resp\"")) {
            var parsed = objectMapper.readTree(respBody);
            int code = parsed.path("base_resp").path("status_code").asInt(0);
            if (code != 0) {
                String msg = parsed.path("base_resp").path("status_msg").asText("unknown");
                if (code == 2049) {
                    throw new BusinessException(ErrorCode.TTS_GENERATE_FAILED, "MiniMax API Key 无效，请检查 backend 配置");
                }
                if (code == 1008) {
                    throw new BusinessException(ErrorCode.TTS_GENERATE_FAILED, "账户余额不足");
                }
                if (code == 1002) {
                    throw new BusinessException(ErrorCode.TTS_GENERATE_FAILED, "TTS 限流，请 5 分钟后重试");
                }
                throw new BusinessException(ErrorCode.TTS_GENERATE_FAILED, "MiniMax 错误: " + msg);
            }
        }

        var root = objectMapper.readTree(respBody);
        String audioHex = root.path("data").path("audio").asText(null);
        if (audioHex == null || audioHex.isEmpty()) {
            throw new BusinessException(ErrorCode.TTS_GENERATE_FAILED, "MiniMax 响应缺少音频数据");
        }

        int len = audioHex.length();
        if (len % 2 != 0) {
            log.error("[TTS] audio_hex 长度为 {} (奇数)，数据损坏", len);
            throw new BusinessException(ErrorCode.TTS_GENERATE_FAILED, "MiniMax 响应 audio_hex 长度为奇数，数据损坏");
        }
        byte[] audioBytes = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            int hi = Character.digit(audioHex.charAt(i), 16);
            int lo = Character.digit(audioHex.charAt(i + 1), 16);
            if (hi < 0 || lo < 0) {
                log.error("[TTS] audio_hex 包含非十六进制字符 at pos {}: '{}'", i, audioHex.charAt(i));
                throw new BusinessException(ErrorCode.TTS_GENERATE_FAILED, "MiniMax 响应 audio_hex 包含无效字符，数据损坏");
            }
            audioBytes[i / 2] = (byte) ((hi << 4) | lo);
        }

        Files.write(audioPath, audioBytes);
        int estimatedSec = Math.max(1, (int) (audioBytes.length / 16000));
        log.info("[TTS] MiniMax segment generated: {} bytes, ~{}s", audioBytes.length, estimatedSec);
        return estimatedSec;
    }

    private synchronized void stateAppendSegment(String taskId, TtsStatusResponse.AudioSegment seg) {
        TtsTaskState state = taskStates.get(taskId);
        if (state != null) {
            state.segments.add(seg);
        }
    }

    private synchronized void markTaskCompleted(String taskId, String mergedUrl, long totalDuration,
                                               List<TtsStatusResponse.AudioSegment> segments) {
        TtsTaskState state = taskStates.get(taskId);
        if (state != null) {
            state.status = "completed";
            state.mergedAudioUrl = mergedUrl;
            state.totalDuration = totalDuration;
            state.segments = new ArrayList<>(segments);
            state.completedAt = System.currentTimeMillis();
        }
    }

    private synchronized void markTaskFailed(String taskId, String errorMsg) {
        TtsTaskState state = taskStates.get(taskId);
        if (state != null) {
            state.status = "failed";
            state.errorMessage = errorMsg;
            state.completedAt = System.currentTimeMillis();
        }
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 300000)
    public void cleanupExpiredTaskStates() {
        long expireThreshold = System.currentTimeMillis() - 30 * 60 * 1000L;
        taskStates.entrySet().removeIf(entry -> {
            if (entry.getValue().completedAt > 0 && entry.getValue().completedAt < expireThreshold) {
                log.debug("[Tts] cleanup expired task state: {}", entry.getKey());
                return true;
            }
            return false;
        });
    }

    private static class TtsTaskState {
        final String taskId;
        final Long courseId;
        final Long sectionId;
        final int estimatedSeconds;
        volatile String status = "queued";
        volatile String errorMessage;
        volatile String mergedAudioUrl;
        volatile long totalDuration = 0;
        volatile List<TtsStatusResponse.AudioSegment> segments = new ArrayList<>();
        volatile long completedAt = 0;

        TtsTaskState(String taskId, Long courseId, Long sectionId, int estimatedSeconds) {
            this.taskId = taskId;
            this.courseId = courseId;
            this.sectionId = sectionId;
            this.estimatedSeconds = estimatedSeconds;
        }

        TtsStatusResponse toResponse() {
            if ("queued".equals(status)) {
                return TtsStatusResponse.queued(taskId, estimatedSeconds);
            }
            if ("failed".equals(status)) {
                return TtsStatusResponse.failed(taskId, errorMessage);
            }
            return TtsStatusResponse.completed(taskId, segments, mergedAudioUrl, totalDuration);
        }
    }

    @Override
    public boolean validateAudioToken(Long courseId, Integer pageNumber, Long sectionId, String token) {
        if (token == null || token.isBlank()) return false;
        if (sectionId == null) return false;
        LambdaQueryWrapper<SlidePage> qw = new LambdaQueryWrapper<SlidePage>()
                .eq(SlidePage::getCourseId, courseId)
                .eq(SlidePage::getSectionId, sectionId)
                .eq(SlidePage::getPageNumber, pageNumber);
        SlidePage page = slidePageMapper.selectOne(qw);
        if (page == null || page.getNarrationAudioUrl() == null) return false;
        return page.getNarrationAudioUrl().contains("token=" + token);
    }

    @Override
    public byte[] getAudio(Long courseId, Integer pageNumber, Long sectionId) {
        try {
            Path basePath = Paths.get(storagePath, String.valueOf(courseId), "audio").toRealPath();
            Path audioPath = null;

            if (sectionId != null && pageNumber != null) {
                audioPath = resolveAudioFromDb(courseId, pageNumber, sectionId, basePath);
                if (audioPath == null) {
                    audioPath = tryGlobResolve(basePath, "section_" + sectionId + "_*_page_" + pageNumber + ".mp3");
                }
                if (audioPath == null) {
                    Path newPath = basePath.resolve("section_" + sectionId + "_page_" + pageNumber + ".mp3").normalize();
                    if (newPath.startsWith(basePath) && Files.exists(newPath)) {
                        audioPath = newPath;
                    }
                }
                if (audioPath == null) {
                    Path compatPath = basePath.resolve("page_" + sectionId + ".mp3").normalize();
                    if (compatPath.startsWith(basePath) && Files.exists(compatPath)) {
                        audioPath = compatPath;
                    }
                }
                if (audioPath == null) {
                    audioPath = resolveMergedFromDb(courseId, sectionId, basePath);
                }
                if (audioPath == null) {
                    audioPath = tryGlobResolve(basePath, "section_" + sectionId + "_*_merged.mp3");
                }
                if (audioPath == null) {
                    Path mergedPath = basePath.resolve("section_" + sectionId + "_merged.mp3").normalize();
                    if (mergedPath.startsWith(basePath) && Files.exists(mergedPath)) {
                        audioPath = mergedPath;
                    }
                }
                if (audioPath == null) {
                    throw new NoSuchFileException(
                        "音频文件不存在: sectionId=" + sectionId + ", pageNumber=" + pageNumber);
                }
            } else if (sectionId != null) {
                audioPath = resolveMergedFromDb(courseId, sectionId, basePath);
                if (audioPath == null) {
                    audioPath = tryGlobResolve(basePath, "section_" + sectionId + "_*_merged.mp3");
                }
                if (audioPath == null) {
                    Path mergedPath = basePath.resolve("section_" + sectionId + "_merged.mp3").normalize();
                    if (mergedPath.startsWith(basePath) && Files.exists(mergedPath)) {
                        audioPath = mergedPath;
                    }
                }
                if (audioPath == null) {
                    Path compatPath = basePath.resolve("page_" + sectionId + ".mp3").normalize();
                    if (compatPath.startsWith(basePath) && Files.exists(compatPath)) {
                        audioPath = compatPath;
                    }
                }
                if (audioPath == null) {
                    throw new NoSuchFileException(
                        "音频文件不存在: sectionId=" + sectionId);
                }
            } else {
                Path pagePath = basePath.resolve("page_" + pageNumber + ".mp3").normalize();
                if (!pagePath.startsWith(basePath)) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "非法的音频路径");
                }
                if (!Files.exists(pagePath)) {
                    throw new NoSuchFileException("音频文件不存在: courseId=" + courseId + ", pageNumber=" + pageNumber);
                }
                audioPath = pagePath;
            }
            if (!audioPath.startsWith(basePath)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "非法的音频路径");
            }
            return Files.readAllBytes(audioPath);
        } catch (NoSuchFileException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "音频文件不存在");
        } catch (IOException e) {
            log.error("[Tts] 读取音频文件失败 courseId={} page={} sectionId={}", courseId, pageNumber, sectionId, e);
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "音频文件读取失败");
        }
    }

    private Path resolveAudioFromDb(Long courseId, Integer pageNumber, Long sectionId, Path basePath) throws IOException {
        LambdaQueryWrapper<SlidePage> qw = new LambdaQueryWrapper<SlidePage>()
                .eq(SlidePage::getCourseId, courseId)
                .eq(SlidePage::getSectionId, sectionId)
                .eq(SlidePage::getPageNumber, pageNumber);
        SlidePage page = slidePageMapper.selectOne(qw);
        if (page == null || page.getNarrationAudioUrl() == null) return null;
        String token = extractToken(page.getNarrationAudioUrl());
        if (token == null) return null;
        Path p = basePath.resolve("section_" + sectionId + "_" + token + "_page_" + pageNumber + ".mp3").normalize();
        if (p.startsWith(basePath) && Files.exists(p)) return p;
        p = basePath.resolve("section_" + sectionId + "_" + token + "_merged.mp3").normalize();
        if (p.startsWith(basePath) && Files.exists(p)) return p;
        return null;
    }

    private Path resolveMergedFromDb(Long courseId, Long sectionId, Path basePath) throws IOException {
        LambdaQueryWrapper<SlidePage> qw = new LambdaQueryWrapper<SlidePage>()
                .eq(SlidePage::getCourseId, courseId)
                .eq(SlidePage::getSectionId, sectionId)
                .last("LIMIT 1");
        SlidePage page = slidePageMapper.selectOne(qw);
        if (page == null || page.getNarrationAudioUrl() == null) return null;
        String token = extractToken(page.getNarrationAudioUrl());
        if (token == null) return null;
        Path p = basePath.resolve("section_" + sectionId + "_" + token + "_merged.mp3").normalize();
        if (p.startsWith(basePath) && Files.exists(p)) return p;
        return null;
    }

    private String extractToken(String narrationUrl) {
        int tokenIdx = narrationUrl.indexOf("token=");
        if (tokenIdx < 0) return null;
        String token = narrationUrl.substring(tokenIdx + 6);
        int ampIdx = token.indexOf('&');
        if (ampIdx > 0) token = token.substring(0, ampIdx);
        return token.isBlank() ? null : token;
    }

    private Path tryGlobResolve(Path baseDir, String glob) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(baseDir, glob)) {
            java.util.Iterator<Path> it = stream.iterator();
            if (it.hasNext()) {
                Path p = it.next();
                if (p.startsWith(baseDir)) return p;
            }
        }
        return null;
    }

    private int estimateDuration(long fileSizeBytes) {
        // MiniMax 128kbps MP3: 128000 bits/sec = 16000 bytes/sec
        int estimatedSeconds = (int) (fileSizeBytes / 16000);
        return Math.max(1, estimatedSeconds);
    }

    private SlidePage getSinglePage(Long courseId, Integer pageNumber) {
        LambdaQueryWrapper<SlidePage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SlidePage::getCourseId, courseId)
                .eq(SlidePage::getPageNumber, pageNumber)
                .last("LIMIT 1");
        SlidePage page = slidePageMapper.selectOne(wrapper);
        if (page == null) {
            throw new BusinessException(ErrorCode.SLIDE_PAGE_NOT_FOUND);
        }
        return page;
    }

    @Override
    public void verifyAccess(Long courseId) {
        if (SecurityUtil.isAdmin() || SecurityUtil.hasRole("ACADEMIC")) return;
        Course course = courseRepository.selectById(courseId);
        if (course == null) throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);

        boolean allowed = false;
        if (SecurityUtil.hasRole("TEACHER")
                && course.getTeacherId() != null
                && course.getTeacherId().equals(SecurityUtil.getCurrentUserId())) {
            allowed = true;
        }
        if (!allowed && SecurityUtil.hasRole("STUDENT")) {
            long count = enrollmentRepository.selectCount(
                    new LambdaQueryWrapper<Enrollment>()
                            .eq(Enrollment::getUserId, SecurityUtil.getCurrentUserId())
                            .eq(Enrollment::getCourseId, courseId)
                            .ne(Enrollment::getEnrollmentStatus, EnrollmentStatus.CANCELLED.getValue()));
            if (count > 0) allowed = true;
        }
        if (!allowed) throw new BusinessException(ErrorCode.NO_PERMISSION);
    }

    private void checkOwner(Long courseId) {
        Course course = courseRepository.selectById(courseId);
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        if (!SecurityUtil.isOwnerOrAdmin(course.getTeacherId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
    }

    /**
     * P1-I-01: 批量失败时将课程所有页面标记为原始状态，让前端轮询可见。
     * 注意：只重置 AUDIO_GENERATING（生成中）的页面，保留 AUDIO_READY（已完成）的已有音频。
     */
    private void markAllPagesError(Long courseId, String errorMessage) {
        try {
            transactionTemplate.execute(tx -> {
                LambdaQueryWrapper<SlidePage> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(SlidePage::getCourseId, courseId);
                List<SlidePage> pages = slidePageMapper.selectList(wrapper);
                int resetCount = 0;
                for (SlidePage page : pages) {
                    if ("AUDIO_GENERATING".equals(page.getNarrationStatus())) {
                        page.setNarrationStatus("TEACHER_EDITED");
                        page.setUpdatedAt(LocalDateTime.now());
                        slidePageMapper.updateById(page);
                        resetCount++;
                    }
                }
                log.warn("[Tts] 批量音频生成失败已标记 courseId={}, reset={}/{} total, error={}", courseId, resetCount, pages.size(), errorMessage);
                return null;
            });
        } catch (Exception e) {
            log.error("[Tts] 标记页面音频错误状态失败 courseId={}", courseId, e);
        }
    }

    private SlidePageVO toPageVO(SlidePage page) {
        SlidePageVO vo = new SlidePageVO();
        vo.setId(page.getId());
        vo.setSlideId(page.getSlideId());
        vo.setCourseId(page.getCourseId());
        vo.setNarrationScript(page.getNarrationScript());
        vo.setNarrationAudioUrl(page.getNarrationAudioUrl());
        vo.setAudioDuration(page.getAudioDuration());
        vo.setNarrationStatus(page.getNarrationStatus());
        vo.setNarrationStatusText(SlidePageVO.narrationStatusText(page.getNarrationStatus()));
        vo.setSegmentCount(page.getSegmentCount());
        vo.setVoice(page.getVoice());
        vo.setTtsModel(page.getTtsModel());
        vo.setGeneratedAt(page.getGeneratedAt());
        vo.setUpdatedAt(page.getUpdatedAt());
        return vo;
    }
}
