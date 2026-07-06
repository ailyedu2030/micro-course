package com.microcourse.controller;

import com.microcourse.audit.AuditedLog;
import com.microcourse.dto.LearningProgressVO;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.ProgressCreateRequest;
import com.microcourse.dto.R;
import com.microcourse.dto.VideoCreateRequest;
import com.microcourse.dto.VideoUpdateRequest;
import com.microcourse.dto.VideoVO;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.service.LearningProgressService;
import com.microcourse.service.VideoAccessService;
import com.microcourse.service.VideoService;
import com.microcourse.util.SecurityUtil;
import com.microcourse.util.VideoSignUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import org.hibernate.validator.constraints.Range;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/videos")
@Validated
public class VideoController {

    private static final Logger log = LoggerFactory.getLogger(VideoController.class);

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
     */
    @PostMapping("/{id}/cover")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<String> uploadCover(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "文件不能为空");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "封面大小不能超过5MB");
        }
        String contentType = file.getContentType();
        if (!Arrays.asList("image/jpeg", "image/png").contains(contentType)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "封面仅支持 JPG/PNG");
        }
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
                                                      @RequestBody(required = false) Map<String, Object> body) {
        Long userId = SecurityUtil.getCurrentUserId();
        VideoVO video = videoService.getById(id);
        ProgressCreateRequest req = new ProgressCreateRequest();
        req.setUserId(userId);
        req.setCourseId(video.getCourseId());
        req.setChapterId(video.getChapterId());
        if (body != null) {
            req.setVideoProgress(asInt(body.get("videoProgress")));
            req.setVideoPosition(asInt(body.get("videoPosition")));
            req.setTotalWatchTime(asInt(body.get("totalWatchTime")));
        }
        return R.ok(learningProgressService.create(req));
    }

    /** 宽松解析整数（兼容 Number / 字符串数字） */
    private static Integer asInt(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number) {
            return ((Number) o).intValue();
        }
        try {
            return Integer.parseInt(o.toString().trim());
        } catch (NumberFormatException e) {
            log.warn("数字解析失败: {}", e.getMessage());
            return null;
        }
    }
}
