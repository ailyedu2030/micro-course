package com.microcourse.controller;

import com.microcourse.audit.AuditedLog;
import com.microcourse.dto.R;
import com.microcourse.dto.VideoCreateRequest;
import com.microcourse.dto.VideoUpdateRequest;
import com.microcourse.dto.VideoVO;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.LearningProgressVO;
import com.microcourse.dto.ProgressCreateRequest;
import com.microcourse.entity.Video;
import com.microcourse.entity.VideoStatus;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.service.LearningProgressService;
import com.microcourse.service.VideoService;
import com.microcourse.service.VideoTranscodeService;
import com.microcourse.service.VideoAccessService;
import com.microcourse.util.SecurityUtil;
import com.microcourse.util.VideoSignUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import org.hibernate.validator.constraints.Range;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/videos")
@Validated
public class VideoController {

    private static final Logger log = LoggerFactory.getLogger(VideoController.class);

    private static final long MAX_FILE_SIZE = 2L * 1024 * 1024 * 1024; // 2GB

    private final VideoService videoService;
    private final VideoTranscodeService videoTranscodeService;
    private final VideoSignUtil videoSignUtil;
    private final Executor videoUploadExecutor;
    private final LearningProgressService learningProgressService;
    private final VideoAccessService videoAccessService;

    /** P1-1: 上传目录从配置注入 */
    @Value("${video.upload-dir:uploads/videos}")
    private String uploadDir;

