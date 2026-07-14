package com.microcourse.controller;

import com.microcourse.audit.AuditedLog;
import com.microcourse.dto.LearningProgressVO;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.ProgressCreateRequest;
import com.microcourse.dto.R;
import com.microcourse.dto.VideoCreateRequest;
import com.microcourse.dto.VideoProgressReportRequest;
import com.microcourse.dto.VideoStatusVO;
import com.microcourse.dto.VideoUpdateRequest;
import com.microcourse.dto.VideoVO;
import com.microcourse.exception.BusinessException;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.microcourse.exception.ErrorCode;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.microcourse.service.LearningProgressService;
import com.microcourse.service.VideoAccessService;
import com.microcourse.service.VideoService;
import com.microcourse.util.SecurityUtil;
import com.microcourse.util.VideoSignUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import org.hibernate.validator.constraints.Range;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/videos")
@Tag(name = "VideoController", description = "VideoController 自动生成 OpenAPI 文档")
@Validated
public class VideoController {

    

    private final VideoService videoService;
    private final VideoSignUtil videoSignUtil;
    private final LearningProgressService learningProgressService;
    private final VideoAccessService videoAccessService;

    public VideoController(VideoService videoService,
                          VideoSignUtil videoSignUtil,
                          LearningProgressService learningProgressService,
                          VideoAccessService videoAccessService) {
        this.videoService = videoService;
        this.videoSignUtil = videoSignUtil;
        this.learningProgressService = learningProgressService;
        this.videoAccessService = videoAccessService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public R<PageResult<VideoVO>> page(
            @RequestParam(required = false) Long courseId,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Range(min = 1, max = 10000) int size) {
        if (courseId == null) {
            return R.ok(PageResult.of(List.of(), 0L, page, size));
        }
        PageResult<VideoVO> result = videoService.page(courseId, page, size);
        return R.ok(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<VideoVO> getById(@PathVariable Long id) {
        videoAccessService.checkStudentAccess(id);
        return R.ok(videoService.getById(id));
    }

    /**
     * GET /api/videos/{id}/status
     * 获取视频转码状态（轻量级轮询接口）
     * 权限：TEACHER（课程创建者）/ ADMIN
     */
    @GetMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<VideoStatusVO> getStatus(@PathVariable Long id) {
        return R.ok(videoService.getStatus(id));
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
     *
     * P0-2 修复：校验课程 Owner 权限
     * P1-4 修复：文件传输同步执行（避免 MultipartFile 临时文件被清理）
     * P1-6 修复：chapterId 校验归属课程
     * P2 修复：MD5 重复上传校验
     */
    @PostMapping("/upload")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @AuditedLog("上传视频")
    public R<VideoVO> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("courseId") Long courseId,
            @RequestParam(value = "chapterId", required = false) Long chapterId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "上传文件不能为空");
        }
        VideoVO vo = videoService.uploadVideo(file, courseId, chapterId);
        return R.ok(vo);
    }

    /**
     * 视频播放签名生成
     *
     * <p>Round 8-1：生成签名前先做选课校验（学生未选课 → 403 NOT_ENROLLED）。</p>
     */
    @GetMapping("/{id}/sign")
    @PreAuthorize("isAuthenticated()")
    public R<String> generateSign(@PathVariable @PositiveOrZero Long id) {
        videoAccessService.checkStudentAccess(id);
        String sign = videoSignUtil.generateSign(id, 2);
        return R.ok(sign);
    }

    /**
     * 获取 HLS 播放 URL 并重定向。
     * 业务逻辑（视频查找、选课校验、签名校验、HLS URL 检查）已移入 VideoService.getHlsPlayUrl()。
     */
    @GetMapping("/{id}/play")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> play(@PathVariable Long id,
                                      @RequestParam("sign") String sign) {
        String hlsUrl = videoService.getHlsPlayUrl(id, sign);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", hlsUrl)
                .build();
    }

    /**
     * 视频封面上传
     * P1-8 修复：图片魔数校验在 VideoServiceImpl.uploadCover() 中执行
     * 【V4 修复】Controller 层文件大小 + contentType 校验下沉到 FileUploadUtil
     */
    @PostMapping("/{id}/cover")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<String> uploadCover(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        com.microcourse.util.FileUploadUtil.assertImage(file, com.microcourse.util.FileUploadUtil.DEFAULT_VIDEO_COVER_MAX_BYTES);
        String coverUrl = videoService.uploadCover(id, file);
        return R.ok(coverUrl);
    }

    /**
     * GET /api/videos/{id}/progress
     * 获取当前学生在该视频所属课程的学习进度（Phase A-4 P0-5 新增）
     * 权限：STUDENT（本人）—— 依据 权限矩阵 v2.0 §2.5 READ_VIDEO_PROGRESS（仅学生本人）。
     * 说明：learning_progress 表以 (user_id, course_id, chapter_id) 为粒度（无 video_id 列），
     *      故按视频所属课程返回本人进度；userId 强制取当前登录用户，天然防 IDOR。
     */
    @GetMapping("/{id}/progress")
    @PreAuthorize("hasRole('STUDENT')")
    public R<List<LearningProgressVO>> getVideoProgress(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        Long courseId = videoService.getCourseIdByVideoId(id);
        return R.ok(learningProgressService.getByUserAndCourse(userId, courseId));
    }

    /**
     * POST /api/videos/{id}/progress
     * 上报当前学生的视频观看进度（断点续播）（Phase A-4 P0-5 新增）
     * 权限：STUDENT（本人）—— 依据 权限矩阵 v2.0 §2.5 UPDATE_VIDEO_PROGRESS（仅学生本人）。
     * 复用既有并发安全的 LearningProgressService.create()；userId/courseId/chapterId 由服务端推导，
     * 客户端无法篡改归属，天然防 IDOR。
     */
    @PostMapping("/{id}/progress")
    @PreAuthorize("hasRole('STUDENT')")
    public R<LearningProgressVO> reportVideoProgress(@PathVariable Long id,
                                                      @RequestBody(required = false) @Valid VideoProgressReportRequest body) {
        // 【V9 修复】Map → DTO, 契约优先
        Long userId = SecurityUtil.getCurrentUserId();
        VideoVO video = videoService.getById(id);
        ProgressCreateRequest req = new ProgressCreateRequest();
        req.setUserId(userId);
        req.setCourseId(video.getCourseId());
        req.setChapterId(video.getChapterId());
        if (body != null) {
            req.setVideoProgress(body.getVideoProgress());
            req.setVideoPosition(body.getVideoPosition());
            req.setTotalWatchTime(body.getTotalWatchTime());
            if (body.getConfidence() != null) {
                req.setConfidence(body.getConfidence().intValue());
            }
            req.setPlaybackSpeed(body.getPlaybackSpeed());
            req.setPlatform(body.getPlatform());
            req.setDeviceId(body.getDeviceId());
        }
        return R.ok(learningProgressService.create(req));
    }

    /**
     * POST /api/videos/{id}/retry
     * 重试失败的转码任务 (权限矩阵 v4.0 §3.5)
     * 权限：TEACHER(创建者) / ADMIN
     */
    @PostMapping("/{id}/retry")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @AuditedLog("视频重试转码")
    public R<VideoVO> retryTranscode(@PathVariable Long id) {
        return R.ok(videoService.retryTranscode(id));
    }

    /**
     * GET /api/videos/{id}/analytics
     * 视频播放分析 (权限矩阵 v4.0 §3.5)
     * 权限：TEACHER(创建者) / ADMIN
     */
    @GetMapping("/{id}/analytics")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<com.microcourse.dto.VideoAnalyticsVO> getAnalytics(@PathVariable Long id) {
        return R.ok(videoService.getAnalytics(id));
    }

    /**
     * POST /api/videos/batch-upload
     * 批量上传视频 (权限矩阵 v4.0 §3.5)
     * 权限：TEACHER(创建者) / ADMIN
     */
    @PostMapping("/batch-upload")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<List<VideoVO>> batchUpload(@RequestParam("files") MultipartFile[] files,
                                        @RequestParam("courseId") Long courseId,
                                        @RequestParam(value = "chapterId", required = false) Long chapterId) {
        return R.ok(videoService.batchUpload(files, courseId, chapterId));
    }

}
