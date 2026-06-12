package com.microcourse.controller;

import com.microcourse.dto.VideoCreateRequest;
import com.microcourse.dto.VideoUpdateRequest;
import com.microcourse.dto.VideoVO;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.R;
import com.microcourse.entity.Video;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;

import com.microcourse.service.VideoService;
import com.microcourse.service.VideoTranscodeService;
import com.microcourse.util.VideoSignUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/videos")
public class VideoController {

    private static final long MAX_FILE_SIZE = 2L * 1024 * 1024 * 1024; // 2GB

    private final VideoService videoService;
    private final VideoTranscodeService videoTranscodeService;
    private final VideoSignUtil videoSignUtil;

    public VideoController(VideoService videoService,
                          VideoTranscodeService videoTranscodeService,
                          VideoSignUtil videoSignUtil) {
        this.videoService = videoService;
        this.videoTranscodeService = videoTranscodeService;
        this.videoSignUtil = videoSignUtil;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public R<PageResult<VideoVO>> page(
            @RequestParam(required = false) Long courseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (courseId == null) {
            return R.ok(PageResult.of(List.of(), 0L, page, size));
        }
        PageResult<VideoVO> result = videoService.page(courseId, page, size);
        return R.ok(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<VideoVO> getById(@PathVariable Long id) {
        VideoVO vo = videoService.getById(id);
        return R.ok(vo);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','ACADEMIC')")
    public R<VideoVO> create(@Valid @RequestBody VideoCreateRequest request) {
        VideoVO vo = videoService.create(request);
        return R.ok(vo);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','ACADEMIC')")
    public R<VideoVO> update(@PathVariable Long id,
                             @Valid @RequestBody VideoUpdateRequest request) {
        VideoVO vo = videoService.update(id, request);
        return R.ok(vo);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','ACADEMIC')")
    public R<Void> delete(@PathVariable Long id) {
        videoService.delete(id);
        return R.ok();
    }

    /**
     * 视频文件上传
     * 验证 contentType 以 video/ 开头，大小 ≤ 2GB
     * 存储到 /data/videos/{courseId}/{videoId}.mp4
     * 立即返回 Video 记录，文件传输与转码异步进行
     */
    @PostMapping("/upload")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','ACADEMIC')")
    public R<VideoVO> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("courseId") Long courseId,
            @RequestParam(value = "chapterId", required = false) Long chapterId) {

        // 验证文件非空
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "上传文件不能为空");
        }

        // 验证 contentType
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("video/")) {
            throw new BusinessException(ErrorCode.VIDEO_UPLOAD_INVALID_FORMAT);
        }

        // 验证大小
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.VIDEO_UPLOAD_TOO_LARGE);
        }

        // 生成唯一文件名
        String originalFilename = file.getOriginalFilename();
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String savedFileName = uuid + "_" + (originalFilename != null ? originalFilename : "video.mp4");

        // 存储路径：/data/videos/{courseId}/
        String baseDir = "/data/videos/" + courseId;
        String tempFileName = uuid + ".mp4";

        try {
            Files.createDirectories(Paths.get(baseDir));
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "无法创建存储目录");
        }

        Path targetPath = Paths.get(baseDir, tempFileName);

        // 创建 Video 记录（状态：UPLOADING）
        Video video = new Video();
        video.setChapterId(chapterId);
        video.setCourseId(courseId);
        video.setTitle(originalFilename != null ? originalFilename : "未命名视频");
        video.setFileName(savedFileName);
        video.setFileSize(file.getSize());
        video.setMimeType(contentType);
        video.setUrl(targetPath.toString());
        video.setOriginalPath(targetPath.toString());
        video.setStatus(0); // UPLOADING
        video.setProgress(0);
        video.setCreatedAt(LocalDateTime.now());
        video.setUpdatedAt(LocalDateTime.now());
        video.setVersion(0);

        videoService.createEntity(video);

        // 异步执行文件传输与转码，不阻塞当前线程
        final Long videoId = video.getId();
        final MultipartFile uploadedFile = file;
        final Path destPath = targetPath;
        CompletableFuture.runAsync(() -> {
            try {
                uploadedFile.transferTo(destPath.toFile());
                videoTranscodeService.transcode(videoId);
            } catch (IOException e) {
                // 记录错误但不影响主流程
            }
        });

        // 立即返回 Video 记录
        return R.ok(videoService.getById(video.getId()));
    }

    /**
     * 视频播放代理
     * 验证 sign 有效后 302 重定向到 HLS 地址
     */
    @GetMapping("/{id}/sign")
    @PreAuthorize("isAuthenticated()")
    public R<String> generateSign(@PathVariable @PositiveOrZero Long id) {
        String sign = videoSignUtil.generateSign(id, 2);
        return R.ok(sign);
    }

    @GetMapping("/{id}/play")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> play(@PathVariable Long id,
                                      @RequestParam("sign") String sign) {
        Video video = videoService.findEntityById(id);
        if (video == null) {
            throw new BusinessException(ErrorCode.VIDEO_NOT_FOUND);
        }

        // 验证签名
        if (sign == null || !videoSignUtil.verifySign(id, sign)) {
            throw new BusinessException(ErrorCode.VIDEO_SIGN_INVALID);
        }

        String hlsPath = video.getHlsUrl();
        if (hlsPath == null || hlsPath.isBlank()) {
            throw new BusinessException(ErrorCode.VIDEO_TRANSCODE_FAILED, "视频转码尚未完成");
        }

        // 302 重定向到 HLS 路径
        return ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", hlsPath)
                .build();
    }
}