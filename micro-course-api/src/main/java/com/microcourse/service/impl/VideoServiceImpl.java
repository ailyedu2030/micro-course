package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.microcourse.dto.VideoCreateRequest;
import com.microcourse.dto.VideoUpdateRequest;
import com.microcourse.dto.VideoVO;
import com.microcourse.dto.PageResult;
import com.microcourse.entity.Course;
import com.microcourse.entity.CourseChapter;
import com.microcourse.entity.Video;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CourseChapterRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.VideoRepository;
import com.microcourse.service.VideoService;
import com.microcourse.util.SecurityUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class VideoServiceImpl implements VideoService {

    private final VideoRepository videoRepository;
    private final CourseChapterRepository chapterRepository;
    private final CourseRepository courseRepository;

    public VideoServiceImpl(VideoRepository videoRepository,
                           CourseChapterRepository chapterRepository,
                           CourseRepository courseRepository) {
        this.videoRepository = videoRepository;
        this.chapterRepository = chapterRepository;
        this.courseRepository = courseRepository;
    }

    @Override
    public PageResult<VideoVO> page(Long courseId, int page, int size) {
        LambdaQueryWrapper<Video> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Video::getCourseId, courseId)
                .orderByAsc(Video::getSortOrder)
                .orderByDesc(Video::getCreatedAt);

        IPage<Video> ipage = videoRepository.selectPage(
                new Page<>(page + 1, size), wrapper);

        // N+1 修复：批量预加载 course
        Map<Long, Course> courseMap = new HashMap<>();
        Set<Long> courseIds = ipage.getRecords().stream()
                .map(Video::getCourseId).filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (!courseIds.isEmpty()) {
            courseRepository.selectBatchIds(courseIds).forEach(c -> courseMap.put(c.getId(), c));
        }
        final Map<Long, Course> finalCourseMap = courseMap;

        PageResult<VideoVO> result = new PageResult<>();
        result.setItems(ipage.getRecords().stream()
                .map(v -> convertToVO(v, finalCourseMap)).toList());
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
        return convertToVO(video);
    }

    @Override
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
        // Validate chapter exists
        CourseChapter chapter = chapterRepository.selectById(request.getChapterId());
        if (chapter == null) {
            throw new BusinessException(ErrorCode.CHAPTER_NOT_FOUND);
        }
        // Validate course exists
        Course course = courseRepository.selectById(request.getCourseId());
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        // Owner check: only course teacher or ADMIN can create video
        assertCourseOwner(course);

        Video video = new Video();
        video.setChapterId(request.getChapterId());
        video.setCourseId(request.getCourseId());
        video.setTitle(request.getTitle());
        video.setFileName(request.getFileName());
        video.setFileSize(request.getFileSize());
        video.setDuration(request.getDuration());
        video.setSortOrder(request.getSortOrder());
        video.setStatus(0); // 上传中
        video.setProgress(0);
        video.setCreatedAt(LocalDateTime.now());
        video.setUpdatedAt(LocalDateTime.now());
        video.setVersion(0);

        videoRepository.insert(video);
        return convertToVO(video);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VideoVO update(Long id, VideoUpdateRequest request) {
        Video video = videoRepository.selectById(id);
        if (video == null) {
            throw new BusinessException(ErrorCode.VIDEO_NOT_FOUND);
        }
        // Owner check: only course teacher or ADMIN can update video
        assertCourseOwnerByCourseId(video.getCourseId());

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

        video.setUpdatedAt(LocalDateTime.now());
        video.setVersion(video.getVersion() == null ? 1 : video.getVersion() + 1);

        videoRepository.updateById(video);
        return convertToVO(video);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Video video = videoRepository.selectById(id);
        if (video == null) {
            throw new BusinessException(ErrorCode.VIDEO_NOT_FOUND);
        }
        // Owner check: only course teacher or ADMIN can delete video
        assertCourseOwnerByCourseId(video.getCourseId());
        videoRepository.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long videoId, int status) {
        Video video = videoRepository.selectById(videoId);
        if (video == null) {
            throw new BusinessException(ErrorCode.VIDEO_NOT_FOUND);
        }
        video.setStatus(status);
        video.setUpdatedAt(LocalDateTime.now());
        videoRepository.updateById(video);
    }

    @Override
    public String uploadCover(Long videoId, MultipartFile file) {
        Video video = videoRepository.selectById(videoId);
        if (video == null) {
            throw new BusinessException(ErrorCode.VIDEO_NOT_FOUND);
        }

        // 保存到 uploads/covers/{videoId}/
        String baseDir = "uploads/covers/" + videoId;
        String originalFilename = file.getOriginalFilename();
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

        String coverUrl = targetPath.toString();
        video.setCoverUrl(coverUrl);
        video.setUpdatedAt(LocalDateTime.now());
        video.setVersion(video.getVersion() == null ? 1 : video.getVersion() + 1);
        videoRepository.updateById(video);

        return coverUrl;
    }

    private VideoVO convertToVO(Video video) {
        VideoVO vo = new VideoVO();
        vo.setId(video.getId());
        vo.setChapterId(video.getChapterId());
        vo.setCourseId(video.getCourseId());
        vo.setTitle(video.getTitle());
        vo.setFileName(video.getFileName());
        vo.setFileSize(video.getFileSize());
        vo.setFileMd5(video.getFileMd5());
        vo.setMimeType(video.getMimeType());
        vo.setDuration(video.getDuration());
        vo.setUrl(video.getUrl());
        vo.setHlsUrl(video.getHlsUrl());
        vo.setThumbnailUrl(video.getThumbnailUrl());
        vo.setCoverUrl(video.getCoverUrl());
        vo.setStatus(video.getStatus());
        vo.setProgress(video.getProgress());
        vo.setErrorMessage(video.getErrorMessage());
        vo.setOriginalPath(video.getOriginalPath());
        vo.setSortOrder(video.getSortOrder());
        vo.setCreatedAt(video.getCreatedAt());
        vo.setUpdatedAt(video.getUpdatedAt());
        vo.setVersion(video.getVersion());

        // Load course name
        if (video.getCourseId() != null) {
            Course course = courseRepository.selectById(video.getCourseId());
            if (course != null) {
                vo.setCourseName(course.getTitle());
            }
        }

        // Load chapter name
        if (video.getChapterId() != null) {
            CourseChapter chapter = chapterRepository.selectById(video.getChapterId());
            if (chapter != null) {
                vo.setChapterName(chapter.getTitle());
            }
        }

        return vo;
    }

    private VideoVO convertToVO(Video video, Map<Long, Course> courseMap) {
        VideoVO vo = new VideoVO();
        vo.setId(video.getId());
        vo.setChapterId(video.getChapterId());
        vo.setCourseId(video.getCourseId());
        vo.setTitle(video.getTitle());
        vo.setFileName(video.getFileName());
        vo.setFileSize(video.getFileSize());
        vo.setFileMd5(video.getFileMd5());
        vo.setMimeType(video.getMimeType());
        vo.setDuration(video.getDuration());
        vo.setUrl(video.getUrl());
        vo.setHlsUrl(video.getHlsUrl());
        vo.setThumbnailUrl(video.getThumbnailUrl());
        vo.setCoverUrl(video.getCoverUrl());
        vo.setStatus(video.getStatus());
        vo.setProgress(video.getProgress());
        vo.setErrorMessage(video.getErrorMessage());
        vo.setOriginalPath(video.getOriginalPath());
        vo.setSortOrder(video.getSortOrder());
        vo.setCreatedAt(video.getCreatedAt());
        vo.setUpdatedAt(video.getUpdatedAt());
        vo.setVersion(video.getVersion());

        // Load course name（使用预加载的 Map）
        if (video.getCourseId() != null) {
            Course course = courseMap.get(video.getCourseId());
            if (course != null) {
                vo.setCourseName(course.getTitle());
            }
        }

        // Load chapter name
        if (video.getChapterId() != null) {
            CourseChapter chapter = chapterRepository.selectById(video.getChapterId());
            if (chapter != null) {
                vo.setChapterName(chapter.getTitle());
            }
        }

        return vo;
    }

    /**
     * 校验当前用户是否为课程 owner（课程创建教师）或 ADMIN
     *
     * @param courseId 课程 ID
     * @throws BusinessException NOT_FOUND 课程不存在，NO_PERMISSION 无权限
     */
    private void assertCourseOwnerByCourseId(Long courseId) {
        Course course = courseRepository.selectById(courseId);
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        assertCourseOwner(course);
    }

    /**
     * 校验当前用户是否为课程 owner（课程创建教师）或 ADMIN
     *
     * @param course 课程实体（非 null）
     * @throws BusinessException NO_PERMISSION 无权限
     */
    private void assertCourseOwner(Course course) {
        if (!SecurityUtil.isOwnerOrAdmin(course.getTeacherId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
    }
}