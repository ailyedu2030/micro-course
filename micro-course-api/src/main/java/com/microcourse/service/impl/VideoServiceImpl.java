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
    private final VideoUploadExecutor videoUploadExecutor;
    private final VideoValidator videoValidator;
    private final LearningProgressRepository learningProgressRepository;

    /** P1-1: 从配置读取存储目录 */
    @Value("${video.storage-base-dir:/data/videos}")
    private String storageBaseDir;

    @Value("${video.cover-dir:uploads/covers}")
    private String coverDir;

    @Value("${video.upload-dir:uploads/videos}")
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
                            VideoUploadExecutor videoUploadExecutor,
                            VideoValidator videoValidator,
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
        this.videoUploadExecutor = videoUploadExecutor;
        this.videoValidator = videoValidator;
    }

    private long getMaxFileSize() {
        String v = adminSettingService.getByKey("max_video_size_mb");
        if (v != null && !v.isBlank()) {
            try { return Long.parseLong(v) * 1024L * 1024L; } catch (NumberFormatException e) {
                log.warn("解析 max_video_size_mb 失败: {}, 使用默认值", v);
            }
        }
        return 2L * 1024L * 1024L * 1024L; // 默认 2GB
    }

    @Override
    public PageResult<VideoVO> page(Long courseId, Long chapterId, int page, int size) {
        LambdaQueryWrapper<Video> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Video::getCourseId, courseId)
                .eq(chapterId != null, Video::getChapterId, chapterId)
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
        if (videoRepository.updateById(video) == 0) {
            throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION, "视频已被其他操作修改，请刷新后重试");
        }
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
                        // P1-C 修复：重试必须回到 UPLOADING(0)——转码任务的 CAS 要求 0→1，
                        // 此前直接置 TRANSCODING(1) 导致"已被其他转码任务接管"，重试永远卡在转码中
                        .set(Video::getStatus, VideoStatus.UPLOADING.getCode())
                        .set(Video::getErrorMessage, (String) null)
                        .set(Video::getProgress, 0)
                        .set(Video::getUpdatedAt, LocalDateTime.now())
                        .setSql("version = version + 1"));
        if (affected == 0) {
            throw new BusinessException(ErrorCode.MS_CONCURRENT_MODIFICATION, "视频状态已被修改，请刷新");
        }

        // 事务提交后调度转码
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    videoTranscodeService.transcode(id);
                }
            });
        } else {
            videoTranscodeService.transcode(id);
        }
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
     * Phase 11 重构: 委托 VideoUploadExecutor
     */
    @Override
    public java.util.List<VideoVO> batchUpload(org.springframework.web.multipart.MultipartFile[] files,
                                                Long courseId, Long chapterId) {
        return videoUploadExecutor.batchUpload(files, courseId, chapterId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String uploadCover(Long videoId, MultipartFile file) {
        // Phase 11 重构: 委托 VideoUploadExecutor
        return videoUploadExecutor.uploadCover(videoId, file);
    }

    /* ================================================================
     *  视频上传（移自 VideoController, Phase 11 委托 VideoUploadExecutor）
     * ================================================================ */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VideoVO uploadVideo(MultipartFile file, Long courseId, Long chapterId) {
        // Phase 11 重构: 委托 VideoUploadExecutor
        return videoUploadExecutor.uploadVideo(file, courseId, chapterId);
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
        assertCourseOwnership(video.getCourseId());
        return new VideoStatusVO(video.getId(), video.getStatus(), video.getProgress(), video.getErrorMessage());
    }

    @Override
    public java.util.List<VideoStatusVO> getStatusBatch(java.util.List<Long> ids) {
        if (ids == null || ids.isEmpty()) return java.util.Collections.emptyList();
        java.util.List<Video> videos = videoRepository.selectBatchIds(ids);
        java.util.Map<Long, VideoStatusVO> map = new java.util.HashMap<>();
        for (Video v : videos) {
            map.put(v.getId(), new VideoStatusVO(v.getId(), v.getStatus(), v.getProgress(), v.getErrorMessage()));
        }
        java.util.List<VideoStatusVO> result = new java.util.ArrayList<>();
        for (Long id : ids) {
            VideoStatusVO vo = map.get(id);
            if (vo != null) result.add(vo);
        }
        return result;
    }

    /* ================================================================
     *  视频文件校验（移自 VideoController）
     * ================================================================ */

    /** P0-2: 校验当前用户是否为课程 owner 或 ADMIN（公开方法） */
    @Override
    public void assertCourseOwnership(Long courseId) {
        // Phase 11 重构: 委托 VideoValidator
        videoValidator.assertCourseOwnership(courseId);
    }

    /** P1-6: 校验章节归属课程 */
    @Override
    public void assertChapterBelongsToCourse(Long chapterId, Long courseId) {
        // Phase 11 重构: 委托 VideoValidator
        videoValidator.assertChapterBelongsToCourse(chapterId, courseId);
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
