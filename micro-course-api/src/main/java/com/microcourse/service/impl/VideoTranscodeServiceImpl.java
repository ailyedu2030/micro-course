package com.microcourse.service.impl;

import com.microcourse.entity.Video;
import com.microcourse.entity.VideoStatus;
import com.microcourse.repository.VideoRepository;
import com.microcourse.service.VideoTranscodeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * FFmpeg HLS 转码服务
 *
 * 状态机：UPLOADING(0) → TRANSCODING(1) → COMPLETED(2)/FAILED(3)
 *
 * P1-1/P1-5: 路径从配置读取
 * P1-2: 超时可配置
 * P1-3: 按 FFmpeg Duration 时间比例估算进度
  * P2: failTranscode 复用已有 Video 对象 + 转码失败清理 HLS 分片
  * [DONE] P3: 状态常量 → VideoStatus 枚举 (Phase 8)
 */
@Service
public class VideoTranscodeServiceImpl implements VideoTranscodeService {

    private static final Logger log = LoggerFactory.getLogger(VideoTranscodeServiceImpl.class);

    /** P1-3: 匹配 FFmpeg 输出中的 Duration 行 */
    private static final Pattern DURATION_PATTERN = Pattern.compile(
            "Duration:\\s*(\\d{2}):(\\d{2}):(\\d{2})\\.(\\d{2})");
    private static final Pattern TIME_PATTERN = Pattern.compile(
            "time=(\\d{2}):(\\d{2}):(\\d{2})\\.(\\d{2})");

    private final VideoRepository videoRepository;

    /** P1-1/P1-5: 存储目录从配置注入 */
    @Value("${video.storage-base-dir:/data/videos}")
    private String storageBaseDir;

    /** P1-2: FFmpeg 超时分钟数 */
    @Value("${video.transcode.timeout-minutes:60}")
    private int transcodeTimeoutMinutes;

    public VideoTranscodeServiceImpl(VideoRepository videoRepository) {
        this.videoRepository = videoRepository;
    }

    @Override
    @Async("videoUploadExecutor")
    @Transactional(rollbackFor = Exception.class)
    public void transcode(Long videoId) {
        log.info("[VideoTranscode] 开始转码 videoId={}", videoId);

        Video video = videoRepository.selectById(videoId);
        if (video == null) {
            log.error("[VideoTranscode] 视频不存在 videoId={}", videoId);
            return;
        }

        String originalPath = video.getOriginalPath();
        if (originalPath == null || originalPath.isBlank()) {
            originalPath = video.getUrl();
            if (originalPath == null || originalPath.isBlank()) {
                log.error("[VideoTranscode] originalPath 为空 videoId={}", videoId);
                failTranscode(video, "原始文件路径为空");
                return;
            }
        }

        Path inputPath = Paths.get(originalPath);
        if (!Files.exists(inputPath)) {
            log.error("[VideoTranscode] 原始文件不存在 path={}", originalPath);
            failTranscode(video, "原始文件不存在: " + originalPath);
            return;
        }

        if (!isFfmpegAvailable()) {
            log.error("[VideoTranscode] FFmpeg 不可用 videoId={}", videoId);
            failTranscode(video, "FFmpeg 未安装或不在 PATH 中");
            return;
        }

        // CAS 条件更新防止并发双 ffmpeg
        com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Video> casWrapper =
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
        casWrapper.eq(Video::getId, videoId)
                .eq(Video::getStatus, VideoStatus.UPLOADING.getCode())
                .set(Video::getStatus, VideoStatus.TRANSCODING.getCode())
                .set(Video::getProgress, 0)
                .set(Video::getUpdatedAt, LocalDateTime.now());
        int affected = videoRepository.update(null, casWrapper);
        if (affected == 0) {
            log.warn("[VideoTranscode] videoId={} 已被其他转码任务接管,跳过本次", videoId);
            return;
        }

        Long courseId = video.getCourseId();
        // P1-1/P1-5: 使用配置目录
        String outputDir = storageBaseDir + "/" + courseId + "/" + videoId;
        String outputM3u8 = outputDir + "/index.m3u8";

        try {
            Files.createDirectories(Paths.get(outputDir));

            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg", "-i", originalPath,
                    "-c:v", "copy", "-c:a", "copy",
                    "-start_number", "0",
                    "-hls_time", "10",
                    "-hls_list_size", "0",
                    "-f", "hls", outputM3u8
            );
            pb.redirectErrorStream(true);

            Process process = pb.start();

            // P1-3: 先解析总时长，再按时间比例估算进度
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                int totalDurationSeconds = 0;
                while ((line = reader.readLine()) != null) {
                    // 解析总时长（只匹配第一次）
                    if (totalDurationSeconds == 0) {
                        Matcher dm = DURATION_PATTERN.matcher(line);
                        if (dm.find()) {
                            totalDurationSeconds = Integer.parseInt(dm.group(1)) * 3600
                                    + Integer.parseInt(dm.group(2)) * 60
                                    + Integer.parseInt(dm.group(3));
                        }
                    }

                    // 解析当前进度
                    Matcher tm = TIME_PATTERN.matcher(line);
                    if (tm.find()) {
                        int currentSeconds = Integer.parseInt(tm.group(1)) * 3600
                                + Integer.parseInt(tm.group(2)) * 60
                                + Integer.parseInt(tm.group(3));

                        int estimatedProgress;
                        if (totalDurationSeconds > 0) {
                            // P1-3: 按实际时长比例计算（0-95%，留 5% 给收尾）
                            estimatedProgress = Math.min(95,
                                    (int) ((long) currentSeconds * 95 / totalDurationSeconds));
                        } else {
                            // fallback: 未知总时长，保守估算
                            estimatedProgress = Math.min(90, currentSeconds / 6);
                        }

                        video.setProgress(estimatedProgress);
                        video.setUpdatedAt(LocalDateTime.now());
                        videoRepository.updateById(video);
                    }
                }
            }

