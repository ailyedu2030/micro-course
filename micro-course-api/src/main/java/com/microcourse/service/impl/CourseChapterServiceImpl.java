package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.microcourse.dto.ChapterCreateRequest;
import com.microcourse.dto.ChapterUpdateRequest;
import com.microcourse.dto.ChapterVO;
import com.microcourse.dto.PageResult;
import com.microcourse.entity.Course;
import com.microcourse.entity.CourseChapter;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CourseChapterRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.service.CourseChapterService;
import com.microcourse.util.SecurityUtil;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class CourseChapterServiceImpl implements CourseChapterService {

    private final CourseChapterRepository chapterRepository;
    private final CourseRepository courseRepository;

    public CourseChapterServiceImpl(CourseChapterRepository chapterRepository,
                                     CourseRepository courseRepository) {
        this.chapterRepository = chapterRepository;
        this.courseRepository = courseRepository;
    }

    @Override
    public PageResult<ChapterVO> page(int page, int size, Long courseId) {
        LambdaQueryWrapper<CourseChapter> wrapper = new LambdaQueryWrapper<>();
        if (courseId != null) {
            wrapper.eq(CourseChapter::getCourseId, courseId);
        }
        wrapper.orderByAsc(CourseChapter::getSortOrder);

        IPage<CourseChapter> ipage = chapterRepository.selectPage(
                new Page<>(page + 1, size), wrapper);

        List<ChapterVO> vos = ipage.getRecords().stream()
                .map(this::convertToVO).collect(Collectors.toList());

        PageResult<ChapterVO> result = new PageResult<>();
        result.setItems(vos);
        result.setPage(page);
        result.setSize(size);
        result.setTotalElements(ipage.getTotal());
        result.setTotalPages(ipage.getPages());
        return result;
    }

    @Override
    public ChapterVO getById(Long id) {
        CourseChapter chapter = chapterRepository.selectById(id);
        if (chapter == null) {
            throw new BusinessException(ErrorCode.CHAPTER_NOT_FOUND);
        }
        return convertToVO(chapter);
    }

    @Override
    @Transactional
    public ChapterVO create(ChapterCreateRequest request) {
        // Validate course exists
        Course course = courseRepository.selectById(request.getCourseId());
        if (course == null) {
            throw new BusinessException(ErrorCode.CHAPTER_COURSE_NOT_FOUND);
        }
        // Owner check: only course teacher or ADMIN can create chapter
        assertCourseOwner(course);

        CourseChapter chapter = new CourseChapter();
        chapter.setCourseId(request.getCourseId());
        chapter.setTitle(request.getTitle());
        chapter.setDescription(request.getDescription());
        chapter.setSortOrder(request.getSortOrder());
        chapter.setChapterType(request.getChapterType() != null ? request.getChapterType() : "VIDEO");
        chapter.setDuration(request.getDuration());
        chapter.setCreatedAt(LocalDateTime.now());
        chapter.setUpdatedAt(LocalDateTime.now());
        chapter.setVersion(0);

        chapterRepository.insert(chapter);
        return convertToVO(chapter);
    }

    @Override
    @Transactional
    public ChapterVO update(Long id, ChapterUpdateRequest request) {
        CourseChapter chapter = chapterRepository.selectById(id);
        if (chapter == null) {
            throw new BusinessException(ErrorCode.CHAPTER_NOT_FOUND);
        }
        // Owner check: only course teacher or ADMIN can update chapter
        assertCourseOwnerByCourseId(chapter.getCourseId());

        // Partial update
        if (request.getTitle() != null) chapter.setTitle(request.getTitle());
        if (request.getDescription() != null) chapter.setDescription(request.getDescription());
        if (request.getSortOrder() != null) chapter.setSortOrder(request.getSortOrder());
        if (request.getChapterType() != null) chapter.setChapterType(request.getChapterType());
        if (request.getDuration() != null) chapter.setDuration(request.getDuration());

        chapter.setUpdatedAt(LocalDateTime.now());
        chapter.setVersion(chapter.getVersion() == null ? 1 : chapter.getVersion() + 1);

        chapterRepository.updateById(chapter);
        return convertToVO(chapter);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        CourseChapter chapter = chapterRepository.selectById(id);
        if (chapter == null) {
            throw new BusinessException(ErrorCode.CHAPTER_NOT_FOUND);
        }
        // Owner check: only course teacher or ADMIN can delete chapter
        assertCourseOwnerByCourseId(chapter.getCourseId());
        chapterRepository.deleteById(id);
    }

    private ChapterVO convertToVO(CourseChapter chapter) {
        ChapterVO vo = new ChapterVO();
        vo.setId(chapter.getId());
        vo.setCourseId(chapter.getCourseId());
        vo.setTitle(chapter.getTitle());
        vo.setDescription(chapter.getDescription());
        vo.setSortOrder(chapter.getSortOrder());
        vo.setChapterType(chapter.getChapterType());
        vo.setDuration(chapter.getDuration());
        vo.setCreatedAt(chapter.getCreatedAt());
        vo.setUpdatedAt(chapter.getUpdatedAt());
        vo.setVersion(chapter.getVersion());
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