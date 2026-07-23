package com.microcourse.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.microcourse.dto.VideoCreateRequest;
import com.microcourse.dto.VideoStatusVO;
import com.microcourse.dto.VideoUpdateRequest;
import com.microcourse.dto.VideoVO;
import com.microcourse.dto.PageResult;
import com.microcourse.entity.Course;
import com.microcourse.entity.CourseChapter;
import com.microcourse.entity.Video;
import com.microcourse.entity.VideoBookmark;
import com.microcourse.enums.VideoStatus;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CourseChapterRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.LearningProgressRepository;
import com.microcourse.repository.VideoBookmarkRepository;
import com.microcourse.repository.VideoRepository;
import com.microcourse.service.AdminSettingService;
import com.microcourse.service.VideoAccessService;
import com.microcourse.service.VideoService;
import com.microcourse.service.VideoTranscodeService;
import com.microcourse.util.RedisUtil;
import com.microcourse.util.SecurityUtil;
import com.microcourse.util.VideoDiskCleanup;
import com.microcourse.util.VideoSignUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
@Service
public class VideoServiceImpl implements VideoService {

    private static final Logger log = LoggerFactory.getLogger(VideoServiceImpl.class);

    private final VideoRepository videoRepository;
    private final CourseChapterRepository chapterRepository;
    private final CourseRepository courseRepository;
    private final VideoBookmarkRepository videoBookmarkRepository;
    private final VideoTranscodeService videoTranscodeService;
    private final VideoAccessService videoAccessService;
    private final VideoSignUtil videoSignUtil;
    private final AdminSettingService adminSettingService;
    private final RedisUtil redisUtil;
    private final LearningProgressRepository learningProgressRepository;

    /** P1-1: 从配置读取存储目录 */
    @Value("${video.storage-base-dir:/data/videos}")
    private String storageBaseDir;

    @Value("${video.cover-dir:uploads/covers}")
    private String coverDir;

    @Value("${video.storage-base-dir:uploads/videos}")
    private String uploadDir;

    public VideoServiceImpl(VideoRepository videoRepository,
                            CourseChapterRepository chapterRepository,
                            CourseRepository courseRepository,
                            VideoBookmarkRepository videoBookmarkRepository,
                            VideoTranscodeService videoTranscodeService,
                            VideoAccessService videoAccessService,
                            VideoSignUtil videoSignUtil,
                            AdminSettingService adminSettingService,
                            RedisUtil redisUtil,
                            LearningProgressRepository learningProgressRepository) {
        this.videoRepository = videoRepository;
        this.chapterRepository = chapterRepository;
        this.courseRepository = courseRepository;
        this.videoBookmarkRepository = videoBookmarkRepository;
        this.videoTranscodeService = videoTranscodeService;
        this.learningProgressRepository = learningProgressRepository;
        this.videoAccessService = videoAccessService;
        this.videoSignUtil = videoSignUtil;
        this.adminSettingService = adminSettingService;
        this.redisUtil = redisUtil;
    }

    private long getMaxFileSize() {
        String v = adminSettingService.getByKey("max_video_size_mb");
        if (v != null && !v.isBlank()) {
            try { return Long.parseLong(v) * 1024L * 1024L; } catch (NumberFormatException ignore) {}
        }
        return 2L * 1024L * 1024L * 1024L; // 默认 2GB
    }

    @Override
    public PageResult<VideoVO> page(Long courseId, int page, int size) {
        LambdaQueryWrapper<Video> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Video::getCourseId, courseId)
                .orderByAsc(Video::getSortOrder)
                .orderByDesc(Video::getCreatedAt);

        IPage<Video> ipage = videoRepository.selectPage(
                new Page<>(page + 1, size), wrapper);