            // P1-2: 使用可配置超时
            boolean finished = process.waitFor(transcodeTimeoutMinutes, TimeUnit.MINUTES);
            if (!finished) {
                log.error("[VideoTranscode] FFmpeg 超时 videoId={} timeout={}min",
                        videoId, transcodeTimeoutMinutes);
                process.destroyForcibly();
                failTranscode(video, "转码超时（" + transcodeTimeoutMinutes + "分钟）");
                cleanupHlsFragments(outputDir);
                return;
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                log.error("[VideoTranscode] FFmpeg 转码失败 exitCode={} videoId={}", exitCode, videoId);
                failTranscode(video, "FFmpeg 转码失败，exitCode=" + exitCode);
                cleanupHlsFragments(outputDir);
                return;
            }

            // 转码成功 — P0-1: hlsUrl 存储为可访问的 API 路径
            String hlsApiUrl = "/api/videos/stream/" + courseId + "/" + videoId + "/index.m3u8";
            video.setHlsUrl(hlsApiUrl);
            video.setStatus(VideoStatus.COMPLETED.getCode());
            video.setProgress(100);
            video.setUpdatedAt(LocalDateTime.now());
            videoRepository.updateById(video);
            log.info("[VideoTranscode] 转码完成 videoId={} hlsUrl={}", videoId, hlsApiUrl);

        } catch (Exception e) {
            log.error("[VideoTranscode] 转码异常 videoId={}", videoId, e);
            failTranscode(video, "视频转码失败，请稍后重试或联系管理员");
            cleanupHlsFragments(outputDir);
        }
    }

    private boolean isFfmpegAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-version");
            Process p = pb.start();
            try (InputStream stdout = p.getInputStream();
                 InputStream stderr = p.getErrorStream()) {
                byte[] buf = new byte[4096];
                while (stdout.read(buf) != -1) { /* discard */ }
                while (stderr.read(buf) != -1) { /* discard */ }
            }
            boolean available = p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0;
            p.destroyForcibly();
            return available;
        } catch (Exception e) {
            log.warn("[VideoTranscode] ffmpeg 检查失败", e);
            return false;
        }
    }

    /**
     * P2: failTranscode 复用已有 Video 对象，不再重复查询
     */
    private void failTranscode(Video video, String errorMessage) {
        video.setStatus(VideoStatus.FAILED.getCode());
        video.setErrorMessage(errorMessage);
        video.setUpdatedAt(LocalDateTime.now());
        videoRepository.updateById(video);
        log.error("[VideoTranscode] 转码失败 videoId={} error={}", video.getId(), errorMessage);
    }

    /**
     * P2: 转码失败时清理已生成的 HLS 分片
     */
    private void cleanupHlsFragments(String outputDir) {
        Path dir = Paths.get(outputDir);
        try {
            if (Files.exists(dir)) {
                try (Stream<Path> walk = Files.walk(dir)) {
                    walk.sorted(Comparator.reverseOrder())
                            .forEach(p -> {
                                try {
                                    Files.deleteIfExists(p);
                                } catch (IOException e) {
                                    log.warn("[VideoTranscode] 清理 HLS 分片失败: {}", p, e);
                                }
                            });
                }
            }
        } catch (IOException e) {
            log.warn("[VideoTranscode] 清理 HLS 目录失败: {}", outputDir, e);
        }
    }
}