    public VideoController(VideoService videoService,
                          VideoTranscodeService videoTranscodeService,
                          VideoSignUtil videoSignUtil,
                          @org.springframework.beans.factory.annotation.Qualifier("videoUploadExecutor")
                          Executor videoUploadExecutor,
                          LearningProgressService learningProgressService,
                          VideoAccessService videoAccessService) {
        this.videoService = videoService;
        this.videoTranscodeService = videoTranscodeService;
        this.videoSignUtil = videoSignUtil;
        this.videoUploadExecutor = videoUploadExecutor;
        this.learningProgressService = learningProgressService;
        this.videoAccessService = videoAccessService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public R<PageResult<VideoVO>> page(
            @RequestParam(required = false) Long courseId,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Range(min = 1, max = 100) int size) {
        if (courseId == null) {
            return R.ok(PageResult.of(List.of(), 0L, page, size));
        }
        PageResult<VideoVO> result = videoService.page(courseId, page, size);
        return R.ok(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<VideoVO> getById(@PathVariable Long id) {
        // ★ Round 8-1 修复：选课校验（仅 STUDENT；ADMIN/TEACHER/ACADEMIC 放行）
        assertStudentEnrolled(id);
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

        // P0-2: 校验课程 Owner 权限
        videoService.assertCourseOwnership(courseId);

        // P1-6: 校验 chapterId 归属课程
        if (chapterId != null) {
            videoService.assertChapterBelongsToCourse(chapterId, courseId);
        }

        // 校验文件类型、大小（魔数校验在此处完成）
        validateVideoFile(file);

        String originalFilename = file.getOriginalFilename();
        String uuid = UUID.randomUUID().toString().replace("-", "");

        // P1-1: 使用配置的上传目录
        String baseDir = uploadDir + "/" + courseId;
        String tempFileName = uuid + ".mp4";

        try {
            Files.createDirectories(Paths.get(baseDir));
        } catch (IOException e) {
            log.error("[VideoUpload] 无法创建存储目录 baseDir={}", baseDir, e);
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "无法创建存储目录");
        }

        Path targetPath = Paths.get(baseDir, tempFileName).toAbsolutePath();

        // 客户体验修复 v1.7.0: 用 Files.copy(inputStream, targetPath) 替代 file.transferTo()
        // 旧实现用 transferTo 在 Spring Boot JAR 模式下会把文件保存到 Tomcat work dir (/private/var/...)
        // 而不是配置的 uploadDir,造成"文件保存失败"500 错误,教师无法上传视频
        try {
            // 确保父目录存在 (绝对路径)
            Path parent = targetPath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            try (java.io.InputStream in = file.getInputStream()) {
                Files.copy(in, targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("[VideoUpload] 文件保存失败 courseId={}, filename={}, target={}", courseId, originalFilename, targetPath, e);
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "文件保存失败: " + e.getMessage());
        }

        // P2: 计算 MD5 并检查重复
        String md5 = computeFileMd5(targetPath);
        Video duplicate = videoService.findByMd5(md5);
        if (duplicate != null) {
            log.info("[VideoUpload] 检测到 MD5 重复 md5={} existingVideoId={}", md5, duplicate.getId());
            // 秒传：删除刚保存的文件，返回已有视频
            try { Files.deleteIfExists(targetPath); } catch (IOException e) {
                log.warn("[VideoUpload] 临时文件删除失败 path={}", targetPath, e);
            }
            return R.ok(videoService.getById(duplicate.getId()));
        }

        // 创建 Video 记录
        Video video = new Video();
        video.setChapterId(chapterId);
        video.setCourseId(courseId);
        video.setTitle(originalFilename != null ? originalFilename : "未命名视频");
        video.setFileName(originalFilename != null ? originalFilename : "video.mp4");
        // 客户体验修复 v1.7.0: URL 必须存为 web 路径(浏览器能访问),不是磁盘绝对路径
        // WebMvcConfig 映射 /api/files/** → file:./uploads/,所以 url 应是 /api/files/videos/{courseId}/{filename}
        video.setUrl("/api/files/videos/" + courseId + "/" + tempFileName);
        video.setFileSize(file.getSize());
        video.setFileMd5(md5);
        video.setMimeType(file.getContentType());
        video.setOriginalPath(targetPath.toString());
        video.setStatus(VideoStatus.UPLOADING.getCode());
        video.setProgress(0);
        video.setCreatedAt(LocalDateTime.now());
        video.setUpdatedAt(LocalDateTime.now());
        video.setVersion(0);

        videoService.createEntity(video);

        // P1-4: 文件已同步保存，仅异步转码
        final Long videoId = video.getId();
        CompletableFuture.runAsync(() -> {
            try {
                videoTranscodeService.transcode(videoId);
            } catch (Exception e) {
                log.error("[VideoUpload] 异步转码失败 videoId={}", videoId, e);
                try {
                    videoService.updateStatus(videoId, VideoStatus.FAILED.getCode());
                } catch (Exception ex) {
                    log.error("[VideoUpload] 更新视频状态为 FAILED 也失败 videoId={}", videoId, ex);
                }
            }
        }, videoUploadExecutor);

        return R.ok(videoService.getById(video.getId()));
    }

    /**
     * 校验视频文件扩展名、MIME type、大小和魔数
     * P1-4: 仅读取魔数字节（不消耗整个流），不影响后续 transferTo
     */
    private void validateVideoFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "上传文件不能为空");
        }
        // Round 11-4 安全加固：文件名路径穿越防护（纵深防御）。
        // 物理落盘虽用 UUID 重命名，但 originalFilename 会写入 DB title/file_name 字段，
        // 仍须拒绝含 ".." / 绝对路径 / 反斜杠的恶意文件名，防止路径穿越与日志注入。
        assertSafeFilename(file.getOriginalFilename());
        String originalFilename = file.getOriginalFilename();
        String ext = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            ext = originalFilename.substring(originalFilename.lastIndexOf(".") + 1);
        }
        if (!Set.of("mp4", "mov", "mkv").contains(ext.toLowerCase())) {
            throw new BusinessException(ErrorCode.VIDEO_FORMAT_INVALID);
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("video/")) {
            throw new BusinessException(ErrorCode.VIDEO_FORMAT_INVALID,
                    "MIME type 必须为 video/*,当前为 " + contentType);
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.VIDEO_TOO_LARGE);
        }
        // 文件魔数检测
        try (InputStream is = file.getInputStream()) {
            byte[] magic = new byte[12];
            int read = is.read(magic);
            if (read < 12) {
                throw new BusinessException(ErrorCode.VIDEO_FORMAT_INVALID, "文件过小，无法验证格式");
            }
            boolean validMagic = isMp4Magic(magic) || isMkvMagic(magic);
            if (!validMagic) {
                throw new BusinessException(ErrorCode.VIDEO_FORMAT_INVALID,
                        "文件魔数校验失败，不是有效的 MP4/MOV/MKV 视频");
            }
        } catch (IOException e) {
            log.warn("视频文件读取失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "无法读取上传文件");
        }
    }

    /**
     * Round 11-4 安全加固：文件名路径穿越防护。
     * 拒绝含 ".." / 路径分隔符 / null 字节的恶意文件名，防止路径穿越与日志注入。
     * 合法上传文件名（如 "lecture01.mp4"）不含这些字符，零影响。
     */
    private static void assertSafeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return; // 文件名缺失走 UUID 落盘 + 默认名兜底，非攻击
        }
        if (filename.contains("..") || filename.contains("/")
                || filename.contains("\\") || filename.indexOf('\u0000') >= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "文件名不合法");
        }
    }

    /** MP4/MOV 文件魔数：ftyp box */
    private static boolean isMp4Magic(byte[] b) {
        int boxSize = ((b[0] & 0xff) << 24) | ((b[1] & 0xff) << 16)
                | ((b[2] & 0xff) << 8) | (b[3] & 0xff);
        return boxSize >= 8 && b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p';
    }

    /** MKV/WebM (EBML) 文件魔数：1A 45 DF A3 */
    private static boolean isMkvMagic(byte[] b) {
        return (b[0] & 0xff) == 0x1A && (b[1] & 0xff) == 0x45
                && (b[2] & 0xff) == 0xDF && (b[3] & 0xff) == 0xA3;
    }

    /**
     * P2: 计算文件 MD5
     */
    private String computeFileMd5(Path filePath) {
        try (InputStream is = Files.newInputStream(filePath)) {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buf = new byte[8192];
            int len;
            while ((len = is.read(buf)) != -1) {
                md.update(buf, 0, len);
            }
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder(32);
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("[VideoUpload] MD5 计算失败 path={}", filePath, e);
            return null;
        }
    }

    /**
     * Round 8-1 修复：视频访问选课校验。
     *
     * <p>仅对 STUDENT 角色强制选课校验；ADMIN / ACADEMIC / TEACHER 直接放行，
     * 且<b>不读取视频行</b>——保持对管理员/教师既有行为零退化
     * （既有测试对不存在 ID 的非学生请求依赖此放行）。</p>
     *
     * @param videoId 视频 ID
     * @throws BusinessException VIDEO_NOT_FOUND（视频不存在）/ NOT_ENROLLED（学生未选课）
     */
    private void assertStudentEnrolled(Long videoId) {
        if (!SecurityUtil.hasRole("STUDENT")) {
            return; // 非学生（教师/管理员/教务）放行
        }
        Video video = videoService.findEntityById(videoId);
        if (video == null) {
            throw new BusinessException(ErrorCode.VIDEO_NOT_FOUND);
        }
        VideoAccessService.AccessResult result =
                videoAccessService.checkVideoAccess(SecurityUtil.getCurrentUserId(), video.getCourseId());
        if (!result.allowed) {
            throw new BusinessException(ErrorCode.NOT_ENROLLED, "请先选课后再观看视频");
        }
    }

    /**
     * 视频播放签名生成
     *
     * <p>Round 8-1：生成签名前先做选课校验（学生未选课 → 403 NOT_ENROLLED）。</p>
     */
    @GetMapping("/{id}/sign")
    @PreAuthorize("isAuthenticated()")
    public R<String> generateSign(@PathVariable @PositiveOrZero Long id) {
        // ★ Round 8-1 修复：选课校验（仅 STUDENT；其余角色放行）
        assertStudentEnrolled(id);
        String sign = videoSignUtil.generateSign(id, 2);
        return R.ok(sign);
    }

    /**
     * P0-1 修复：play() 返回的 HLS URL 现在是 /api/videos/stream/ 格式
     * 浏览器可直接访问该 API 端点获取 HLS 内容
     */
    @GetMapping("/{id}/play")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> play(@PathVariable Long id,
                                      @RequestParam("sign") String sign) {
        Video video = videoService.findEntityById(id);
        if (video == null) {
            throw new BusinessException(ErrorCode.VIDEO_NOT_FOUND);
        }

        // ★ Round 8-1 修复：选课校验（仅 STUDENT；先于签名校验，未选课直接 403 NOT_ENROLLED）
        if (SecurityUtil.hasRole("STUDENT")) {
            VideoAccessService.AccessResult access =
                    videoAccessService.checkVideoAccess(SecurityUtil.getCurrentUserId(), video.getCourseId());
            if (!access.allowed) {
                throw new BusinessException(ErrorCode.NOT_ENROLLED, "请先选课后再观看视频");
            }
        }

        if (sign == null || !videoSignUtil.verifySign(id, sign)) {
            throw new BusinessException(ErrorCode.VIDEO_SIGN_INVALID);
        }

        String hlsUrl = video.getHlsUrl();
        if (hlsUrl == null || hlsUrl.isBlank()) {
            throw new BusinessException(ErrorCode.VIDEO_TRANSCODE_FAILED, "视频转码尚未完成");
        }

        // P0-1: hlsUrl 已是 /api/videos/stream/... 格式，可直接重定向
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
        Video video = videoService.findEntityById(id);
        if (video == null) {
            throw new BusinessException(ErrorCode.VIDEO_NOT_FOUND);
        }
        return R.ok(learningProgressService.getByUserAndCourse(userId, video.getCourseId()));
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
        Video video = videoService.findEntityById(id);
        if (video == null) {
            throw new BusinessException(ErrorCode.VIDEO_NOT_FOUND);
        }
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