        // 批量预加载 course（P2: 避免 N+1）
        Map<Long, Course> courseMap = new HashMap<>();
        Set<Long> courseIds = ipage.getRecords().stream()
                .map(Video::getCourseId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (!courseIds.isEmpty()) {
            courseRepository.selectBatchIds(courseIds).forEach(c -> courseMap.put(c.getId(), c));
        }

        // 批量预加载 chapter（P2: 避免 N+1）
        Map<Long, CourseChapter> chapterMap = new HashMap<>();
        Set<Long> chapterIds = ipage.getRecords().stream()
                .map(Video::getChapterId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (!chapterIds.isEmpty()) {
            chapterRepository.selectBatchIds(chapterIds).forEach(ch -> chapterMap.put(ch.getId(), ch));
        }

        PageResult<VideoVO> result = new PageResult<>();
        result.setItems(ipage.getRecords().stream()
                .map(v -> convertToVO(v, courseMap, chapterMap)).toList());
        result.setPage(page);
        result.setSize(size);
        result.setTotalElements(ipage.getTotal());
        result.setTotalPages(ipage.getPages());
        return result;
    }

    @Override
    public VideoVO getById(Long id) {
        Video video = videoRepository.selectById(id);
        if (video == null) {
            throw new BusinessException(ErrorCode.VIDEO_NOT_FOUND);
        }
        // P2: 单条查询也用 Map 复用 convertToVO，避免重复代码
        Map<Long, Course> courseMap = new HashMap<>();
        if (video.getCourseId() != null) {
            Course course = courseRepository.selectById(video.getCourseId());
            if (course != null) {
                courseMap.put(course.getId(), course);
            }
        }
        Map<Long, CourseChapter> chapterMap = new HashMap<>();
        if (video.getChapterId() != null) {
            CourseChapter chapter = chapterRepository.selectById(video.getChapterId());
            if (chapter != null) {
                chapterMap.put(chapter.getId(), chapter);
            }
        }
        return convertToVO(video, courseMap, chapterMap);
    }

    @Override
    /** @deprecated 返回 Entity 对象，仅限内部使用。外部调用请用 Service VO 方法。 */
    @Deprecated
    public Video findEntityById(Long id) {
        return videoRepository.selectById(id);
    }

    @Override
    public Video createEntity(Video video) {
        videoRepository.insert(video);
        return video;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VideoVO create(VideoCreateRequest request) {
        // Validate course exists
        Course course = courseRepository.selectById(request.getCourseId());
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        // Owner check: only course teacher or ADMIN can create video
        assertCourseOwner(course);

        // Validate chapter exists and belongs to course（P1-6）
        if (request.getChapterId() != null) {
            assertChapterBelongsToCourse(request.getChapterId(), request.getCourseId());
        }

        Video video = new Video();
        video.setChapterId(request.getChapterId());
        video.setCourseId(request.getCourseId());
        video.setTitle(request.getTitle());
        video.setFileName(request.getFileName());
        video.setFileSize(request.getFileSize());
        video.setDuration(request.getDuration());
        video.setSortOrder(request.getSortOrder());
        video.setStatus(VideoStatus.UPLOADING.getCode());
        video.setProgress(0);
        video.setCreatedAt(LocalDateTime.now());
        video.setUpdatedAt(LocalDateTime.now());
        video.setVersion(0);

        videoRepository.insert(video);

        Map<Long, Course> courseMap = Map.of(course.getId(), course);
        return convertToVO(video, courseMap, new HashMap<>());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VideoVO update(Long id, VideoUpdateRequest request) {
        Video video = videoRepository.selectById(id);
        if (video == null) {
            throw new BusinessException(ErrorCode.VIDEO_NOT_FOUND);
        }
        // Owner check
        assertCourseOwnership(video.getCourseId());

        // Partial update
        if (request.getTitle() != null) {
            video.setTitle(request.getTitle());
        }
        if (request.getSortOrder() != null) {
            video.setSortOrder(request.getSortOrder());
        }
        if (request.getStatus() != null) {
            video.setStatus(request.getStatus());
        }
        if (request.getChapterId() != null) {
            assertChapterBelongsToCourse(request.getChapterId(), video.getCourseId());
            video.setChapterId(request.getChapterId());
        }

        video.setUpdatedAt(LocalDateTime.now());
        // P2: @Version 由 MyBatis-Plus 乐观锁插件自动处理
        videoRepository.updateById(video);
        return getById(id);
    }

    /**
     * P0-4 修复：delete() 同时清理磁盘文件（视频目录 + 封面文件）。
     *
     * C2-2 修复：DB 删除立即提交后，磁盘清理在事务外执行，
     * 避免 Files.walk 阻塞 DB 连接/行锁，降低事务持有时间。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Video video = videoRepository.selectById(id);
        if (video == null) {
            throw new BusinessException(ErrorCode.VIDEO_NOT_FOUND);
        }
        // Owner check
        assertCourseOwnership(video.getCourseId());

        // 级联清理书签
        videoBookmarkRepository.delete(new LambdaQueryWrapper<VideoBookmark>()
                .eq(VideoBookmark::getVideoId, id));

        // 删除数据库记录（事务内）
        videoRepository.deleteById(id);

        // C2-2: 注册 afterCommit 回调，事务提交后再清理磁盘文件
        scheduleDiskCleanup(video);

        // 注意：磁盘清理通过 cleanupDiskFiles 在事务提交后执行。
        // 由于 deleteById 是逻辑删除（@TableLogic），DB 操作极快，
        // 事务提交后立即释放连接；磁盘清理不占用 DB 资源。
        // 如果磁盘清理失败，仅记录日志，不影响 DB 删除结果。
    }

    /**
     * C2-2 修复：事务提交后执行磁盘清理。
     * 通过 TransactionSynchronizationManager 注册 afterCommit 回调，
     * 确保 DB 删除已提交后才开始文件清理。
     */
    private void scheduleDiskCleanup(Video video) {
        org.springframework.transaction.support.TransactionSynchronizationManager
                .registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        VideoDiskCleanup.cleanup(video, storageBaseDir, coverDir);
                    }
                });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long videoId, int status) {
        LambdaQueryWrapper<Video> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Video::getId, videoId);
        Video v = videoRepository.selectOne(wrapper);
        if (v == null) {
            throw new BusinessException(ErrorCode.VIDEO_NOT_FOUND);
        }

