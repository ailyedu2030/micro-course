package com.microcourse.controller;

import com.microcourse.service.VideoStreamService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HLS 流旧路径兼容别名。
 *
 * <p>历史版本 VideoTranscodeServiceImpl 将 hlsUrl 存为
 * {@code /api/videos/stream/{courseId}/{videoId}/index.m3u8}，
 * 而实际处理器为 {@code /api/video-stream/...}——旧路径无处理器导致
 * 转码视频播放 404（P0，2026-08-03 真实链路复现）。
 * 本别名保证已入库的旧 hlsUrl 继续可用；新转码已改为标准路径。</p>
 *
 * @see VideoStreamController
 * @see com.microcourse.service.VideoStreamService
 */
@RestController
@RequestMapping("/api/videos/stream")
@Tag(name = "VideoStreamAliasController", description = "HLS 流旧路径兼容别名（/api/videos/stream）")
public class VideoStreamAliasController {

    private final VideoStreamService videoStreamService;

    public VideoStreamAliasController(VideoStreamService videoStreamService) {
        this.videoStreamService = videoStreamService;
    }

    @GetMapping("/{courseId}/{videoId}/{filename}")
    @PreAuthorize("hasAnyRole('STUDENT','TEACHER','ADMIN','ACADEMIC')")
    public ResponseEntity<Resource> stream(
            @PathVariable Long courseId,
            @PathVariable Long videoId,
            @PathVariable String filename,
            @RequestParam(value = "sign", required = false) String sign) {
        return videoStreamService.stream(courseId, videoId, filename, sign);
    }
}
