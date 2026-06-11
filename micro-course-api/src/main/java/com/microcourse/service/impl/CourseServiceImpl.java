package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.microcourse.dto.*;
import com.microcourse.entity.Course;
import com.microcourse.entity.CourseCategory;
import com.microcourse.entity.CourseChapter;
import com.microcourse.entity.User;
import com.microcourse.enums.CourseStatus;
import com.microcourse.enums.UserRole;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CourseChapterRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.CourseCategoryRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.CourseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CourseServiceImpl implements CourseService {

    private final CourseRepository courseRepository;
    private final CourseCategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final CourseChapterRepository chapterRepository;

    public CourseServiceImpl(CourseRepository courseRepository,
                             CourseCategoryRepository categoryRepository,
                             UserRepository userRepository,
                             CourseChapterRepository chapterRepository) {
        this.courseRepository = courseRepository;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
        this.chapterRepository = chapterRepository;
    }

    @Override
    public PageResult<CourseVO> page(CoursePageQuery query) {
        LambdaQueryWrapper<Course> wrapper = new LambdaQueryWrapper<>();
        if (query.getTitle() != null && !query.getTitle().isEmpty()) {
            wrapper.like(Course::getTitle, query.getTitle());
        }
        if (query.getCategoryId() != null) {
            wrapper.eq(Course::getCategoryId, query.getCategoryId());
        }
        if (query.getTeacherId() != null) {
            wrapper.eq(Course::getTeacherId, query.getTeacherId());
        }
        if (query.getStatus() != null) {
            wrapper.eq(Course::getStatus, query.getStatus());
        }
        wrapper.orderByDesc(Course::getCreatedAt);

        IPage<Course> ipage = courseRepository.selectPage(
                new Page<>(query.getPage() + 1, query.getSize()), wrapper);

        List<CourseVO> vos = ipage.getRecords().stream()
                .map(this::convertToVO).collect(Collectors.toList());

        PageResult<CourseVO> result = new PageResult<>();
        result.setItems(vos);
        result.setPage(query.getPage());
        result.setSize(query.getSize());
        result.setTotalElements(ipage.getTotal());
        result.setTotalPages(ipage.getPages());
        return result;
    }

    @Override
    public CourseVO getById(Long id) {
        Course course = courseRepository.selectById(id);
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        CourseVO vo = convertToVO(course);

        // Load chapters
        LambdaQueryWrapper<CourseChapter> chapterWrapper = new LambdaQueryWrapper<>();
        chapterWrapper.eq(CourseChapter::getCourseId, id)
                .orderByAsc(CourseChapter::getSortOrder);
        List<CourseChapter> chapters = chapterRepository.selectList(chapterWrapper);
        vo.setChapters(chapters.stream().map(this::convertChapterToVO).collect(Collectors.toList()));

        return vo;
    }

    @Override
    @Transactional
    public CourseVO create(CourseCreateRequest request) {
        // Validate category exists
        if (categoryRepository.selectById(request.getCategoryId()) == null) {
            throw new BusinessException(ErrorCode.COURSE_CATEGORY_NOT_FOUND);
        }
        // Validate teacher exists and has TEACHER role
        User teacher = userRepository.selectById(request.getTeacherId());
        if (teacher == null) {
            throw new BusinessException(ErrorCode.COURSE_TEACHER_NOT_FOUND);
        }
        if (teacher.getRole() != UserRole.TEACHER) {
            throw new BusinessException(ErrorCode.COURSE_TEACHER_NOT_FOUND);
        }

        Course course = new Course();
        course.setTitle(request.getTitle());
        course.setCategoryId(request.getCategoryId());
        course.setTeacherId(request.getTeacherId());
        course.setSubtitle(request.getSubtitle());
        course.setSummary(request.getSummary());
        course.setCoverUrl(request.getCoverUrl());
        course.setOfferDepartmentId(request.getOfferDepartmentId());
        course.setSemester(request.getSemester());
        course.setCreditHours(request.getCreditHours());
        course.setCourseNature(request.getCourseNature());
        course.setMaxStudents(request.getMaxStudents());
        course.setDifficulty(request.getDifficulty());
        course.setDescription(request.getDescription());
        course.setStatus(CourseStatus.DRAFT.getCode());
        course.setCreatedAt(LocalDateTime.now());
        course.setUpdatedAt(LocalDateTime.now());
        course.setVersion(0);

        courseRepository.insert(course);
        return convertToVO(course);
    }

    @Override
    @Transactional
    public CourseVO update(Long id, CourseUpdateRequest request) {
        Course course = courseRepository.selectById(id);
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        // Published course cannot be edited directly
        if (course.getStatus() != null && course.getStatus() == CourseStatus.PUBLISHED.getCode()) {
            throw new BusinessException(ErrorCode.COURSE_PUBLISHED_CANNOT_EDIT);
        }

        // Partial update
        if (request.getTitle() != null) course.setTitle(request.getTitle());
        if (request.getCategoryId() != null) {
            if (categoryRepository.selectById(request.getCategoryId()) == null) {
                throw new BusinessException(ErrorCode.COURSE_CATEGORY_NOT_FOUND);
            }
            course.setCategoryId(request.getCategoryId());
        }
        if (request.getTeacherId() != null) {
            User teacher = userRepository.selectById(request.getTeacherId());
            if (teacher == null || teacher.getRole() != UserRole.TEACHER) {
                throw new BusinessException(ErrorCode.COURSE_TEACHER_NOT_FOUND);
            }
            course.setTeacherId(request.getTeacherId());
        }
        if (request.getSubtitle() != null) course.setSubtitle(request.getSubtitle());
        if (request.getSummary() != null) course.setSummary(request.getSummary());
        if (request.getCoverUrl() != null) course.setCoverUrl(request.getCoverUrl());
        if (request.getOfferDepartmentId() != null) course.setOfferDepartmentId(request.getOfferDepartmentId());
        if (request.getSemester() != null) course.setSemester(request.getSemester());
        if (request.getCreditHours() != null) course.setCreditHours(request.getCreditHours());
        if (request.getCourseNature() != null) course.setCourseNature(request.getCourseNature());
        if (request.getMaxStudents() != null) course.setMaxStudents(request.getMaxStudents());
        if (request.getDifficulty() != null) course.setDifficulty(request.getDifficulty());
        if (request.getDescription() != null) course.setDescription(request.getDescription());

        course.setUpdatedAt(LocalDateTime.now());
        course.setVersion(course.getVersion() == null ? 1 : course.getVersion() + 1);

        courseRepository.updateById(course);
        return convertToVO(course);
    }

    @Override
    @Transactional
    public void updateStatus(Long id, Integer status) {
        Course course = courseRepository.selectById(id);
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }

        CourseStatus newStatus = CourseStatus.getDescription(status) != null
                ? CourseStatus.values()[status]
                : null;
        if (newStatus == null) {
            throw new BusinessException(ErrorCode.COURSE_INVALID_STATUS);
        }

        Integer currentStatus = course.getStatus();

        // Status transition validation
        // DRAFT(0) -> PENDING_REVIEW(1)
        // PENDING_REVIEW(1) -> APPROVED(2) by ADMIN
        // APPROVED(2) -> PUBLISHED(4)
        // PUBLISHED(4) -> CLOSED(5)
        // CLOSED(5) -> ARCHIVED(6) or DRAFT(0)
        // REJECTED(3) -> DRAFT(0)

        if (currentStatus == CourseStatus.PUBLISHED.getCode()) {
            throw new BusinessException(ErrorCode.COURSE_STATUS_TRANSITION_NOT_ALLOWED);
        }

        course.setStatus(status);
        course.setUpdatedAt(LocalDateTime.now());
        course.setVersion(course.getVersion() == null ? 1 : course.getVersion() + 1);

        if (status == CourseStatus.PUBLISHED.getCode()) {
            course.setPublishedAt(LocalDateTime.now());
        }

        courseRepository.updateById(course);
    }

    @Override
    @Transactional
    public void submitForReview(Long id) {
        Course course = courseRepository.selectById(id);
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        // DRAFT(0) → PENDING_REVIEW(1)
        if (course.getStatus() != CourseStatus.DRAFT.getCode()) {
            throw new BusinessException(ErrorCode.COURSE_STATUS_TRANSITION_NOT_ALLOWED);
        }
        course.setStatus(CourseStatus.PENDING_REVIEW.getCode());
        course.setUpdatedAt(LocalDateTime.now());
        course.setVersion(course.getVersion() == null ? 1 : course.getVersion() + 1);
        courseRepository.updateById(course);
    }

    @Override
    @Transactional
    public void approve(Long id) {
        Course course = courseRepository.selectById(id);
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        // PENDING_REVIEW(1) → APPROVED(2)
        if (course.getStatus() != CourseStatus.PENDING_REVIEW.getCode()) {
            throw new BusinessException(ErrorCode.COURSE_STATUS_TRANSITION_NOT_ALLOWED);
        }
        course.setStatus(CourseStatus.APPROVED.getCode());
        course.setUpdatedAt(LocalDateTime.now());
        course.setVersion(course.getVersion() == null ? 1 : course.getVersion() + 1);
        courseRepository.updateById(course);
    }

    @Override
    @Transactional
    public void reject(Long id, String reason) {
        Course course = courseRepository.selectById(id);
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        // PENDING_REVIEW(1) → REJECTED(3)
        if (course.getStatus() != CourseStatus.PENDING_REVIEW.getCode()) {
            throw new BusinessException(ErrorCode.COURSE_STATUS_TRANSITION_NOT_ALLOWED);
        }
        course.setStatus(CourseStatus.REJECTED.getCode());
        course.setRejectReason(reason);
        course.setUpdatedAt(LocalDateTime.now());
        course.setVersion(course.getVersion() == null ? 1 : course.getVersion() + 1);
        courseRepository.updateById(course);
    }

    @Override
    @Transactional
    public void publish(Long id) {
        Course course = courseRepository.selectById(id);
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        // APPROVED(2) → PUBLISHED(4)
        if (course.getStatus() != CourseStatus.APPROVED.getCode()) {
            throw new BusinessException(ErrorCode.COURSE_STATUS_TRANSITION_NOT_ALLOWED);
        }
        course.setStatus(CourseStatus.PUBLISHED.getCode());
        course.setPublishedAt(LocalDateTime.now());
        course.setUpdatedAt(LocalDateTime.now());
        course.setVersion(course.getVersion() == null ? 1 : course.getVersion() + 1);
        courseRepository.updateById(course);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        // 软删除：使用 status=5 (CLOSED/下架) 代替物理删除
        updateStatus(id, CourseStatus.CLOSED.getCode());
        // 级联软删除章节（使用 @TableLogic，delete 变为 UPDATE SET deleted_at = NOW()）
        LambdaQueryWrapper<CourseChapter> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CourseChapter::getCourseId, id);
        chapterRepository.delete(wrapper);
    }

    private CourseVO convertToVO(Course course) {
        CourseVO vo = new CourseVO();
        vo.setId(course.getId());
        vo.setTitle(course.getTitle());
        vo.setSubtitle(course.getSubtitle());
        vo.setSummary(course.getSummary());
        vo.setCoverUrl(course.getCoverUrl());
        vo.setCategoryId(course.getCategoryId());
        vo.setTeacherId(course.getTeacherId());
        vo.setOfferDepartmentId(course.getOfferDepartmentId());
        vo.setSemester(course.getSemester());
        vo.setCreditHours(course.getCreditHours());
        vo.setCourseNature(course.getCourseNature());
        vo.setMaxStudents(course.getMaxStudents());
        vo.setDifficulty(course.getDifficulty());
        vo.setStatus(course.getStatus());
        vo.setRejectReason(course.getRejectReason());
        vo.setDescription(course.getDescription());
        vo.setStudentCount(course.getStudentCount());
        vo.setAvgRating(course.getAvgRating());
        vo.setPublishedAt(course.getPublishedAt());
        vo.setCreatedAt(course.getCreatedAt());
        vo.setUpdatedAt(course.getUpdatedAt());
        vo.setVersion(course.getVersion());

        if (course.getStatus() != null) {
            vo.setStatusText(CourseStatus.getDescription(course.getStatus()));
        }

        // Load category name
        if (course.getCategoryId() != null) {
            CourseCategory category = categoryRepository.selectById(course.getCategoryId());
            if (category != null) {
                vo.setCategoryName(category.getName());
            }
        }

        // Load teacher name
        if (course.getTeacherId() != null) {
            User teacher = userRepository.selectById(course.getTeacherId());
            if (teacher != null) {
                vo.setTeacherName(teacher.getRealName());
            }
        }

        return vo;
    }

    private ChapterVO convertChapterToVO(CourseChapter chapter) {
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
}