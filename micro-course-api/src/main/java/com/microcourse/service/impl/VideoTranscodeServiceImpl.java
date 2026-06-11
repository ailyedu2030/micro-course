package com.microcourse.service.impl;

import com.microcourse.entity.Video;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.VideoRepository;
import com.microcourse.service.VideoTranscodeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * FFmpeg HLS 转码服务
 *
 * 依据：Phase 8 开发规范
 * 状态机：UPLOADING(0) → TRANSCODING(1) → COMPLETED(2)/FAILED(3)
 */
@Service
public class VideoTranscodeServiceImpl implements VideoTranscodeService {

    private static final Logger log = LoggerFactory.getLogger(VideoTranscodeServiceImpl.class);

    private static final int UPLOADING = 0;
    private static final int TRANSCODING = 1;
    private static final int COMPLETED = 2;
    private static final int FAILED = 3;

    private static final Pattern TIME_PATTERN = Pattern.compile("time=(\\d{2}):(\\d{2}):(\\d{2})\\.(\\d{2})");

    private final VideoRepository videoRepository;

    public VideoTranscodeServiceImpl(VideoRepository videoRepository) {
        this.videoRepository = videoRepository;
    }

    @Override
    @Async
    public void transcode(Long videoId) {
        log.info("[VideoTranscode] 开始转码 videoId={}", videoId);

        Video video = videoRepository.selectById(videoId);
        if (video == null) {
            log.error("[VideoTranscode] 视频不存在 videoId={}", videoId);
            return;
        }

        String originalPath = video.getOriginalPath();
        if (originalPath == null || originalPath.isBlank()) {
            // 兼容：如果 originalPath 为空则尝试使用 url 字段
            originalPath = video.getUrl();
            if (originalPath == null || originalPath.isBlank()) {
                log.error("[VideoTranscode] originalPath 为空 videoId={}", videoId);
                failTranscode(videoId, "原始文件路径为空");
                return;
            }
        }

        Path inputPath = Paths.get(originalPath);
        if (!Files.exists(inputPath)) {
            log.error("[VideoTranscode] 原始文件不存在 path={}", originalPath);
            failTranscode(videoId, "原始文件不存在: " + originalPath);
            return;
        }

        // 检查 ffmpeg 是否可用
        if (!isFfmpegAvailable()) {
            log.error("[VideoTranscode] FFmpeg 不可用，跳过转码 videoId={}", videoId);
            failTranscode(videoId, "FFmpeg 未安装或不在 PATH 中");
            return;
        }

        // 更新状态为 TRANSCODING
        video.setStatus(TRANSCODING);
        video.setProgress(0);
        video.setUpdatedAt(LocalDateTime.now());
        videoRepository.updateById(video);

        Long courseId = video.getCourseId();
        String outputDir = "/data/videos/" + courseId + "/" + videoId;
        String outputM3u8 = outputDir + "/index.m3u8";

        try {
            // 确保输出目录存在
            Files.createDirectories(Paths.get(outputDir));

            // 执行 FFmpeg 转码
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

            // 读取输出并解析进度
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                Integer lastDuration = null;
                while ((line = reader.readLine()) != null) {
                    // 解析进度：time=00:00:10.00
                    Matcher m = TIME_PATTERN.matcher(line);
                    if (m.find()) {
                        int hours = Integer.parseInt(m.group(1));
                        int minutes = Integer.parseInt(m.group(2));
                        int seconds = Integer.parseInt(m.group(3));
                        int currentDuration = hours * 3600 + minutes * 60 + seconds;

                        if (lastDuration != null && currentDuration > lastDuration) {
                            // 估算进度（假设总时长未知，简单按 0-90% 估算，最后等待完成）
                            // 更准确的方式需要解析 ffmpeg 输出获取总时长
                            int estimatedProgress = Math.min(90, currentDuration * 10 / 60);
                            video.setProgress(estimatedProgress);
                            video.setUpdatedAt(LocalDateTime.now());
                            videoRepository.updateById(video);
                        }
                        lastDuration = currentDuration;
                    }
                }
            }

            boolean finished = process.waitFor(30, TimeUnit.MINUTES);
            if (!finished) {
                log.error("[VideoTranscode] FFmpeg 超时 videoId={}", videoId);
                process.destroyForcibly();
                failTranscode(videoId, "转码超时（30分钟）");
                return;
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                log.error("[VideoTranscode] FFmpeg 转码失败 exitCode={} videoId={}", exitCode, videoId);
                failTranscode(videoId, "FFmpeg 转码失败，exitCode=" + exitCode);
                return;
            }

            // 转码成功
            video.setHlsUrl(outputM3u8);
            video.setStatus(COMPLETED);
            video.setProgress(100);
            video.setUpdatedAt(LocalDateTime.now());
            videoRepository.updateById(video);
            log.info("[VideoTranscode] 转码完成 videoId={} hlsPath={}", videoId, outputM3u8);

        } catch (Exception e) {
            log.error("[VideoTranscode] 转码异常 videoId={}", videoId, e);
            failTranscode(videoId, ErrorCode.VIDEO_TRANSCODE_FAILED.getMessage());
        }
    }

    private boolean isFfmpegAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-version");
            Process p = pb.start();
            boolean available = p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0;
            p.destroyForcibly();
            return available;
        } catch (Exception e) {
            return false;
        }
    }

    private void failTranscode(Long videoId, String errorMessage) {
        Video video = videoRepository.selectById(videoId);
        if (video != null) {
            video.setStatus(FAILED);
            video.setErrorMessage(errorMessage);
            video.setUpdatedAt(LocalDateTime.now());
            videoRepository.updateById(video);
        }
        log.error("[VideoTranscode] 转码失败 videoId={} error={}", videoId, errorMessage);
    }
}