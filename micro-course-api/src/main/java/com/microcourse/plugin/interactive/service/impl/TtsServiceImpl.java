package com.microcourse.plugin.interactive.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.entity.Course;
import com.microcourse.entity.Enrollment;
import com.microcourse.enums.EnrollmentStatus;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.plugin.interactive.dto.SlidePageVO;
import com.microcourse.plugin.interactive.entity.SlidePage;
import com.microcourse.plugin.interactive.mapper.SlidePageMapper;
import com.microcourse.plugin.interactive.service.TtsService;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.util.SecurityUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
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
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Transactional(rollbackFor = Exception.class)
public class TtsServiceImpl implements TtsService {

    private static final Logger log = LoggerFactory.getLogger(TtsServiceImpl.class);

    private final SlidePageMapper slidePageMapper;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final TransactionTemplate transactionTemplate;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${plugin.interactive.minimax.tts-model:speech-2.8-hd}")
    private String ttsModel;

    /** Qwen3-TTS 0.6B 预定义声音 ID（vivian/serena/dylan/ryan/eric/aiden/ono_anna/sohee/uncle_fu） */
    @Value("${plugin.interactive.minimax.tts-voice:vivian}")
    private String ttsVoice;

    @Value("${plugin.interactive.slides.storage-path:/data/slides}")
    private String storagePath;

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

    public TtsServiceImpl(SlidePageMapper slidePageMapper,
                          CourseRepository courseRepository,
                          EnrollmentRepository enrollmentRepository,
                          TransactionTemplate transactionTemplate) {
        this.slidePageMapper = slidePageMapper;
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.transactionTemplate = transactionTemplate;
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

        // 2. 降级到 mmx CLI
        try {
            ProcessBuilder pb = new ProcessBuilder(MMX_CMD, "--version");
            Process process = pb.start();
            try (var stdout = process.getInputStream();
                 var stderr = process.getErrorStream()) {
                // 消费输出流，防止缓冲区满导致进程挂起
                byte[] buffer = new byte[4096];
                while (stdout.read(buffer) != -1) { /* discard */ }
                while (stderr.read(buffer) != -1) { /* discard */ }

                boolean finished = process.waitFor(5, TimeUnit.SECONDS);
                if (finished && process.exitValue() == 0) {
                    mmxAvailable = true;
                    mmxCheckMessage = "可用";
                    log.info("[TTS] mmx CLI 检测通过（降级模式）");
                } else {
                    mmxCheckMessage = "mmx CLI 执行失败或超时";
                    log.warn("[TTS] mmx CLI 不可用，TTS 将降级为纯文本模式");
                }
            } finally {
                process.destroy();
            }
        } catch (IOException e) {
            mmxCheckMessage = "mmx CLI 未安装或不在 PATH 中: " + e.getMessage();
            log.warn("[TTS] mmx CLI 不可用: {}，TTS 将降级为纯文本模式", e.getMessage());
        } catch (Exception e) {
            mmxCheckMessage = "检测 mmx 时异常: " + e.getMessage();
            log.warn("[TTS] 检测 mmx CLI 时发生异常: {}", e.getMessage(), e);
        }
    }

    @Override
    public SlidePageVO generate(Long courseId, Integer pageNumber) {
        checkOwner(courseId);
        SlidePage page = getPage(courseId, pageNumber);
        return doGenerate(courseId, pageNumber, page);
    }

    private SlidePageVO doGenerate(Long courseId, Integer pageNumber, SlidePage page) {
        String script = page.getNarrationScript();
        if (script == null || script.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "讲述稿为空，请先生成讲述稿");
        }

        String audioFileName = "page_" + pageNumber + ".mp3";

