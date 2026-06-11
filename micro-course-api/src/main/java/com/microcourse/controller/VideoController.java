package com.microcourse.controller;

import com.microcourse.dto.VideoCreateRequest;
import com.microcourse.dto.VideoUpdateRequest;
import com.microcourse.dto.VideoVO;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.R;
import com.microcourse.entity.Video;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.VideoRepository;
import com.microcourse.service.VideoService;
import com.microcourse.service.VideoTranscodeService;
import com.microcourse.util.VideoSignUtil;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/videos")
public class VideoController {

    private static final long MAX_FILE_SIZE = 2L * 1024 * 1024 * 1024; // 2GB

    private final VideoService videoService;
    private final VideoTranscodeService videoTranscodeService;
    private final VideoSignUtil videoSignUtil;
    private final VideoRepository videoRepository;

    public VideoController(VideoService videoService,
                          VideoTranscodeService videoTranscodeService,
                          VideoSignUtil videoSignUtil,
                          VideoRepository videoRepository) {
        this.videoService = videoService;
        this.videoTranscodeService = videoTranscodeService;
        this.videoSignUtil = videoSignUtil;
        this.videoRepository = videoRepository;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public R<PageResult<VideoVO>> page(
            @RequestParam Long courseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
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
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<VideoVO> create(@Valid @RequestBody VideoCreateRequest request) {
        VideoVO vo = videoService.create(request);
        return R.ok(vo);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<VideoVO> update(@PathVariable Long id,
                             @Valid @RequestBody VideoUpdateRequest request) {
        VideoVO vo = videoService.update(id, request);
        return R.ok(vo);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<Void> delete(@PathVariable Long id) {
        videoService.delete(id);
        return R.ok();
    }

    /**
     * 视频文件上传
     * 验证 contentType 以 video/ 开头，大小 ≤ 2GB
     * 存储到 /data/videos/{courseId}/{videoId}.mp4
     * 写入 Video 表后异步调用转码
     */
    @PostMapping("/upload")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
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

        // 存储路径：/data/videos/{courseId}/{videoId}.mp4
        // 先创建临时路径，等拿到 videoId 后再移动
        String baseDir = "/data/videos/" + courseId;
        String tempFileName = uuid + ".mp4";

        try {
            Files.createDirectories(Paths.get(baseDir));
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "无法创建存储目录");
        }

        Path targetPath = Paths.get(baseDir, tempFileName);

        try {
            file.transferTo(targetPath.toFile());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "文件保存失败: " + e.getMessage());
        }

        // 创建 Video 记录
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

        videoRepository.insert(video);

        // 异步调用转码
        videoTranscodeService.transcode(video.getId());

        return R.ok(videoService.getById(video.getId()));
    }

    /**
     * 视频播放代理
     * 验证 sign 有效后 302 重定向到 HLS 地址
     */
    @GetMapping("/{id}/play")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> play(@PathVariable Long id,
                                      @RequestParam("sign") String sign) {
        Video video = videoRepository.selectById(id);
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