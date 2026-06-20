package com.microcourse.controller;

import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * HLS 视频流代理
 *
 * P0-1 修复：302 重定向到文件系统路径 → 通过 HTTP 端点流式返回 HLS 内容
 * 同时代理 .m3u8 和 .ts 分片文件
 */
@RestController
@RequestMapping("/api/videos/stream")
public class VideoStreamController {

    private static final Logger log = LoggerFactory.getLogger(VideoStreamController.class);

    @Value("${video.storage-base-dir:/data/videos}")
    private String storageBaseDir;

    /**
     * 流式返回 HLS 文件（.m3u8 / .ts）
     *
     * 路径格式：/api/videos/stream/{courseId}/{videoId}/{filename}
     * filename 可以是 index.m3u8 或 segment0.ts 等
     */
    @GetMapping("/{courseId}/{videoId}/{filename}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> stream(
            @PathVariable Long courseId,
            @PathVariable Long videoId,
            @PathVariable String filename) {

        // 安全校验：文件名不能包含路径穿越字符
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "非法文件名");
        }

        // 限制文件扩展名
        String lower = filename.toLowerCase();
        if (!lower.endsWith(".m3u8") && !lower.endsWith(".ts")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "仅支持 .m3u8 和 .ts 文件");
        }

        Path filePath = Paths.get(storageBaseDir, String.valueOf(courseId),
                String.valueOf(videoId), filename);

        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            throw new BusinessException(ErrorCode.VIDEO_NOT_FOUND, "HLS 文件不存在");
        }

        // 确保路径在 storageBaseDir 内，防止穿越
        Path basePath = Paths.get(storageBaseDir).toAbsolutePath().normalize();
        Path resolvedPath = filePath.toAbsolutePath().normalize();
        if (!resolvedPath.startsWith(basePath)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "路径非法");
        }

        String contentType = lower.endsWith(".m3u8")
                ? "application/vnd.apple.mpegurl"
                : "video/mp2t";

        Resource resource = new FileSystemResource(filePath);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .body(resource);
    }
}