        try {
            Path audioDir = Paths.get(storagePath, String.valueOf(courseId), "audio");
            Files.createDirectories(audioDir);
            Path audioPath = audioDir.resolve(audioFileName);

            markPageStatus(courseId, pageNumber, "AUDIO_GENERATING");

            boolean success = false;
            int audioDuration = 0;

            if (ttsLocalAvailable) {
                try {
                    audioDuration = callQwen3TtsService(script, audioPath);
                    success = true;
                } catch (Exception e) {
                    log.warn("[TTS] Qwen3-TTS 调用失败: {}，降级到 mmx", e.getMessage());
                    if (mmxAvailable) {
                        audioDuration = callMmxCli(script, audioPath);
                        success = true;
                    }
                }
            } else if (mmxAvailable) {
                audioDuration = callMmxCli(script, audioPath);
                success = true;
            }

            if (!success) {
                markPageStatus(courseId, pageNumber, "TEACHER_EDITED");
                throw new BusinessException(ErrorCode.TTS_GENERATE_FAILED);
            }

            long fileSize = Files.size(audioPath);
            if (audioDuration <= 0) {
                audioDuration = estimateDuration(fileSize);
            }

            final int finalDuration = audioDuration;
            transactionTemplate.execute(tx -> {
                SlidePage fresh = slidePageMapper.selectById(page.getId());
                if (fresh == null) return null;
                fresh.setNarrationAudioUrl("/api/courses/" + courseId + "/slides/pages/" + pageNumber + "/audio");
                fresh.setAudioDuration(finalDuration);
                fresh.setNarrationStatus("AUDIO_READY");
                fresh.setUpdatedAt(LocalDateTime.now());
                slidePageMapper.updateById(fresh);
                return null;
            });

            log.info("TTS complete: courseId={}, page={}, duration={}s, backend={}",
                    courseId, pageNumber, finalDuration,
                    ttsLocalAvailable ? "qwen3-local" : "mmx");

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("TTS failed for courseId={} page={}", courseId, pageNumber, e);
            try { markPageStatus(courseId, pageNumber, "TEACHER_EDITED"); } catch (Exception ignored) {}
            throw new BusinessException(ErrorCode.TTS_GENERATE_FAILED);
        }

        return toPageVO(page);
    }

    private void markPageStatus(Long courseId, Integer pageNumber, String status) {
        transactionTemplate.execute(tx -> {
            SlidePage p = getPage(courseId, pageNumber);
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
     * 降级方案：调用 mmx CLI（MiniMax）
     */
    private int callMmxCli(String script, Path audioPath) throws Exception {
        Path tempTextFile = Files.createTempFile("narration_", ".txt");
        try {
            Files.writeString(tempTextFile, script);

            ProcessBuilder pb = new ProcessBuilder(
                    MMX_CMD, "speech", "synthesize",
                    "--text-file", tempTextFile.toString(),
                    "--voice", ttsVoice,
                    "--model", ttsModel,
                    "--format", "mp3",
                    "--out", audioPath.toString(),
                    "--quiet"
            );

            Process process = pb.start();
            try (var stdout = process.getInputStream();
                 var stderr = process.getErrorStream()) {
                // 消费 stdout（即使不关心输出），防止缓冲区满导致进程挂起
                byte[] buffer = new byte[4096];
                while (stdout.read(buffer) != -1) { /* discard */ }

                boolean finished = process.waitFor(120, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    throw new IOException("mmx TTS 超时");
                }

                // 进程已结束，读取 stderr 获取错误信息
                String errorOutput = new String(stderr.readAllBytes());
                int exitCode = process.exitValue();
                if (exitCode != 0) {
                    throw new IOException("mmx TTS 失败 exit=" + exitCode + ": " + errorOutput);
                }
                return 0;
            } finally {
                process.destroy();
            }
        } finally {
            try { Files.deleteIfExists(tempTextFile); } catch (IOException ignored) { }
        }
    }

    @Override
    @Async("slideRenderExecutor")
    public void generateAll(Long courseId) {
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
    public byte[] getAudio(Long courseId, Integer pageNumber) {
        try {
            Path basePath = Paths.get(storagePath, String.valueOf(courseId), "audio").toRealPath();
            Path audioPath = basePath.resolve("page_" + pageNumber + ".mp3").normalize();
            if (!audioPath.startsWith(basePath)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "非法的音频路径");
            }
            return Files.readAllBytes(audioPath);
        } catch (NoSuchFileException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "音频文件不存在: " + pageNumber);
        } catch (IOException e) {
            log.error("[Tts] 读取音频文件失败 courseId={} page={}", courseId, pageNumber, e);
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "音频文件读取失败");
        }
    }

    private int estimateDuration(long fileSizeBytes) {
        int estimatedSeconds = (int) (fileSizeBytes / 2000);
        return Math.max(1, estimatedSeconds);
    }

    private SlidePage getPage(Long courseId, Integer pageNumber) {
        LambdaQueryWrapper<SlidePage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SlidePage::getCourseId, courseId)
                .eq(SlidePage::getPageNumber, pageNumber);
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
        vo.setUpdatedAt(page.getUpdatedAt());
        return vo;
    }
}