        int affected = videoRepository.update(null,
            new LambdaUpdateWrapper<Video>()
                .eq(Video::getId, videoId)
                .eq(Video::getStatus, v.getStatus())
                .set(Video::getStatus, status)
                .set(Video::getUpdatedAt, LocalDateTime.now())
                .setSql("version = version + 1"));
        if (affected == 0) {
            throw new BusinessException(ErrorCode.MS_CONCURRENT_MODIFICATION, "视频状态已被修改，请刷新");
        }
    }

    /**
     * 重试失败的转码任务 (权限矩阵 v4.0 §3.5 RETRY_TRANSCODE)
     * 仅 FAILED(3) 状态的视频可重试, 重置为 TRANSCODING(1) 并清空错误信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public VideoVO retryTranscode(Long id) {
        Video v = videoRepository.selectById(id);
        if (v == null) {
            throw new BusinessException(ErrorCode.VIDEO_NOT_FOUND);
        }
        // owner 校验
        assertCourseOwnership(v.getCourseId());

        if (v.getStatus() == null || v.getStatus() != VideoStatus.FAILED.getCode()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM,
                    "仅 FAILED 状态的视频可重试转码, 当前状态: " + v.getStatus());
        }

        int affected = videoRepository.update(null,
                new LambdaUpdateWrapper<Video>()
                        .eq(Video::getId, id)
                        .eq(Video::getStatus, VideoStatus.FAILED.getCode())
                        .set(Video::getStatus, VideoStatus.TRANSCODING.getCode())
                        .set(Video::getErrorMessage, (String) null)
                        .set(Video::getProgress, 0)
                        .set(Video::getUpdatedAt, LocalDateTime.now())
                        .setSql("version = version + 1"));
        if (affected == 0) {
            throw new BusinessException(ErrorCode.MS_CONCURRENT_MODIFICATION, "视频状态已被修改，请刷新");
        }

        scheduleTranscodeAfterCommit(id);
        log.info("[RetryTranscode] 视频重新进入转码: id={}", id);
        return getById(id);
    }

    /**
     * 视频播放分析 (权限矩阵 v4.0 §3.5 GET_VIDEO_ANALYTICS)
     */
    @Override
    public com.microcourse.dto.VideoAnalyticsVO getAnalytics(Long id) {
        Video v = videoRepository.selectById(id);
        if (v == null) {
            throw new BusinessException(ErrorCode.VIDEO_NOT_FOUND);
        }
        assertCourseOwnership(v.getCourseId());

        com.microcourse.dto.VideoAnalyticsVO vo = new com.microcourse.dto.VideoAnalyticsVO();
        vo.setVideoId(v.getId());
        vo.setVideoTitle(v.getTitle());
        vo.setTotalDuration(v.getDuration());

        // 学习进度聚合: LearningProgress 通过 chapterId 关联视频 (一个 chapter 对应一个视频)
        Long chapterId = v.getChapterId();
        Long uniqueViewers = chapterId != null ? learningProgressRepository.countUniqueViewersByChapterId(chapterId) : 0L;
        Long playCount = chapterId != null ? learningProgressRepository.countByChapterId(chapterId) : 0L;
        Double avgWatchSeconds = chapterId != null ? learningProgressRepository.avgWatchSecondsByChapterId(chapterId) : 0.0;
        Long completedCount = chapterId != null ? learningProgressRepository.countCompletedByChapterId(chapterId) : 0L;

        vo.setUniqueViewers(uniqueViewers != null ? uniqueViewers : 0L);
        vo.setPlayCount(playCount != null ? playCount : 0L);
        vo.setAvgWatchSeconds(avgWatchSeconds != null ? avgWatchSeconds.intValue() : 0);
        if (uniqueViewers != null && uniqueViewers > 0 && completedCount != null) {
            vo.setCompletionRate(java.math.BigDecimal.valueOf(completedCount)
                    .divide(java.math.BigDecimal.valueOf(uniqueViewers), 4,
                            java.math.RoundingMode.HALF_UP));
        } else {
            vo.setCompletionRate(java.math.BigDecimal.ZERO);
        }
        return vo;
    }

    /**
     * 批量上传视频 (权限矩阵 v4.0 §3.5 BATCH_UPLOAD_VIDEO)
     * 复用单文件上传逻辑, 任一文件失败不中断其他文件
     */
    @Override
    public java.util.List<VideoVO> batchUpload(org.springframework.web.multipart.MultipartFile[] files,
                                                Long courseId, Long chapterId) {
        if (files == null || files.length == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "上传文件不能为空");
        }
        assertCourseOwnership(courseId);
        java.util.List<VideoVO> results = new java.util.ArrayList<>();
        for (org.springframework.web.multipart.MultipartFile file : files) {
            try {
                VideoVO vo = uploadVideo(file, courseId, chapterId);
                results.add(vo);
            } catch (Exception e) {
                log.error("[BatchUpload] 文件上传失败: filename={}, err={}",
                        file.getOriginalFilename(), e.getMessage());
                // 单文件失败不阻塞其他文件, 但需要返回失败信息
                VideoVO failed = new VideoVO();
                failed.setTitle(file.getOriginalFilename());
                failed.setErrorMessage(e.getMessage());
                results.add(failed);
            }
        }
        return results;
    }
    /**
     * P0-3 修复：封面 URL 改为可访问的 API 路径
     * P1-8 修复：封面文件魔数校验
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String uploadCover(Long videoId, MultipartFile file) {
        // ★ Round 9-3 修复：空文件防御（Service 层兜底，防止保存 0 字节图片 → 友好 400）
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "封面文件不能为空");
        }
        Video video = videoRepository.selectById(videoId);
        if (video == null) {
            throw new BusinessException(ErrorCode.VIDEO_NOT_FOUND);
        }

        // SECURITY: 只有课程教师或 ADMIN 可上传封面
        assertCourseOwnership(video.getCourseId());

        // P1-8: 图片魔数校验
        validateImageMagic(file);

        // 保存到 {coverDir}/{videoId}/
        String baseDir = coverDir + "/" + videoId;
        String originalFilename = file.getOriginalFilename();
        // Round 11-4 安全修复：封面文件名路径穿越防护。
        // 原实现 ext 取自 originalFilename.substring(lastIndexOf(".")),恶意名如
        // "x.jpg/../../evil" 会污染 Paths.get 落盘路径造成路径穿越,此处显式拒绝。
        if (originalFilename != null
                && (originalFilename.contains("..") || originalFilename.contains("/")
                    || originalFilename.contains("\\") || originalFilename.indexOf('\u0000') >= 0)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "封面文件名不合法");
        }
        String ext = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            ext = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String savedFileName = java.util.UUID.randomUUID().toString().replace("-", "") + ext;
        Path targetPath = Paths.get(baseDir, savedFileName);

        try {
            Files.createDirectories(targetPath.getParent());
            file.transferTo(targetPath.toFile());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "封面保存失败");
        }

        // P0-3: 返回可访问的 API URL（而非文件系统路径）
        String coverUrl = "/api/files/covers/" + videoId + "/" + savedFileName;
        video.setCoverUrl(coverUrl);
        video.setUpdatedAt(LocalDateTime.now());
        videoRepository.updateById(video);

        return coverUrl;
    }

    /**
     * P1-8: 图片魔数校验（JPEG: FFD8FF, PNG: 89504E47）
     */
    private void validateImageMagic(MultipartFile file) {
        // 【复用工具类】FileUploadUtil.assertImageMagic 已有完整实现
        com.microcourse.util.FileUploadUtil.assertImageMagic(file);
    }

    /* ================================================================
     *  视频上传（移自 VideoController）
     * ================================================================ */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VideoVO uploadVideo(MultipartFile file, Long courseId, Long chapterId) {
        // P0-2: 校验课程 Owner 权限
        assertCourseOwnership(courseId);

        // P1-6: 校验 chapterId 归属课程
        if (chapterId != null) {
            assertChapterBelongsToCourse(chapterId, courseId);
        }

        // 校验文件类型、大小、魔数
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
        try {
            Path parent = targetPath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("[VideoUpload] 文件保存失败 courseId={}, filename={}, target={}", courseId, originalFilename, targetPath, e);
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "文件保存失败: " + e.getMessage());
        }

        // OP-0290: 计算 MD5 并检查重复（Redis 分布式锁防止并发秒传竞争）
        String md5 = computeFileMd5(targetPath);
        if (md5 == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "文件校验失败");
        }
        String lockKey = "lock:video:md5:" + md5;
        String lockValue = UUID.randomUUID().toString();
        boolean locked = redisUtil.tryLock(lockKey, lockValue, 30);
        try {
            // 锁内二次检查：防止两个并发线程先后通过第一次检查
            Video duplicate = findByMd5(md5);
            if (duplicate != null) {
                log.info("[VideoUpload] 检测到 MD5 重复 md5={} existingVideoId={}", md5, duplicate.getId());
                // 秒传：删除刚保存的文件，返回已有视频
                try { Files.deleteIfExists(targetPath); } catch (IOException e) {
                    log.warn("[VideoUpload] 临时文件删除失败 path={}", targetPath, e);
                }
                return getById(duplicate.getId());
            }
        } finally {
            if (locked) {
                redisUtil.releaseLock(lockKey, lockValue);
            }
        }

        // 创建 Video 记录
        Video video = new Video();
        video.setChapterId(chapterId);
        video.setCourseId(courseId);
        video.setTitle(originalFilename != null ? originalFilename : "未命名视频");
        video.setFileName(originalFilename != null ? originalFilename : "video.mp4");
        // WebMvcConfig 映射 /api/files/** → file:./uploads/, 所以 url 是 /api/files/videos/{courseId}/{filename}
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

        createEntity(video);

        // 转码任务必须在事务提交后再提交，避免异步线程读不到刚插入的视频记录。
        final Long videoId = video.getId();
        scheduleTranscodeAfterCommit(videoId);

        return getById(video.getId());
    }

    private void scheduleTranscodeAfterCommit(Long videoId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submitTranscode(videoId);
                }
            });
            return;
        }
        submitTranscode(videoId);
    }

    private void submitTranscode(Long videoId) {
        try {
            videoTranscodeService.transcode(videoId);
        } catch (Exception e) {
            log.error("[VideoUpload] 提交转码任务失败 videoId={}", videoId, e);
            throw e;
        }
    }

    @Override
    public String getHlsPlayUrl(Long id, String sign) {
        Video video = findEntityById(id);
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

        // 签名校验
        if (sign == null || !videoSignUtil.verifySign(id, sign)) {
            throw new BusinessException(ErrorCode.VIDEO_SIGN_INVALID);
        }

        String hlsUrl = video.getHlsUrl();
        if (hlsUrl == null || hlsUrl.isBlank()) {
            throw new BusinessException(ErrorCode.VIDEO_TRANSCODE_FAILED, "视频转码尚未完成");
        }

        return hlsUrl;
    }

    @Override
    public Long getCourseIdByVideoId(Long videoId) {
        Video video = videoRepository.selectById(videoId);
        if (video == null) {
            throw new BusinessException(ErrorCode.VIDEO_NOT_FOUND);
        }
        return video.getCourseId();
    }

    @Override
    public VideoStatusVO getStatus(Long id) {
        Video video = videoRepository.selectById(id);
        if (video == null) {
            throw new BusinessException(ErrorCode.VIDEO_NOT_FOUND);
        }
        return new VideoStatusVO(video.getId(), video.getStatus(), video.getProgress(), video.getErrorMessage());
    }

    /* ================================================================
     *  视频文件校验（移自 VideoController）
     * ================================================================ */

    /**
     * 校验视频文件扩展名、MIME type、大小和魔数
     * P1-4: 仅读取魔数字节（不消耗整个流），不影响后续 transferTo
     */
    private void validateVideoFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "上传文件不能为空");
        }
        // Round 11-4 安全加固：文件名路径穿越防护（纵深防御）。
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
        if (file.getSize() > getMaxFileSize()) {
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
     * 拒绝含 ".." / 路径分隔符 / null 字节的恶意文件名。
     * 【复用工具类】已迁移至 FileUploadUtil.assertSafeFilename
     */
    private static void assertSafeFilename(String filename) {
        com.microcourse.util.FileUploadUtil.assertSafeFilename(filename);
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
     * 【复用工具类】已迁移至 HashUtil.computeFileMd5
     */
    private String computeFileMd5(Path filePath) {
        return com.microcourse.util.HashUtil.computeFileMd5(filePath);
    }

    /** P0-2: 校验当前用户是否为课程 owner 或 ADMIN（公开方法） */
    @Override
    public void assertCourseOwnership(Long courseId) {
        Course course = courseRepository.selectById(courseId);
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        assertCourseOwner(course);
    }

    /** P1-6: 校验章节归属课程 */
    @Override
    public void assertChapterBelongsToCourse(Long chapterId, Long courseId) {
        CourseChapter chapter = chapterRepository.selectById(chapterId);
        if (chapter == null) {
            throw new BusinessException(ErrorCode.CHAPTER_NOT_FOUND);
        }
        if (!courseId.equals(chapter.getCourseId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "章节不属于该课程");
        }
    }

    /** P2: 按 MD5 查询是否已有重复视频 */
    @Override
    /** @deprecated 返回 Entity 对象，仅限内部 MD5 去重使用。 */
    @Deprecated
    public Video findByMd5(String md5) {
        if (md5 == null || md5.isBlank()) {
            return null;
        }
        LambdaQueryWrapper<Video> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Video::getFileMd5, md5).last("LIMIT 1");
        return videoRepository.selectOne(wrapper);
    }

    private VideoVO convertToVO(Video video, Map<Long, Course> courseMap,
                                Map<Long, CourseChapter> chapterMap) {
        return com.microcourse.util.VideoConverter.convertToVO(video, courseMap, chapterMap);
    }

    /**
     * 校验当前用户是否为课程 owner 或 ADMIN。
     * <p>通用模式：实现逻辑与 CourseChapterServiceImpl.assertCourseOwner(Course) 一致。
     * 若需统一重构，可抽取到公共工具类。</p>
     */
    private void assertCourseOwner(Course course) {
        if (!SecurityUtil.isOwnerOrAdmin(course.getTeacherId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
    }
}
